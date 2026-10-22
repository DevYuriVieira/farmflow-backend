package com.farmflow.service;

import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 20;

    private final OutboxService outboxService;
    private final ApplicationEventPublisher localEventPublisher;
    private final RabbitMqEventPublisher rabbitMqEventPublisher;
    private final FarmFlowMetrics farmFlowMetrics;
    private volatile long circuitOpenUntilTimestamp = 0;

    @Scheduled(fixedDelayString = "${farmflow.outbox.interval-ms:2000}")
    public void processOutboxMessages() {
        if (System.currentTimeMillis() < circuitOpenUntilTimestamp) {
            return;
        }

        List<OutboxMessage> messages = outboxService.fetchUnprocessedBatch(MAX_ATTEMPTS, BATCH_SIZE);
        if (messages.isEmpty()) {
            return;
        }

        for (OutboxMessage message : messages) {
            try {
                localEventPublisher.publishEvent(message);
                String routingKey = resolveRoutingKey(message);

                long startNanos = System.nanoTime();
                rabbitMqEventPublisher.publish(
                        routingKey,
                        message.getPayload(),
                        message.getId(),
                        message.getCorrelationId(),
                        message.getTraceParent()
                );
                farmFlowMetrics.recordOutboxPublishDurationNanos(System.nanoTime() - startNanos);

                outboxService.markProcessed(message.getId());
                farmFlowMetrics.incrementOutboxPublished();
            } catch (Exception e) {
                log.error("Failed to process outbox message id={}", message.getId(), e);
                outboxService.markFailed(message.getId(), e.getMessage());
                farmFlowMetrics.incrementOutboxFailed();

                if (isBrokerConnectivityException(e)) {
                    log.warn("Broker connectivity failure detected. Halting batch and tripping relay circuit breaker for 5000ms.");
                    tripCircuitBreaker(5000);
                    break;
                }
            }
        }
    }

    public boolean isCircuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntilTimestamp;
    }

    public void resetCircuitBreaker() {
        this.circuitOpenUntilTimestamp = 0;
    }

    private void tripCircuitBreaker(long durationMs) {
        this.circuitOpenUntilTimestamp = System.currentTimeMillis() + durationMs;
    }

    private boolean isBrokerConnectivityException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.springframework.amqp.AmqpConnectException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.SocketException
                    || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String resolveRoutingKey(OutboxMessage message) {
        if (message.getRoutingKey() != null && !message.getRoutingKey().isBlank()) {
            return message.getRoutingKey();
        }
        String eventType = message.getEventType();
        if (eventType == null) {
            return "events.general";
        }
        if (eventType.contains("Telemetry")) {
            return "telemetry.alert.critical";
        }
        if (eventType.contains("Pest")) {
            return "pest.detected.warning";
        }
        if (eventType.contains("Machinery")) {
            return "machinery.maintenance.scheduled";
        }
        if (eventType.contains("Activity")) {
            return "activity.completed";
        }
        return "events.general";
    }
}