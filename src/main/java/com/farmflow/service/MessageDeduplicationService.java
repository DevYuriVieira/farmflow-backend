package com.farmflow.service;

import com.farmflow.entity.ProcessedMessage;
import com.farmflow.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MessageDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(MessageDeduplicationService.class);

    private final ProcessedMessageRepository processedMessageRepository;

    @Transactional
    public boolean processIfNew(String messageId, String eventType) {
        if (messageId == null || messageId.isBlank()) {
            return true;
        }

        if (processedMessageRepository.existsById(messageId)) {
            log.warn("Duplicate message detected for messageId={} eventType={}. Skipping execution.",
                    messageId, eventType);
            return false;
        }

        ProcessedMessage processed = ProcessedMessage.builder()
                .messageId(messageId)
                .eventType(eventType)
                .processedAt(Instant.now())
                .build();

        processedMessageRepository.save(processed);
        return true;
    }

    public boolean isAlreadyProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        return processedMessageRepository.existsById(messageId);
    }
}