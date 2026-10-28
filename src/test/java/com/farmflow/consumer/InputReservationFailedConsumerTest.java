package com.farmflow.consumer;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.entity.FarmActivity;
import com.farmflow.enums.ActivityPriority;
import com.farmflow.enums.ActivityType;
import com.farmflow.repository.ActivityRepository;
import com.farmflow.service.MessageDeduplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class InputReservationFailedConsumerTest {

    @Mock
    private MessageDeduplicationService deduplicationService;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private com.farmflow.repository.SagaAuditLogRepository sagaAuditLogRepository;

    @Mock
    private com.farmflow.service.OutboxService outboxService;

    @Mock
    private FarmFlowMetrics farmFlowMetrics;

    @Mock
    private AmqpRetryRouter retryRouter;

    private InputReservationFailedConsumer sut;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sut = new InputReservationFailedConsumer(
                deduplicationService,
                activityRepository,
                sagaAuditLogRepository,
                outboxService,
                objectMapper,
                farmFlowMetrics,
                retryRouter
        );
    }

    @Test
    @DisplayName("deve reabrir atividade e registrar nota de compensacao ao receber evento de falha")
    void shouldProcessInputReservationFailedAndReopenActivityWithCompensationNotes() {
        String payload = """
            {
                "id": "evt-fail-1",
                "source": "https://farmflow.com/supply-service",
                "type": "com.farmflow.supply.input.reservation.failed.v1",
                "specversion": "1.0",
                "time": "2026-10-26T14:00:00Z",
                "tenantId": "farm-uberaba",
                "correlationId": "corr-fail-100",
                "data": {
                    "failureId": "fail-99",
                    "farmId": "farm-uberaba",
                    "activityId": "act-101",
                    "itemId": "seed-soja-01",
                    "requestedQuantity": 200.0,
                    "reason": "INSUFFICIENT_STOCK",
                    "failedAt": "2026-10-26T14:00:00Z"
                }
            }
            """;

        FarmActivity activity = FarmActivity.builder()
                .id("act-101")
                .farmId("farm-uberaba")
                .plantationId("plt-1")
                .title("Plantio de Soja")
                .type(ActivityType.PLANTING)
                .priority(ActivityPriority.HIGH)
                .completed(true)
                .notes("Atividade concluida inicialmente")
                .completedAt("2026-10-26T13:50:00Z")
                .build();

        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-fail-100");
        props.setCorrelationId("corr-fail-100");
        props.setHeader(CorrelationIdFilter.TRACEPARENT_HEADER, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(InputReservationFailedConsumer.CONSUMER_NAME),
                eq("msg-fail-100"),
                eq("InputReservationFailed"),
                any(Runnable.class)
        );

        when(activityRepository.findByIdAndFarmId("act-101", "farm-uberaba")).thenReturn(Optional.of(activity));

        sut.handleInputReservationFailed(message);

        ArgumentCaptor<FarmActivity> captor = ArgumentCaptor.forClass(FarmActivity.class);
        verify(activityRepository).save(captor.capture());

        FarmActivity savedActivity = captor.getValue();
        assertEquals(com.farmflow.enums.ActivityStatus.IN_PROGRESS, savedActivity.getStatus());
        assertFalse(savedActivity.getCompleted());
        assertNull(savedActivity.getCompletedAt());
        verify(sagaAuditLogRepository).save(any(com.farmflow.entity.SagaAuditLog.class));
        verify(outboxService).recordCloudEvent(any(com.farmflow.event.cloudevents.CloudEvent.class));
        verify(farmFlowMetrics).incrementConsumerProcessed("InputReservationFailed");
        verifyNoInteractions(retryRouter);
    }

    @Test
    @DisplayName("deve descartar mensagens duplicadas sem reprocessar compensacao")
    void shouldDiscardDuplicateInputReservationFailedMessage() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("dup-fail-1");
        Message message = new Message("{\"activityId\":\"act-1\",\"farmId\":\"f-1\"}".getBytes(StandardCharsets.UTF_8), props);

        when(deduplicationService.executeIfNew(
                eq(InputReservationFailedConsumer.CONSUMER_NAME),
                eq("dup-fail-1"),
                eq("InputReservationFailed"),
                any(Runnable.class)
        )).thenReturn(false);

        sut.handleInputReservationFailed(message);

        verify(farmFlowMetrics).incrementConsumerDuplicate("InputReservationFailed");
        verify(farmFlowMetrics, never()).incrementConsumerProcessed(anyString());
        verifyNoInteractions(activityRepository);
        verifyNoInteractions(retryRouter);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando mensagem nao possuir messageId")
    void shouldRoutePoisonWhenMissingMessageId() {
        MessageProperties props = new MessageProperties();
        props.setMessageId(null);
        Message message = new Message("{\"activityId\":\"act-1\",\"farmId\":\"f-1\"}".getBytes(StandardCharsets.UTF_8), props);

        sut.handleInputReservationFailed(message);

        verify(retryRouter).routePoison(eq(message), eq(RabbitMqTopologyConfig.INPUT_RESERVATION_FAILED_QUEUE), eq("MISSING_MESSAGE_ID"));
        verify(farmFlowMetrics).incrementConsumerFailed("InputReservationFailed");
        verifyNoInteractions(deduplicationService);
        verifyNoInteractions(activityRepository);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando mensagem contiver corpo malformado")
    void shouldRoutePoisonWhenMalformedJsonPayload() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-malformed-fail");
        Message message = new Message("not-valid-json".getBytes(StandardCharsets.UTF_8), props);

        sut.handleInputReservationFailed(message);

        verify(retryRouter).routePoison(eq(message), eq(RabbitMqTopologyConfig.INPUT_RESERVATION_FAILED_QUEUE), eq("MALFORMED_JSON_PAYLOAD"));
        verify(farmFlowMetrics).incrementConsumerFailed("InputReservationFailed");
        verifyNoInteractions(deduplicationService);
        verifyNoInteractions(activityRepository);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando payload omitir farmId")
    void shouldRoutePoisonWhenMissingFarmId() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-no-farm-id");
        Message message = new Message("{\"activityId\":\"act-1\"}".getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(InputReservationFailedConsumer.CONSUMER_NAME),
                eq("msg-no-farm-id"),
                eq("InputReservationFailed"),
                any(Runnable.class)
        );

        sut.handleInputReservationFailed(message);

        verify(retryRouter).routePoison(eq(message), eq(RabbitMqTopologyConfig.INPUT_RESERVATION_FAILED_QUEUE), contains("Missing required farmId"));
        verify(farmFlowMetrics).incrementConsumerFailed("InputReservationFailed");
        verifyNoInteractions(activityRepository);
    }

    @Test
    @DisplayName("deve escalonar falhas transitorias para fila de retry progressivo")
    void shouldEscalateTransientFailureToRetryQueue() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-transient-fail");
        Message message = new Message("{\"activityId\":\"act-1\",\"farmId\":\"f-1\"}".getBytes(StandardCharsets.UTF_8), props);

        RuntimeException transientError = new RuntimeException("DB connection dropped");
        when(deduplicationService.executeIfNew(
                eq(InputReservationFailedConsumer.CONSUMER_NAME),
                eq("msg-transient-fail"),
                eq("InputReservationFailed"),
                any(Runnable.class)
        )).thenThrow(transientError);

        sut.handleInputReservationFailed(message);

        verify(retryRouter).routeFailure(
                eq(message),
                eq(RabbitMqTopologyConfig.INPUT_RESERVATION_FAILED_QUEUE),
                eq("supply.input.reservation.failed"),
                eq(transientError)
        );
        verify(farmFlowMetrics).incrementConsumerFailed("InputReservationFailed");
        verifyNoInteractions(activityRepository);
    }
}
