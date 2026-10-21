package com.farmflow.service;

import com.farmflow.entity.ProcessedMessageId;
import com.farmflow.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

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
    void shouldAllowNewMessageAndExecuteBusinessEffect() {
        when(processedMessageRepository.insertIfNew(eq("test-consumer"), eq("msg-1"), eq("TelemetryAlert"), any(Instant.class)))
                .thenReturn(1);

        AtomicBoolean effectExecuted = new AtomicBoolean(false);
        boolean isNew = deduplicationService.executeIfNew("test-consumer", "msg-1", "TelemetryAlert", () -> effectExecuted.set(true));

        assertTrue(isNew);
        assertTrue(effectExecuted.get());
        verify(processedMessageRepository, times(1))
                .insertIfNew(eq("test-consumer"), eq("msg-1"), eq("TelemetryAlert"), any(Instant.class));
    }

    @Test
    void shouldRejectDuplicateMessageWithoutExecutingBusinessEffect() {
        when(processedMessageRepository.insertIfNew(eq("test-consumer"), eq("msg-dup"), eq("TelemetryAlert"), any(Instant.class)))
                .thenReturn(0);

        AtomicBoolean effectExecuted = new AtomicBoolean(false);
        boolean isNew = deduplicationService.executeIfNew("test-consumer", "msg-dup", "TelemetryAlert", () -> effectExecuted.set(true));

        assertFalse(isNew);
        assertFalse(effectExecuted.get());
        verify(processedMessageRepository, times(1))
                .insertIfNew(eq("test-consumer"), eq("msg-dup"), eq("TelemetryAlert"), any(Instant.class));
    }

    @Test
    void shouldAllowMessageWithNullOrBlankIdGracefully() {
        AtomicBoolean effectExecuted = new AtomicBoolean(false);
        assertTrue(deduplicationService.executeIfNew("test-consumer", null, "TestEvent", () -> effectExecuted.set(true)));
        assertTrue(effectExecuted.get());

        effectExecuted.set(false);
        assertTrue(deduplicationService.executeIfNew("test-consumer", "", "TestEvent", () -> effectExecuted.set(true)));
        assertTrue(effectExecuted.get());

        verify(processedMessageRepository, never()).insertIfNew(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldPropagateExceptionWhenBusinessEffectFailsToEnsureTransactionRollback() {
        when(processedMessageRepository.insertIfNew(anyString(), anyString(), anyString(), any(Instant.class)))
                .thenReturn(1);

        assertThrows(IllegalStateException.class, () ->
                deduplicationService.executeIfNew("test-consumer", "msg-fail", "TelemetryAlert", () -> {
                    throw new IllegalStateException("Simulated transactional persistence error");
                })
        );
    }

    @Test
    void shouldPurgeExpiredInboxRecords() {
        when(processedMessageRepository.deleteOlderThan(any(Instant.class))).thenReturn(15);

        int deleted = deduplicationService.purgeExpiredInboxRecords(Duration.ofDays(7));

        assertEquals(15, deleted);
        verify(processedMessageRepository, times(1)).deleteOlderThan(any(Instant.class));
    }

    @Test
    void shouldCheckIfAlreadyProcessed() {
        when(processedMessageRepository.existsById(new ProcessedMessageId("consumer-a", "msg-1"))).thenReturn(true);
        when(processedMessageRepository.existsById(new ProcessedMessageId("consumer-a", "msg-2"))).thenReturn(false);

        assertTrue(deduplicationService.isAlreadyProcessed("consumer-a", "msg-1"));
        assertFalse(deduplicationService.isAlreadyProcessed("consumer-a", "msg-2"));
        assertFalse(deduplicationService.isAlreadyProcessed("consumer-a", null));
        assertFalse(deduplicationService.isAlreadyProcessed("consumer-a", ""));
    }

    @Test
    void shouldSupportBackwardCompatibleProcessIfNew() {
        when(processedMessageRepository.insertIfNew(eq("default"), eq("legacy-msg"), eq("LegacyEvent"), any(Instant.class)))
                .thenReturn(1);

        assertTrue(deduplicationService.processIfNew("legacy-msg", "LegacyEvent"));
        verify(processedMessageRepository, times(1))
                .insertIfNew(eq("default"), eq("legacy-msg"), eq("LegacyEvent"), any(Instant.class));
    }
}