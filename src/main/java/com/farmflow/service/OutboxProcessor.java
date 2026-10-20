package com.farmflow.service;

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
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${farmflow.outbox.interval-ms:2000}")
    public void processOutboxMessages() {
        List<OutboxMessage> messages = outboxService.fetchUnprocessedBatch(MAX_ATTEMPTS, BATCH_SIZE);
        if (messages.isEmpty()) {
            return;
        }

        for (OutboxMessage message : messages) {
            try {
                eventPublisher.publishEvent(message);
                outboxService.markProcessed(message.getId());
            } catch (Exception e) {
                log.error("Failed to process outbox message id={}", message.getId(), e);
                outboxService.markFailed(message.getId(), e.getMessage());
            }
        }
    }
}