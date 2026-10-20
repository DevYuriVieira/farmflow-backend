package com.farmflow.service;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.entity.OutboxMessage;
import com.farmflow.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxMessage recordEvent(String eventType, Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize domain event to outbox JSON payload", e);
        }

        String traceParent = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (traceParent == null || traceParent.isBlank()) {
            traceParent = UUID.randomUUID().toString();
        }

        OutboxMessage message = OutboxMessage.builder()
                .eventType(eventType)
                .payload(payload)
                .traceParent(traceParent)
                .occurredOn(Instant.now())
                .attempts(0)
                .build();

        return outboxRepository.save(message);
    }

    @Transactional
    public List<OutboxMessage> fetchUnprocessedBatch(int maxAttempts, int batchSize) {
        return outboxRepository.findUnprocessedForUpdateSkipLocked(maxAttempts, batchSize);
    }

    @Transactional
    public void markProcessed(String messageId) {
        outboxRepository.findById(messageId).ifPresent(message -> {
            message.setProcessedOn(Instant.now());
            outboxRepository.save(message);
        });
    }

    @Transactional
    public void markFailed(String messageId, String errorMessage) {
        outboxRepository.findById(messageId).ifPresent(message -> {
            message.setAttempts(message.getAttempts() + 1);
            message.setLastError(errorMessage);
            outboxRepository.save(message);
        });
    }

    public long getBacklogCount() {
        return outboxRepository.countByProcessedOnIsNull();
    }
}