package com.farmflow.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.entity.FarmActivity;
import com.farmflow.repository.ActivityRepository;
import com.farmflow.service.MessageDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsumerResilienceTest {

    private MessageDeduplicationService deduplicationService;
    private ActivityRepository activityRepository;
    private RabbitTemplate rabbitTemplate;
    private FarmFlowMetrics farmFlowMetrics;
    private TelemetryAlertConsumer telemetryConsumer;
    private PestScoutingConsumer pestConsumer;

    @BeforeEach
    void setUp() {
        deduplicationService = mock(MessageDeduplicationService.class);
        activityRepository = mock(ActivityRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        farmFlowMetrics = mock(FarmFlowMetrics.class);
        ObjectMapper objectMapper = new ObjectMapper();

        telemetryConsumer = new TelemetryAlertConsumer(
                deduplicationService,
                activityRepository,
                objectMapper,
                rabbitTemplate,
                farmFlowMetrics
        );
        pestConsumer = new PestScoutingConsumer(
                deduplicationService,
                activityRepository,
                objectMapper,
                rabbitTemplate,
                farmFlowMetrics
        );
    }

    @Test
    void shouldProcessValidTelemetryMessageAndApplyBusinessEffectWhenNotDuplicate() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("tel-123");
        Message message = new Message("{\"sensorId\":\"s1\",\"farmId\":\"f1\",\"val\":42.5}".getBytes(StandardCharsets.UTF_8), props);

        doAnswer(invocation -> {
            Runnable effect = invocation.getArgument(3);
            effect.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(TelemetryAlertConsumer.CONSUMER_NAME),
                eq("tel-123"),
                eq("TelemetryAlert"),
                any(Runnable.class)
        );

        telemetryConsumer.handleTelemetryAlert(message);

        verify(deduplicationService, times(1)).executeIfNew(
                eq(TelemetryAlertConsumer.CONSUMER_NAME),
                eq("tel-123"),
                eq("TelemetryAlert"),
                any(Runnable.class)
        );
        verify(farmFlowMetrics, times(1)).incrementConsumerProcessed("TelemetryAlert");
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));

        ArgumentCaptor<FarmActivity> captor = ArgumentCaptor.forClass(FarmActivity.class);
        verify(activityRepository, times(1)).save(captor.capture());
        FarmActivity savedActivity = captor.getValue();
        assertEquals("f1", savedActivity.getFarmId());
        assertEquals("Telemetry Alert Inspection: s1", savedActivity.getTitle());
    }

    @Test
    void shouldDiscardDuplicateTelemetryMessageWithoutSideEffects() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("tel-dup-123");
        Message message = new Message("{\"sensorId\":\"s1\",\"val\":42.5}".getBytes(StandardCharsets.UTF_8), props);

        when(deduplicationService.executeIfNew(
                eq(TelemetryAlertConsumer.CONSUMER_NAME),
                eq("tel-dup-123"),
                eq("TelemetryAlert"),
                any(Runnable.class)
        )).thenReturn(false);

        telemetryConsumer.handleTelemetryAlert(message);

        verify(deduplicationService, times(1)).executeIfNew(
                eq(TelemetryAlertConsumer.CONSUMER_NAME),
                eq("tel-dup-123"),
                eq("TelemetryAlert"),
                any(Runnable.class)
        );
        verify(farmFlowMetrics, times(1)).incrementConsumerDuplicate("TelemetryAlert");
        verify(farmFlowMetrics, never()).incrementConsumerProcessed(anyString());
        verify(activityRepository, never()).save(any(FarmActivity.class));
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void shouldRoutePoisonTelemetryMessageToDlq() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("tel-poison");
        Message message = new Message("INVALID_CORRUPTED_NON_JSON".getBytes(StandardCharsets.UTF_8), props);

        telemetryConsumer.handleTelemetryAlert(message);

        verify(rabbitTemplate, times(1)).send(
                eq(RabbitMqTopologyConfig.DLX_EXCHANGE),
                eq("events.dlq"),
                eq(message)
        );
        verify(farmFlowMetrics, times(1)).incrementConsumerFailed("TelemetryAlert");
        verify(deduplicationService, never()).executeIfNew(anyString(), anyString(), anyString(), any());
        verify(activityRepository, never()).save(any(FarmActivity.class));
    }

    @Test
    void shouldProcessValidPestMessageAndApplyBusinessEffectWhenNotDuplicate() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("pest-123");
        Message message = new Message("{\"farmId\":\"farm-1\",\"plantationId\":\"p-1\",\"pestName\":\"Spodoptera\"}".getBytes(StandardCharsets.UTF_8), props);

        doAnswer(invocation -> {
            Runnable effect = invocation.getArgument(3);
            effect.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(PestScoutingConsumer.CONSUMER_NAME),
                eq("pest-123"),
                eq("PestDetected"),
                any(Runnable.class)
        );

        pestConsumer.handlePestDetected(message);

        verify(deduplicationService, times(1)).executeIfNew(
                eq(PestScoutingConsumer.CONSUMER_NAME),
                eq("pest-123"),
                eq("PestDetected"),
                any(Runnable.class)
        );
        verify(farmFlowMetrics, times(1)).incrementConsumerProcessed("PestDetected");
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));

        ArgumentCaptor<FarmActivity> captor = ArgumentCaptor.forClass(FarmActivity.class);
        verify(activityRepository, times(1)).save(captor.capture());
        FarmActivity savedActivity = captor.getValue();
        assertEquals("farm-1", savedActivity.getFarmId());
        assertEquals("p-1", savedActivity.getPlantationId());
        assertEquals("Pest Alert Inspection: Spodoptera", savedActivity.getTitle());
    }

    @Test
    void shouldDiscardDuplicatePestMessageWithoutSideEffects() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("pest-dup-123");
        Message message = new Message("{\"pest\":\"Lagarta\",\"field\":\"T1\"}".getBytes(StandardCharsets.UTF_8), props);

        when(deduplicationService.executeIfNew(
                eq(PestScoutingConsumer.CONSUMER_NAME),
                eq("pest-dup-123"),
                eq("PestDetected"),
                any(Runnable.class)
        )).thenReturn(false);

        pestConsumer.handlePestDetected(message);

        verify(deduplicationService, times(1)).executeIfNew(
                eq(PestScoutingConsumer.CONSUMER_NAME),
                eq("pest-dup-123"),
                eq("PestDetected"),
                any(Runnable.class)
        );
        verify(farmFlowMetrics, times(1)).incrementConsumerDuplicate("PestDetected");
        verify(farmFlowMetrics, never()).incrementConsumerProcessed(anyString());
        verify(activityRepository, never()).save(any(FarmActivity.class));
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void shouldRoutePoisonPestMessageToDlq() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("pest-poison");
        Message message = new Message("".getBytes(StandardCharsets.UTF_8), props);

        pestConsumer.handlePestDetected(message);

        verify(rabbitTemplate, times(1)).send(
                eq(RabbitMqTopologyConfig.DLX_EXCHANGE),
                eq("events.dlq"),
                eq(message)
        );
        verify(farmFlowMetrics, times(1)).incrementConsumerFailed("PestDetected");
        verify(deduplicationService, never()).executeIfNew(anyString(), anyString(), anyString(), any());
        verify(activityRepository, never()).save(any(FarmActivity.class));
    }

    @Test
    void shouldPropagateTraceparentAndCorrelationIdToMdcDuringConsumerProcessing() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("tel-trace-1");
        props.setHeader(com.farmflow.config.CorrelationIdFilter.TRACEPARENT_HEADER, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        props.setCorrelationId("corr-999");
        Message message = new Message("{\"sensorId\":\"s1\"}".getBytes(StandardCharsets.UTF_8), props);

        java.util.concurrent.atomic.AtomicReference<String> capturedTraceParent = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> capturedCorrelationId = new java.util.concurrent.atomic.AtomicReference<>();

        doAnswer(invocation -> {
            capturedTraceParent.set(org.slf4j.MDC.get(com.farmflow.config.CorrelationIdFilter.MDC_TRACEPARENT_KEY));
            capturedCorrelationId.set(org.slf4j.MDC.get(com.farmflow.config.CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
            return true;
        }).when(deduplicationService).executeIfNew(anyString(), anyString(), anyString(), any());

        telemetryConsumer.handleTelemetryAlert(message);

        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", capturedTraceParent.get());
        assertEquals("corr-999", capturedCorrelationId.get());
        assertNull(org.slf4j.MDC.get(com.farmflow.config.CorrelationIdFilter.MDC_TRACEPARENT_KEY));
        assertNull(org.slf4j.MDC.get(com.farmflow.config.CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
    }
}