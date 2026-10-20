package com.farmflow.consumer;

import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.service.MessageDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;

class ConsumerResilienceTest {

    private MessageDeduplicationService deduplicationService;
    private RabbitTemplate rabbitTemplate;
    private com.farmflow.config.FarmFlowMetrics farmFlowMetrics;
    private TelemetryAlertConsumer telemetryConsumer;
    private PestScoutingConsumer pestConsumer;

    @BeforeEach
    void setUp() {
        deduplicationService = mock(MessageDeduplicationService.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        farmFlowMetrics = mock(com.farmflow.config.FarmFlowMetrics.class);
        telemetryConsumer = new TelemetryAlertConsumer(deduplicationService, rabbitTemplate, farmFlowMetrics);
        pestConsumer = new PestScoutingConsumer(deduplicationService, rabbitTemplate, farmFlowMetrics);
    }

    @Test
    void shouldProcessValidTelemetryMessageWhenNotDuplicate() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("tel-123");
        Message message = new Message("{\"sensorId\":\"s1\",\"val\":42.5}".getBytes(StandardCharsets.UTF_8), props);

        when(deduplicationService.processIfNew("tel-123", "TelemetryAlert")).thenReturn(true);

        telemetryConsumer.handleTelemetryAlert(message);

        verify(deduplicationService, times(1)).processIfNew("tel-123", "TelemetryAlert");
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void shouldDiscardDuplicateTelemetryMessageWithoutSideEffects() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("tel-dup-123");
        Message message = new Message("{\"sensorId\":\"s1\",\"val\":42.5}".getBytes(StandardCharsets.UTF_8), props);

        when(deduplicationService.processIfNew("tel-dup-123", "TelemetryAlert")).thenReturn(false);

        telemetryConsumer.handleTelemetryAlert(message);

        verify(deduplicationService, times(1)).processIfNew("tel-dup-123", "TelemetryAlert");
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
        verify(deduplicationService, never()).processIfNew(anyString(), anyString());
    }

    @Test
    void shouldProcessValidPestMessageWhenNotDuplicate() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("pest-123");
        Message message = new Message("{\"pest\":\"Lagarta\",\"field\":\"T1\"}".getBytes(StandardCharsets.UTF_8), props);

        when(deduplicationService.processIfNew("pest-123", "PestDetected")).thenReturn(true);

        pestConsumer.handlePestDetected(message);

        verify(deduplicationService, times(1)).processIfNew("pest-123", "PestDetected");
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
        verify(deduplicationService, never()).processIfNew(anyString(), anyString());
    }
}