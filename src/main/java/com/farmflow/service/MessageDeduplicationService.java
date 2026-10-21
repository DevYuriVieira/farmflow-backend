package com.farmflow.service;

import com.farmflow.entity.ProcessedMessageId;
import com.farmflow.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MessageDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(MessageDeduplicationService.class);

    private final ProcessedMessageRepository processedMessageRepository;

    @Transactional
    public boolean executeIfNew(String consumerName, String messageId, String eventType, Runnable businessEffect) {
        if (messageId == null || messageId.isBlank()) {
            if (businessEffect != null) {
                businessEffect.run();
            }
            return true;
        }

        String effectiveConsumer = (consumerName != null && !consumerName.isBlank()) ? consumerName : "default";
        String effectiveEventType = (eventType != null && !eventType.isBlank()) ? eventType : "unknown";

        int inserted = processedMessageRepository.insertIfNew(
                effectiveConsumer,
                messageId,
                effectiveEventType,
                Instant.now()
        );

        if (inserted == 0) {
            log.warn("Duplicate message detected for consumer={} messageId={} eventType={}. Skipping business effect.",
                    effectiveConsumer, messageId, effectiveEventType);
            return false;
        }

        if (businessEffect != null) {
            businessEffect.run();
        }
        return true;
    }

    @Transactional
    public boolean processIfNew(String messageId, String eventType) {
        return executeIfNew("default", messageId, eventType, () -> {});
    }

    public boolean isAlreadyProcessed(String consumerName, String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        String effectiveConsumer = (consumerName != null && !consumerName.isBlank()) ? consumerName : "default";
        return processedMessageRepository.existsById(new ProcessedMessageId(effectiveConsumer, messageId));
    }

    public boolean isAlreadyProcessed(String messageId) {
        return isAlreadyProcessed("default", messageId);
    }

    @Transactional
    public int purgeExpiredInboxRecords(Duration maxRetentionAge) {
        Instant cutoff = Instant.now().minus(maxRetentionAge);
        int deleted = processedMessageRepository.deleteOlderThan(cutoff);
        log.debug("Expunged {} processed inbox records older than {}", deleted, cutoff);
        return deleted;
    }

    @Scheduled(fixedDelayString = "${farmflow.inbox.cleanup-interval-ms:86400000}")
    @Transactional
    public void purgeOldProcessedMessages() {
        purgeExpiredInboxRecords(Duration.ofDays(7));
    }
}