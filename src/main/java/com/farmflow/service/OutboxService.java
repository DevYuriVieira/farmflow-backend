package com.farmflow.service;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.W3CTraceContextUtils;
import com.farmflow.entity.OutboxMessage;
import com.farmflow.enums.OutboxStatus;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private static final int LEASE_DURATION_SECONDS = 300;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private static final Map<String, String> ROUTING_KEY_CATALOG = Map.ofEntries(
            Map.entry("TelemetryAlert", "telemetry.alert.critical"),
            Map.entry("TelemetryAlertEvent", "telemetry.alert.critical"),
            Map.entry("PestDetected", "pest.detected.warning"),
            Map.entry("PestDetectedEvent", "pest.detected.warning"),
            Map.entry("MachineryMaintenanceScheduled", "machinery.maintenance.scheduled"),
            Map.entry("MachineryMaintenanceScheduledEvent", "machinery.maintenance.scheduled"),
            Map.entry("MachineryOperatingHoursUpdated", "machinery.maintenance.hours"),
            Map.entry("MachineryOperatingHoursUpdatedEvent", "machinery.maintenance.hours"),
            Map.entry("ActivityCompleted", "activity.completed"),
            Map.entry("ActivityCompletedEvent", "activity.completed"),
            Map.entry("com.farmflow.activity.completed.v1", "activity.completed"),
            Map.entry("com.farmflow.pest.alert.raised.v1", "pest.detected.warning"),
            Map.entry("com.farmflow.supply.input.reserved.v1", "supply.input.reserved"),
            Map.entry("com.farmflow.telemetry.alert.raised.v1", "telemetry.alert.critical"),
            Map.entry("com.farmflow.machinery.maintenance.scheduled.v1", "machinery.maintenance.scheduled"),
            Map.entry("com.farmflow.machinery.hours.updated.v1", "machinery.maintenance.hours"),
            Map.entry("com.farmflow.supply.input.reservation.failed.v1", "supply.input.reservation.failed")
    );

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> OutboxMessage recordCloudEvent(CloudEvent<T> cloudEvent) {
        if (cloudEvent == null) {
            throw new IllegalArgumentException("cloudEvent must not be null");
        }
        String routingKey = resolveRoutingKey(cloudEvent.type());
        return recordCloudEvent(routingKey, cloudEvent);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> OutboxMessage recordCloudEvent(String routingKey, CloudEvent<T> cloudEvent) {
        if (cloudEvent == null) {
            throw new IllegalArgumentException("cloudEvent must not be null");
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(cloudEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize CloudEvent to outbox JSON payload", e);
        }

        String correlationId = cloudEvent.correlationId();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
        }

        String traceParent = MDC.get(CorrelationIdFilter.MDC_TRACEPARENT_KEY);
        if (!W3CTraceContextUtils.isValidW3CTraceParent(traceParent)) {
            traceParent = W3CTraceContextUtils.generateTraceParent(correlationId);
        }

        OutboxMessage message = OutboxMessage.builder()
                .eventType(cloudEvent.type())
                .routingKey(routingKey != null ? routingKey : resolveRoutingKey(cloudEvent.type()))
                .payload(payload)
                .correlationId(correlationId)
                .traceParent(traceParent)
                .occurredOn(cloudEvent.time() != null ? cloudEvent.time() : Instant.now())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .build();

        return outboxRepository.save(message);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage recordEvent(String eventType, Object event) {
        String routingKey = resolveRoutingKey(eventType);
        return recordEvent(eventType, routingKey, event);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
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
        if (!W3CTraceContextUtils.isValidW3CTraceParent(traceParent)) {
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
            return Collections.emptyList();
        }

        Instant leaseExpiresAt = now.plusSeconds(LEASE_DURATION_SECONDS);
        for (OutboxMessage message : messages) {
            if (message.getStatus() == OutboxStatus.PROCESSING) {
                int newAttempts = message.getAttempts() + 1;
                message.setAttempts(newAttempts);
                if (newAttempts >= maxAttempts) {
                    message.setStatus(OutboxStatus.DEAD);
                    continue;
                }
            }
            message.setStatus(OutboxStatus.PROCESSING);
            message.setNextAttemptAt(leaseExpiresAt);
        }
        outboxRepository.saveAll(messages);
        return messages.stream().filter(m -> m.getStatus() == OutboxStatus.PROCESSING).toList();
    }

    @Transactional
    public List<OutboxMessage> fetchUnprocessedBatch(int maxAttempts, int batchSize) {
        return fetchAndClaimBatch(maxAttempts, batchSize);
    }

    @Transactional
    public void markProcessed(String messageId) {
        outboxRepository.findById(messageId).ifPresent(message -> {
            if (message.getStatus() == OutboxStatus.DEAD) {
                log.warn("Cannot mark DEAD outbox message as PUBLISHED: messageId={}", messageId);
                return;
            }
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
        return outboxRepository.countPendingBacklog();
    }

    @Transactional
    public int purgePublishedMessages(java.time.Duration maxRetentionAge) {
        Instant cutoff = Instant.now().minus(maxRetentionAge);
        int deleted = outboxRepository.deleteProcessedOlderThan(cutoff);
        log.debug("Expunged {} published outbox records older than {}", deleted, cutoff);
        return deleted;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${farmflow.outbox.cleanup-interval-ms:86400000}")
    @Transactional
    public void purgeOldPublishedMessages() {
        purgePublishedMessages(java.time.Duration.ofDays(7));
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
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType cannot be null or blank");
        }
        String routingKey = ROUTING_KEY_CATALOG.get(eventType);
        if (routingKey == null) {
            throw new IllegalArgumentException("Unknown domain event type for outbox routing: " + eventType);
        }
        return routingKey;
    }

    private long calculateExponentialBackoff(int attempt) {
        long base = (long) (5 * Math.pow(3, Math.max(0, attempt - 1)));
        long jitter = ThreadLocalRandom.current().nextLong(1, 4);
        return Math.min(base + jitter, 300);
    }
}