package com.farmflow.service;

import com.farmflow.entity.ProcessedMessage;
import com.farmflow.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageDeduplicationServiceTest {

    private ProcessedMessageRepository processedMessageRepository;
    private MessageDeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        processedMessageRepository = mock(ProcessedMessageRepository.class);
        deduplicationService = new MessageDeduplicationService(processedMessageRepository);
    }

    @Test
    void shouldAllowNewMessageAndPersistAuditRecord() {
        when(processedMessageRepository.existsById("msg-new-1")).thenReturn(false);

        boolean isNew = deduplicationService.processIfNew("msg-new-1", "TelemetryAlert");

        assertTrue(isNew);
        verify(processedMessageRepository, times(1)).save(any(ProcessedMessage.class));
    }

    @Test
    void shouldRejectDuplicateMessageWithoutPersistingAgain() {
        when(processedMessageRepository.existsById("msg-dup-1")).thenReturn(true);

        boolean isNew = deduplicationService.processIfNew("msg-dup-1", "TelemetryAlert");

        assertFalse(isNew);
        verify(processedMessageRepository, never()).save(any(ProcessedMessage.class));
    }

    @Test
    void shouldAllowMessageWithNullOrBlankIdGracefully() {
        assertTrue(deduplicationService.processIfNew(null, "TestEvent"));
        assertTrue(deduplicationService.processIfNew("", "TestEvent"));
        verify(processedMessageRepository, never()).existsById(anyString());
    }
}