package com.farmflow.service;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.entity.OutboxMessage;
import com.farmflow.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxServiceTest {

    private OutboxRepository outboxRepository;
    private ObjectMapper objectMapper;
    private OutboxService outboxService;
    private ApplicationEventPublisher eventPublisher;
    private RabbitMqEventPublisher rabbitMqEventPublisher;
    private com.farmflow.config.FarmFlowMetrics farmFlowMetrics;
    private OutboxProcessor outboxProcessor;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxRepository.class);
        objectMapper = new ObjectMapper();
        outboxService = new OutboxService(outboxRepository, objectMapper);
        eventPublisher = mock(ApplicationEventPublisher.class);
        rabbitMqEventPublisher = mock(RabbitMqEventPublisher.class);
        farmFlowMetrics = mock(com.farmflow.config.FarmFlowMetrics.class);
        outboxProcessor = new OutboxProcessor(outboxService, eventPublisher, rabbitMqEventPublisher, farmFlowMetrics);

        MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID_KEY, "trace-abc-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldRecordEventAtomicallyInOutbox() {
        Map<String, String> event = Map.of("sensorId", "sensor-99", "alert", "HIGH_TEMPERATURE");
        when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxMessage recorded = outboxService.recordEvent("TelemetryAlertEvent", event);

        assertNotNull(recorded);
        assertEquals("TelemetryAlertEvent", recorded.getEventType());
        assertTrue(recorded.getPayload().contains("sensor-99"));
        assertEquals("trace-abc-123", recorded.getTraceParent());
        assertEquals(0, recorded.getAttempts());
        assertNotNull(recorded.getOccurredOn());
        assertNull(recorded.getProcessedOn());
        verify(outboxRepository, times(1)).save(any(OutboxMessage.class));
    }

    @Test
    void shouldMarkMessageAsProcessed() {
        OutboxMessage message = OutboxMessage.builder()
                .id("msg-1")
                .eventType("TestEvent")
                .payload("{}")
                .occurredOn(Instant.now())
                .build();
        when(outboxRepository.findById("msg-1")).thenReturn(Optional.of(message));

        outboxService.markProcessed("msg-1");

        assertNotNull(message.getProcessedOn());
        verify(outboxRepository, times(1)).save(message);
    }

    @Test
    void shouldIncrementAttemptsAndRecordLastErrorOnFailure() {
        OutboxMessage message = OutboxMessage.builder()
                .id("msg-2")
                .eventType("TestEvent")
                .payload("{}")
                .occurredOn(Instant.now())
                .attempts(1)
                .build();
        when(outboxRepository.findById("msg-2")).thenReturn(Optional.of(message));

        outboxService.markFailed("msg-2", "Network partition timeout");

        assertEquals(2, message.getAttempts());
        assertEquals("Network partition timeout", message.getLastError());
        verify(outboxRepository, times(1)).save(message);
    }

    @Test
    void shouldProcessBatchAndPublishEvents() {
        OutboxMessage msg = OutboxMessage.builder()
                .id("msg-batch-1")
                .eventType("PestDetectedEvent")
                .payload("{\"pest\":\"Lagarta\"}")
                .traceParent("trace-1")
                .occurredOn(Instant.now())
                .build();
        when(outboxRepository.findUnprocessedForUpdateSkipLocked(3, 20)).thenReturn(List.of(msg));
        when(outboxRepository.findById("msg-batch-1")).thenReturn(Optional.of(msg));

        outboxProcessor.processOutboxMessages();

        verify(eventPublisher, times(1)).publishEvent(msg);
        verify(rabbitMqEventPublisher, times(1)).publish("pest.detected.warning", msg.getPayload(), "trace-1");
        assertNotNull(msg.getProcessedOn());
    }

    @Test
    void shouldHandleExceptionDuringBatchProcessingGracefully() {
        OutboxMessage msg = OutboxMessage.builder()
                .id("msg-fail-1")
                .eventType("PestDetectedEvent")
                .payload("{\"pest\":\"Lagarta\"}")
                .traceParent("trace-fail")
                .occurredOn(Instant.now())
                .attempts(0)
                .build();
        when(outboxRepository.findUnprocessedForUpdateSkipLocked(3, 20)).thenReturn(List.of(msg));
        when(outboxRepository.findById("msg-fail-1")).thenReturn(Optional.of(msg));
        doThrow(new RuntimeException("Broker connection refused"))
                .when(rabbitMqEventPublisher).publish(anyString(), any(), anyString());

        outboxProcessor.processOutboxMessages();

        assertEquals(1, msg.getAttempts());
        assertEquals("Broker connection refused", msg.getLastError());
        assertNull(msg.getProcessedOn());
    }
}