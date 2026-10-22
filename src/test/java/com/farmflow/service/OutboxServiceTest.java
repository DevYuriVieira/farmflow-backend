package com.farmflow.service;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.W3CTraceContextUtils;
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
        assertTrue(W3CTraceContextUtils.isValidW3CTraceParent(recorded.getTraceParent()));
        assertEquals("trace-abc-123", recorded.getCorrelationId());
        assertEquals(0, recorded.getAttempts());
        assertNotNull(recorded.getOccurredOn());
        assertNull(recorded.getProcessedOn());
        verify(outboxRepository, times(1)).save(any(OutboxMessage.class));
    }

    @Test
    void shouldPropagateExistingValidW3CTraceParentFromMdc() {
        String validTraceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        MDC.put(CorrelationIdFilter.MDC_TRACEPARENT_KEY, validTraceParent);
        when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxMessage recorded = outboxService.recordEvent("TelemetryAlertEvent", Map.of("sensorId", "s1"));

        assertEquals(validTraceParent, recorded.getTraceParent());
        assertEquals("trace-abc-123", recorded.getCorrelationId());
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
        when(outboxRepository.findPendingForClaim(any(), eq(3), eq(20))).thenReturn(List.of(msg));
        when(outboxRepository.findById("msg-batch-1")).thenReturn(Optional.of(msg));

        outboxProcessor.processOutboxMessages();

        verify(eventPublisher, times(1)).publishEvent(msg);
        verify(rabbitMqEventPublisher, times(1)).publish(eq("pest.detected.warning"), eq(msg.getPayload()), eq("msg-batch-1"), isNull(), eq("trace-1"));
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
        when(outboxRepository.findPendingForClaim(any(), eq(3), eq(20))).thenReturn(List.of(msg));
        when(outboxRepository.findById("msg-fail-1")).thenReturn(Optional.of(msg));
        doThrow(new RuntimeException("Broker connection refused"))
                .when(rabbitMqEventPublisher).publish(anyString(), any(), any(), any(), any());

        outboxProcessor.processOutboxMessages();

        assertEquals(1, msg.getAttempts());
        assertEquals("Broker connection refused", msg.getLastError());
        assertNull(msg.getProcessedOn());
    }

    @Test
    void shouldCalculateOldestMessageLagSeconds() {
        Instant twoMinutesAgo = Instant.now().minusSeconds(120);
        when(outboxRepository.findOldestUnprocessedOccurredOn()).thenReturn(twoMinutesAgo);

        double lag = outboxService.getOldestMessageLagSeconds();

        assertTrue(lag >= 119.0 && lag <= 125.0);
    }

    @Test
    void shouldReturnDeadLetterCount() {
        when(outboxRepository.countByStatus(com.farmflow.enums.OutboxStatus.DEAD)).thenReturn(4L);

        long deadCount = outboxService.getDeadLetterCount();

        assertEquals(4L, deadCount);
    }

    @Test
    void shouldRecordPublishDurationMetricOnSuccessfulPublish() {
        OutboxMessage msg = OutboxMessage.builder()
                .id("msg-timer-1")
                .eventType("TelemetryAlertEvent")
                .payload("{}")
                .occurredOn(Instant.now())
                .build();
        when(outboxRepository.findPendingForClaim(any(), eq(3), eq(20))).thenReturn(List.of(msg));
        when(outboxRepository.findById("msg-timer-1")).thenReturn(Optional.of(msg));

        outboxProcessor.processOutboxMessages();

        verify(farmFlowMetrics, times(1)).recordOutboxPublishDurationNanos(anyLong());
        verify(farmFlowMetrics, times(1)).incrementOutboxPublished();
    }

    @Test
    void shouldResolveRoutingKeysForKnownEvents() {
        assertEquals("telemetry.alert.critical", outboxService.resolveRoutingKey("TelemetryAlert"));
        assertEquals("telemetry.alert.critical", outboxService.resolveRoutingKey("TelemetryAlertEvent"));
        assertEquals("pest.detected.warning", outboxService.resolveRoutingKey("PestDetected"));
        assertEquals("pest.detected.warning", outboxService.resolveRoutingKey("PestDetectedEvent"));
        assertEquals("machinery.maintenance.scheduled", outboxService.resolveRoutingKey("MachineryMaintenanceScheduled"));
        assertEquals("machinery.maintenance.hours", outboxService.resolveRoutingKey("MachineryOperatingHoursUpdated"));
        assertEquals("activity.completed", outboxService.resolveRoutingKey("ActivityCompleted"));
        assertEquals("activity.completed", outboxService.resolveRoutingKey("ActivityCompletedEvent"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionForUnknownOrNullEventType() {
        assertThrows(IllegalArgumentException.class, () -> outboxService.resolveRoutingKey("UnknownEvent"));
        assertThrows(IllegalArgumentException.class, () -> outboxService.resolveRoutingKey(null));
        assertThrows(IllegalArgumentException.class, () -> outboxService.resolveRoutingKey("   "));
    }
}