package com.farmflow.consumer;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.repository.InventoryRepository;
import com.farmflow.service.MessageDeduplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ActivityCompletedConsumerTest {

    @Mock
    private MessageDeduplicationService deduplicationService;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private FarmFlowMetrics farmFlowMetrics;

    @Mock
    private AmqpRetryRouter retryRouter;

    private ActivityCompletedConsumer sut;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sut = new ActivityCompletedConsumer(
                deduplicationService,
                inventoryRepository,
                objectMapper,
                farmFlowMetrics,
                retryRouter
        );
    }

    @Test
    @DisplayName("deve processar mensagem de atividade concluída e deduzir estoque atomicamente")
    void shouldProcessActivityCompletedAndDeductStock() {
        String payload = """
            {
                "activityId": "act-100",
                "farmId": "farm-uberaba",
                "inputsUsed": [
                    { "itemId": "item-npk", "quantity": 150.0, "unit": "kg" }
                ]
            }
            """;
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-act-100");
        props.setCorrelationId("corr-100");
        props.setHeader(CorrelationIdFilter.TRACEPARENT_HEADER, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(ActivityCompletedConsumer.CONSUMER_NAME),
                eq("msg-act-100"),
                eq("ActivityCompleted"),
                any(Runnable.class)
        );

        when(inventoryRepository.deductAtomically(eq("item-npk"), eq(150.0), any(Instant.class))).thenReturn(1);

        sut.handleActivityCompleted(message);

        verify(deduplicationService).executeIfNew(
                eq(ActivityCompletedConsumer.CONSUMER_NAME),
                eq("msg-act-100"),
                eq("ActivityCompleted"),
                any(Runnable.class)
        );
        verify(inventoryRepository).deductAtomically(eq("item-npk"), eq(150.0), any(Instant.class));
        verify(farmFlowMetrics).incrementConsumerProcessed("ActivityCompleted");
        verifyNoInteractions(retryRouter);
    }

    @Test
    @DisplayName("deve processar envelope CloudEvents v1 e deduzir insumos utilizados")
    void shouldProcessCloudEventsV1Payload() {
        String payload = """
            {
                "id": "evt-ce-1",
                "source": "https://farmflow.com/core-service",
                "type": "com.farmflow.activity.completed.v1",
                "specversion": "1.0",
                "time": "2026-10-24T10:00:00Z",
                "tenantId": "farm-goias",
                "correlationId": "corr-ce-1",
                "data": {
                    "activityId": "act-ce-50",
                    "farmId": "farm-goias",
                    "title": "Plantio de Soja",
                    "completedAt": "2026-10-24T10:00:00Z",
                    "inputsUsed": [
                        { "itemId": "seed-soja-01", "quantity": 25.5, "unit": "kg" }
                    ]
                }
            }
            """;
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-ce-1");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(ActivityCompletedConsumer.CONSUMER_NAME),
                eq("msg-ce-1"),
                eq("ActivityCompleted"),
                any(Runnable.class)
        );

        when(inventoryRepository.deductAtomically(eq("seed-soja-01"), eq(25.5), any(Instant.class))).thenReturn(1);

        sut.handleActivityCompleted(message);

        verify(inventoryRepository).deductAtomically(eq("seed-soja-01"), eq(25.5), any(Instant.class));
        verify(farmFlowMetrics).incrementConsumerProcessed("ActivityCompleted");
    }

    @Test
    @DisplayName("deve descartar mensagens duplicadas sem acionar efeitos colaterais")
    void shouldDiscardDuplicateMessages() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("dup-act-1");
        Message message = new Message("{\"activityId\":\"act-1\",\"farmId\":\"f-1\"}".getBytes(StandardCharsets.UTF_8), props);

        when(deduplicationService.executeIfNew(
                eq(ActivityCompletedConsumer.CONSUMER_NAME),
                eq("dup-act-1"),
                eq("ActivityCompleted"),
                any(Runnable.class)
        )).thenReturn(false);

        sut.handleActivityCompleted(message);

        verify(farmFlowMetrics).incrementConsumerDuplicate("ActivityCompleted");
        verify(farmFlowMetrics, never()).incrementConsumerProcessed(anyString());
        verifyNoInteractions(inventoryRepository);
        verifyNoInteractions(retryRouter);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando mensagem não possuir messageId obrigatório")
    void shouldRoutePoisonWhenMissingMessageId() {
        MessageProperties props = new MessageProperties();
        props.setMessageId(null);
        Message message = new Message("{\"activityId\":\"act-1\",\"farmId\":\"f-1\"}".getBytes(StandardCharsets.UTF_8), props);

        sut.handleActivityCompleted(message);

        verify(retryRouter).routePoison(eq(message), eq(RabbitMqTopologyConfig.ACTIVITY_COMPLETED_QUEUE), eq("MISSING_MESSAGE_ID"));
        verify(farmFlowMetrics).incrementConsumerFailed("ActivityCompleted");
        verifyNoInteractions(deduplicationService);
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando corpo da mensagem não for JSON válido")
    void shouldRoutePoisonWhenMalformedPayload() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-malformed");
        Message message = new Message("malformed-not-json".getBytes(StandardCharsets.UTF_8), props);

        sut.handleActivityCompleted(message);

        verify(retryRouter).routePoison(eq(message), eq(RabbitMqTopologyConfig.ACTIVITY_COMPLETED_QUEUE), eq("MALFORMED_JSON_PAYLOAD"));
        verify(farmFlowMetrics).incrementConsumerFailed("ActivityCompleted");
        verifyNoInteractions(deduplicationService);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando payload omitir farmId obrigatório")
    void shouldRoutePoisonWhenMissingFarmId() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-no-farm");
        Message message = new Message("{\"activityId\":\"act-1\"}".getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(ActivityCompletedConsumer.CONSUMER_NAME),
                eq("msg-no-farm"),
                eq("ActivityCompleted"),
                any(Runnable.class)
        );

        sut.handleActivityCompleted(message);

        verify(retryRouter).routePoison(eq(message), eq(RabbitMqTopologyConfig.ACTIVITY_COMPLETED_QUEUE), contains("Missing required farmId"));
        verify(farmFlowMetrics).incrementConsumerFailed("ActivityCompleted");
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("deve escalonar falhas transitórias para fila de retry progressivo")
    void shouldEscalateTransientFailureToRetryQueue() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-transient");
        Message message = new Message("{\"activityId\":\"act-1\",\"farmId\":\"f-1\"}".getBytes(StandardCharsets.UTF_8), props);

        RuntimeException transientError = new RuntimeException("Database lock acquisition timeout");
        when(deduplicationService.executeIfNew(
                eq(ActivityCompletedConsumer.CONSUMER_NAME),
                eq("msg-transient"),
                eq("ActivityCompleted"),
                any(Runnable.class)
        )).thenThrow(transientError);

        sut.handleActivityCompleted(message);

        verify(retryRouter).routeFailure(
                eq(message),
                eq(RabbitMqTopologyConfig.ACTIVITY_COMPLETED_QUEUE),
                eq("activity.completed"),
                eq(transientError)
        );
        verify(farmFlowMetrics).incrementConsumerFailed("ActivityCompleted");
        verifyNoInteractions(inventoryRepository);
    }
}
