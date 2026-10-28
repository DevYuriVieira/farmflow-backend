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
class InputReservedConsumerTest {

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

    private InputReservedConsumer sut;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sut = new InputReservedConsumer(
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
    @DisplayName("deve confirmar reserva de insumo e atualizar notas da atividade")
    void shouldProcessInputReservedConfirmationAndAnnotateActivity() {
        String payload = """
            {
                "id": "evt-res-1",
                "source": "https://farmflow.com/supply-service",
                "type": "com.farmflow.supply.input.reserved.v1",
                "specversion": "1.0",
                "time": "2026-10-26T14:00:00Z",
                "tenantId": "farm-uberaba",
                "correlationId": "corr-res-100",
                "data": {
                    "reservationId": "res-99",
                    "farmId": "farm-uberaba",
                    "activityId": "act-101",
                    "itemId": "item-fertilizer-x",
                    "quantity": 100.0,
                    "unit": "kg",
                    "reservedAt": "2026-10-26T14:00:00Z"
                }
            }
            """;

        FarmActivity activity = FarmActivity.builder()
                .id("act-101")
                .farmId("farm-uberaba")
                .plantationId("plt-1")
                .title("Adubacao de Cobertura")
                .type(ActivityType.FERTILIZATION)
                .priority(ActivityPriority.MEDIUM)
                .status(com.farmflow.enums.ActivityStatus.PENDING_CONFIRMATION)
                .completed(false)
                .notes("Atividade aguardando confirmacao de estoque")
                .build();

        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-res-100");
        props.setCorrelationId("corr-res-100");
        props.setHeader(CorrelationIdFilter.TRACEPARENT_HEADER, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(InputReservedConsumer.CONSUMER_NAME),
                eq("msg-res-100"),
                eq("InputReserved"),
                any(Runnable.class)
        );

        when(activityRepository.findByIdAndFarmId("act-101", "farm-uberaba")).thenReturn(Optional.of(activity));

        sut.handleInputReserved(message);

        ArgumentCaptor<FarmActivity> captor = ArgumentCaptor.forClass(FarmActivity.class);
        verify(activityRepository).save(captor.capture());
        FarmActivity savedActivity = captor.getValue();

        assertEquals(com.farmflow.enums.ActivityStatus.COMPLETED, savedActivity.getStatus());
        assertTrue(savedActivity.getCompleted());
        assertNotNull(savedActivity.getCompletedAt());
        verify(sagaAuditLogRepository).save(any(com.farmflow.entity.SagaAuditLog.class));
        verify(outboxService).recordCloudEvent(any(com.farmflow.event.cloudevents.CloudEvent.class));
        verify(farmFlowMetrics).incrementConsumerProcessed("InputReserved");
        verifyNoInteractions(retryRouter);
    }

    @Test
    @DisplayName("deve descartar evento duplicado sem reprocessar")
    void shouldDiscardDuplicateInputReservedEvent() {
        String payload = "{\"data\": {\"farmId\": \"f-1\", \"activityId\": \"a-1\"}}";
        MessageProperties props = new MessageProperties();
        props.setMessageId("dup-msg-1");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        when(deduplicationService.executeIfNew(
                eq(InputReservedConsumer.CONSUMER_NAME),
                eq("dup-msg-1"),
                eq("InputReserved"),
                any(Runnable.class)
        )).thenReturn(false);

        sut.handleInputReserved(message);

        verify(farmFlowMetrics).incrementConsumerDuplicate("InputReserved");
        verifyNoInteractions(activityRepository);
        verifyNoInteractions(retryRouter);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando messageId for nulo")
    void shouldRoutePoisonMessageWhenMessageIdIsMissing() {
        String payload = "{\"data\": {\"farmId\": \"f-1\", \"activityId\": \"a-1\"}}";
        MessageProperties props = new MessageProperties();
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        sut.handleInputReserved(message);

        verify(retryRouter).routePoison(message, RabbitMqTopologyConfig.INPUT_RESERVED_QUEUE, "MISSING_MESSAGE_ID");
        verify(farmFlowMetrics).incrementConsumerFailed("InputReserved");
        verifyNoInteractions(deduplicationService);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando payload for JSON malformado")
    void shouldRoutePoisonMessageWhenPayloadIsMalformedJson() {
        String payload = "NOT_A_JSON";
        MessageProperties props = new MessageProperties();
        props.setMessageId("bad-json-msg");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        sut.handleInputReserved(message);

        verify(retryRouter).routePoison(message, RabbitMqTopologyConfig.INPUT_RESERVED_QUEUE, "MALFORMED_JSON_PAYLOAD");
        verify(farmFlowMetrics).incrementConsumerFailed("InputReserved");
        verifyNoInteractions(deduplicationService);
    }

    @Test
    @DisplayName("deve rotear para DLQ como poison quando faltar campos obrigatorios")
    void shouldRoutePoisonMessageWhenMandatoryFieldsAreMissing() {
        String payload = "{\"data\": {\"itemId\": \"item-x\"}}";
        MessageProperties props = new MessageProperties();
        props.setMessageId("missing-fields-msg");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(InputReservedConsumer.CONSUMER_NAME),
                eq("missing-fields-msg"),
                eq("InputReserved"),
                any(Runnable.class)
        );

        sut.handleInputReserved(message);

        verify(retryRouter).routePoison(eq(message), eq(RabbitMqTopologyConfig.INPUT_RESERVED_QUEUE), contains("Missing required farmId"));
        verify(farmFlowMetrics).incrementConsumerFailed("InputReserved");
    }

    @Test
    @DisplayName("deve rotear falha transitoria para fila de retry")
    void shouldRouteFailureViaRetryRouterOnTransientException() {
        String payload = "{\"data\": {\"farmId\": \"f-1\", \"activityId\": \"act-error\"}}";
        MessageProperties props = new MessageProperties();
        props.setMessageId("transient-msg");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(InputReservedConsumer.CONSUMER_NAME),
                eq("transient-msg"),
                eq("InputReserved"),
                any(Runnable.class)
        );

        when(activityRepository.findByIdAndFarmId("act-error", "f-1")).thenThrow(new RuntimeException("Database connectivity lost"));

        sut.handleInputReserved(message);

        verify(retryRouter).routeFailure(eq(message), eq(RabbitMqTopologyConfig.INPUT_RESERVED_QUEUE), eq("supply.input.reserved"), any(RuntimeException.class));
        verify(farmFlowMetrics).incrementConsumerFailed("InputReserved");
    }
}
