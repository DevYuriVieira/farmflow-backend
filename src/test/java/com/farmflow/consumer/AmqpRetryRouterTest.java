package com.farmflow.consumer;

import com.farmflow.config.RabbitMqTopologyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AmqpRetryRouterTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AmqpRetryRouter sut;

    @Test
    @DisplayName("getRetryCount: deve extrair contador correto do header x-retry-count")
    void shouldExtractRetryCountFromHeader() {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(AmqpRetryRouter.HEADER_RETRY_COUNT, 2);
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

        int count = sut.getRetryCount(message);

        assertEquals(2, count);
    }

    @Test
    @DisplayName("getRetryCount: deve extrair contador a partir do header xDeath quando x-retry-count ausente")
    void shouldExtractRetryCountFromXDeathHeader() {
        MessageProperties properties = new MessageProperties();
        Map<String, Object> deathRecord = Map.of("count", 3L);
        properties.setHeader("x-death", List.of(deathRecord));
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

        int count = sut.getRetryCount(message);

        assertEquals(3, count);
    }

    @Test
    @DisplayName("getRetryCount: deve retornar 0 quando nenhum header de retry estiver presente")
    void shouldReturnZeroWhenNoRetryHeadersPresent() {
        MessageProperties properties = new MessageProperties();
        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

        int count = sut.getRetryCount(message);

        assertEquals(0, count);
    }

    @ParameterizedTest(name = "routeFailure: tentativa {0} deve rotear para {1} com tipo {2}")
    @CsvSource({
            "0, telemetry.alerts.retry.10s, RETRY_STAGE_10S, 1",
            "1, telemetry.alerts.retry.30s, RETRY_STAGE_30S, 2",
            "2, telemetry.alerts.retry.90s, RETRY_STAGE_90S, 3",
            "3, events.dlq, EXHAUSTED_RETRIES, 4",
            "5, events.dlq, EXHAUSTED_RETRIES, 6"
    })
    void shouldRouteFailureThroughProgressiveStages(int initialAttempt, String expectedRoutingKey, String expectedFailureType, int expectedNextAttempt) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("msg-abc-123");
        properties.setCorrelationId("corr-xyz-789");
        properties.setContentType("application/json");
        properties.setContentEncoding("UTF-8");
        if (initialAttempt > 0) {
            properties.setHeader(AmqpRetryRouter.HEADER_RETRY_COUNT, initialAttempt);
        }

        byte[] payload = "{\"status\":\"error\"}".getBytes(StandardCharsets.UTF_8);
        Message message = new Message(payload, properties);
        RuntimeException cause = new RuntimeException("Transient DB lock timeout");

        sut.routeFailure(message, "farmflow.telemetry.alerts", "telemetry.alerts", cause);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(RabbitMqTopologyConfig.DLX_EXCHANGE),
                eq(expectedRoutingKey),
                captor.capture()
        );

        Message enriched = captor.getValue();
        assertEquals("msg-abc-123", enriched.getMessageProperties().getMessageId());
        assertEquals("corr-xyz-789", enriched.getMessageProperties().getCorrelationId());
        assertEquals("application/json", enriched.getMessageProperties().getContentType());
        assertEquals(expectedNextAttempt, (Integer) enriched.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_RETRY_COUNT));
        assertEquals(expectedFailureType, enriched.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_FAILURE_TYPE));
        assertEquals("farmflow.telemetry.alerts", enriched.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_ORIGINAL_QUEUE));
        assertEquals("Transient DB lock timeout", enriched.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_EXCEPTION_MESSAGE));
        assertNotNull(enriched.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_FAILED_AT));

        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("routePoison: deve rotear imediatamente para DLQ com tipo POISON_MESSAGE")
    void shouldRoutePoisonMessageImmediatelyToDlq() {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("poison-001");
        properties.setHeader("custom-trace", "trace-val");
        Message message = new Message("MALFORMED_NON_JSON_DATA".getBytes(StandardCharsets.UTF_8), properties);

        sut.routePoison(message, "farmflow.pest.detected", "MALFORMED_JSON_PAYLOAD");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq(RabbitMqTopologyConfig.DLX_EXCHANGE),
                eq("events.dlq"),
                captor.capture()
        );

        Message enriched = captor.getValue();
        assertEquals("poison-001", enriched.getMessageProperties().getMessageId());
        assertEquals(AmqpRetryRouter.FAILURE_TYPE_POISON, enriched.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_FAILURE_TYPE));
        assertEquals("MALFORMED_JSON_PAYLOAD", enriched.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_EXCEPTION_MESSAGE));
        assertEquals("farmflow.pest.detected", enriched.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_ORIGINAL_QUEUE));
        assertEquals(0, (Integer) enriched.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_RETRY_COUNT));
        assertEquals("trace-val", enriched.getMessageProperties().getHeader("custom-trace"));

        verifyNoMoreInteractions(rabbitTemplate);
    }
}
