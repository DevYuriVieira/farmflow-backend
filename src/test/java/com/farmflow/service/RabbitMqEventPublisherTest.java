package com.farmflow.service;

import com.farmflow.config.RabbitMqTopologyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RabbitMqEventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private RabbitMqEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new RabbitMqEventPublisher(rabbitTemplate);
    }

    @Test
    void shouldPublishSuccessfullyWhenBrokerAcks() {
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(RabbitMqTopologyConfig.MAIN_EXCHANGE),
                eq("telemetry.alert.critical"),
                any(),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        assertDoesNotThrow(() -> publisher.publish(
                "telemetry.alert.critical",
                "{\"status\":\"ALERT\"}",
                "msg-101",
                "corr-101",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        ));

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMqTopologyConfig.MAIN_EXCHANGE),
                eq("telemetry.alert.critical"),
                any(),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
    }

    @Test
    void shouldThrowAmqpExceptionWhenBrokerNacks() {
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            cd.getFuture().complete(new CorrelationData.Confirm(false, "Queue quota exceeded"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class)
        );

        AmqpException ex = assertThrows(AmqpException.class, () -> publisher.publish(
                "telemetry.alert.critical",
                "payload",
                "msg-102",
                "corr-102",
                null
        ));

        assertTrue(ex.getMessage().contains("Broker rejected message publication (NACK)"));
        assertTrue(ex.getMessage().contains("Queue quota exceeded"));
    }

    @Test
    void shouldThrowAmqpExceptionWhenMessageIsUnroutable() {
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            ReturnedMessage returned = new ReturnedMessage(
                    new Message("payload".getBytes(), new MessageProperties()),
                    312,
                    "NO_ROUTE",
                    RabbitMqTopologyConfig.MAIN_EXCHANGE,
                    "unroutable.key"
            );
            cd.setReturned(returned);
            cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class)
        );

        AmqpException ex = assertThrows(AmqpException.class, () -> publisher.publish(
                "unroutable.key",
                "payload",
                "msg-103",
                "corr-103",
                null
        ));

        assertTrue(ex.getMessage().contains("unroutable"));
        assertTrue(ex.getMessage().contains("NO_ROUTE"));
    }

    @Test
    void shouldThrowAmqpExceptionWhenConfirmTimesOut() {
        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(4);
            cd.getFuture().completeExceptionally(new RuntimeException("Connection closed"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(), any(MessagePostProcessor.class), any(CorrelationData.class)
        );

        AmqpException ex = assertThrows(AmqpException.class, () -> publisher.publish(
                "machinery.maintenance.scheduled",
                "payload",
                "msg-104",
                "corr-104",
                null
        ));

        assertTrue(ex.getMessage().contains("Publisher confirm timeout or failure"));
    }
}
