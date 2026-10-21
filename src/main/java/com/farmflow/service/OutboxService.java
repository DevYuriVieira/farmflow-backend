package com.farmflow.service;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.W3CTraceContextUtils;
import com.farmflow.entity.OutboxMessage;
import com.farmflow.enums.OutboxStatus;
import com.farmflow.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private static final int LEASE_DURATION_SECONDS = 60;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private static final Map<String, String> ROUTING_KEY_CATALOG = Map.of(
            "TelemetryAlert", "telemetry.alert.critical",
            "TelemetryAlertEvent", "telemetry.alert.critical",
            "PestDetected", "pest.detected.warning",
            "PestDetectedEvent", "pest.detected.warning",
            "MachineryMaintenanceScheduled", "machinery.maintenance.scheduled",
            "MachineryMaintenanceScheduledEvent", "machinery.maintenance.scheduled",
            "MachineryOperatingHoursUpdated", "machinery.maintenance.hours",
            "MachineryOperatingHoursUpdatedEvent", "machinery.maintenance.hours",
            "ActivityCompleted", "activity.completed",
            "ActivityCompletedEvent", "activity.completed"
    );

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxMessage recordEvent(String eventType, Object event) {
        String routingKey = resolveRoutingKey(eventType);
        return recordEvent(eventType, routingKey, event);
    }

    @Transactional
    public OutboxMessage recordEvent(String eventType, String routingKey, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize domain event to outbox JSON payload", e);
        }

        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String traceParent = MDC.get(CorrelationIdFilter.MDC_TRACEPARENT_KEY);
        if (traceParent == null || traceParent.isBlank()) {
            traceParent = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        }
        if (traceParent == null || traceParent.isBlank()) {
            traceParent = W3CTraceContextUtils.generateTraceParent(correlationId);
        }

        OutboxMessage message = OutboxMessage.builder()
                .eventType(eventType)
                .routingKey(routingKey != null ? routingKey : resolveRoutingKey(eventType))
                .payload(payload)
                .correlationId(correlationId)
                .traceParent(traceParent)
                .occurredOn(Instant.now())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .build();

        return outboxRepository.save(message);
    }

    @Transactional
    public List<OutboxMessage> fetchAndClaimBatch(int maxAttempts, int batchSize) {
        Instant now = Instant.now();
        List<OutboxMessage> messages = outboxRepository.findPendingForClaim(now, maxAttempts, batchSize);
        if (messages == null || messages.isEmpty()) {
            messages = outboxRepository.findUnprocessedForUpdateSkipLocked(maxAttempts, batchSize);
        }
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        Instant leaseExpiresAt = now.plusSeconds(LEASE_DURATION_SECONDS);
        for (OutboxMessage message : messages) {
            message.setStatus(OutboxStatus.PROCESSING);
            message.setNextAttemptAt(leaseExpiresAt);
        }
        outboxRepository.saveAll(messages);
        return messages;
    }

    @Transactional
    public List<OutboxMessage> fetchUnprocessedBatch(int maxAttempts, int batchSize) {
        return fetchAndClaimBatch(maxAttempts, batchSize);
    }

    @Transactional
    public void markProcessed(String messageId) {
        outboxRepository.findById(messageId).ifPresent(message -> {
            message.setStatus(OutboxStatus.PUBLISHED);
            message.setProcessedOn(Instant.now());
            outboxRepository.save(message);
        });
    }

    @Transactional
    public void markFailed(String messageId, String errorMessage) {
        outboxRepository.findById(messageId).ifPresent(message -> {
            int newAttempts = message.getAttempts() + 1;
            message.setAttempts(newAttempts);
            message.setLastError(errorMessage);

            if (newAttempts >= DEFAULT_MAX_ATTEMPTS) {
                message.setStatus(OutboxStatus.DEAD);
                log.error("Outbox message id={} reached max attempts ({}). Marked as DEAD.", messageId, newAttempts);
            } else {
                message.setStatus(OutboxStatus.PENDING);
                long delaySeconds = calculateExponentialBackoff(newAttempts);
                message.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
                log.warn("Outbox message id={} failed (attempt {}/{}). Scheduled retry in {}s.",
                        messageId, newAttempts, DEFAULT_MAX_ATTEMPTS, delaySeconds);
            }
            outboxRepository.save(message);
        });
    }

    public long getBacklogCount() {
        return outboxRepository.countByProcessedOnIsNull();
    }

    public double getOldestMessageLagSeconds() {
        Instant oldest = outboxRepository.findOldestUnprocessedOccurredOn();
        if (oldest == null) {
            return 0.0;
        }
        return Math.max(0.0, java.time.Duration.between(oldest, Instant.now()).toMillis() / 1000.0);
    }

    public long getDeadLetterCount() {
        return outboxRepository.countByStatus(OutboxStatus.DEAD);
    }

    public String resolveRoutingKey(String eventType) {
        if (eventType == null) {
            return "events.general";
        }
        return ROUTING_KEY_CATALOG.getOrDefault(eventType, "events.general");
    }

    private long calculateExponentialBackoff(int attempt) {
        long base = (long) (5 * Math.pow(3, Math.max(0, attempt - 1)));
        long jitter = ThreadLocalRandom.current().nextLong(1, 4);
        return Math.min(base + jitter, 300);
    }
}