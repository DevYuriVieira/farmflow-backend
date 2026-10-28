package com.farmflow.consumer;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.entity.Machinery;
import com.farmflow.repository.MachineryRepository;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ActivityCompletedMachineryConsumerTest {

    @Mock
    private MessageDeduplicationService deduplicationService;

    @Mock
    private MachineryRepository machineryRepository;

    @Mock
    private FarmFlowMetrics farmFlowMetrics;

    @Mock
    private AmqpRetryRouter retryRouter;

    private ActivityCompletedMachineryConsumer sut;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sut = new ActivityCompletedMachineryConsumer(
                deduplicationService,
                machineryRepository,
                objectMapper,
                farmFlowMetrics,
                retryRouter
        );
    }

    @Test
    @DisplayName("deve incrementar horimetro de maquinario monotonicamente quando presente no evento")
    void shouldUpdateMachineryOperatingHoursMonotonicallyWhenMachineryIsPresent() {
        String payload = """
            {
                "id": "evt-act-mach-1",
                "source": "https://farmflow.com/core-service",
                "type": "com.farmflow.activity.completed.v1",
                "specversion": "1.0",
                "time": "2026-10-26T14:00:00Z",
                "tenantId": "farm-uberaba",
                "correlationId": "corr-mach-100",
                "data": {
                    "activityId": "act-201",
                    "farmId": "farm-uberaba",
                    "activityType": "HARVESTING",
                    "title": "Colheita de Milho",
                    "completedAt": "2026-10-26T14:00:00Z",
                    "machineryId": "mach-jd-8000",
                    "hoursUsed": 6.5
                }
            }
            """;

        Machinery machinery = Machinery.builder()
                .id("mach-jd-8000")
                .farmId("farm-uberaba")
                .name("Trator John Deere 8R")
                .type("Tractor")
                .model("8R 370")
                .operatingHours(120.0)
                .build();

        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-act-mach-1");
        props.setCorrelationId("corr-mach-100");
        props.setHeader(CorrelationIdFilter.TRACEPARENT_HEADER, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(ActivityCompletedMachineryConsumer.CONSUMER_NAME),
                eq("msg-act-mach-1"),
                eq("MachineryActivityCompleted"),
                any(Runnable.class)
        );

        when(machineryRepository.incrementOperatingHoursAtomic(eq("mach-jd-8000"), eq("farm-uberaba"), eq(6.5), any(Instant.class)))
                .thenReturn(1);

        sut.handleActivityCompleted(message);

        verify(machineryRepository).incrementOperatingHoursAtomic(eq("mach-jd-8000"), eq("farm-uberaba"), eq(6.5), any(Instant.class));
        verify(farmFlowMetrics).incrementConsumerProcessed("MachineryActivityCompleted");
        verifyNoInteractions(retryRouter);
    }

    @Test
    @DisplayName("deve ignorar atualizacao de horimetro quando maquinario nao for informado")
    void shouldSkipOperatingHoursUpdateWhenMachineryIdIsMissingOrHoursIsZero() {
        String payload = """
            {
                "data": {
                    "activityId": "act-no-mach",
                    "farmId": "farm-uberaba",
                    "activityType": "MANUAL_WEEDING"
                }
            }
            """;

        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-no-mach");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(ActivityCompletedMachineryConsumer.CONSUMER_NAME),
                eq("msg-no-mach"),
                eq("MachineryActivityCompleted"),
                any(Runnable.class)
        );

        sut.handleActivityCompleted(message);

        verifyNoInteractions(machineryRepository);
        verify(farmFlowMetrics).incrementConsumerProcessed("MachineryActivityCompleted");
        verifyNoInteractions(retryRouter);
    }

    @Test
    @DisplayName("deve descartar mensagem duplicada sem reprocessamento")
    void shouldDiscardDuplicateMachineryActivityCompletedEvent() {
        String payload = "{\"data\": {\"machineryId\": \"mach-1\", \"hoursUsed\": 2.0}}";
        MessageProperties props = new MessageProperties();
        props.setMessageId("dup-mach-msg");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        when(deduplicationService.executeIfNew(
                eq(ActivityCompletedMachineryConsumer.CONSUMER_NAME),
                eq("dup-mach-msg"),
                eq("MachineryActivityCompleted"),
                any(Runnable.class)
        )).thenReturn(false);

        sut.handleActivityCompleted(message);

        verify(farmFlowMetrics).incrementConsumerDuplicate("MachineryActivityCompleted");
        verifyNoInteractions(machineryRepository);
        verifyNoInteractions(retryRouter);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando messageId for nulo")
    void shouldRoutePoisonMessageWhenMessageIdIsMissing() {
        String payload = "{\"data\": {\"machineryId\": \"mach-1\"}}";
        MessageProperties props = new MessageProperties();
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        sut.handleActivityCompleted(message);

        verify(retryRouter).routePoison(message, RabbitMqTopologyConfig.MACHINERY_ACTIVITY_COMPLETED_QUEUE, "MISSING_MESSAGE_ID");
        verify(farmFlowMetrics).incrementConsumerFailed("MachineryActivityCompleted");
        verifyNoInteractions(deduplicationService);
    }

    @Test
    @DisplayName("deve rotear para DLQ quando payload for JSON malformado")
    void shouldRoutePoisonMessageWhenPayloadIsMalformedJson() {
        String payload = "MALFORMED_JSON_STRING";
        MessageProperties props = new MessageProperties();
        props.setMessageId("bad-json-mach");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        sut.handleActivityCompleted(message);

        verify(retryRouter).routePoison(message, RabbitMqTopologyConfig.MACHINERY_ACTIVITY_COMPLETED_QUEUE, "MALFORMED_JSON_PAYLOAD");
        verify(farmFlowMetrics).incrementConsumerFailed("MachineryActivityCompleted");
        verifyNoInteractions(deduplicationService);
    }

    @Test
    @DisplayName("deve rotear falha transitoria para fila de retry")
    void shouldRouteFailureViaRetryRouterOnTransientException() {
        String payload = "{\"data\": {\"farmId\": \"farm-1\", \"machineryId\": \"mach-error\", \"hoursUsed\": 3.0}}";
        MessageProperties props = new MessageProperties();
        props.setMessageId("transient-mach-msg");
        Message message = new Message(payload.getBytes(StandardCharsets.UTF_8), props);

        doAnswer(inv -> {
            Runnable r = inv.getArgument(3);
            r.run();
            return true;
        }).when(deduplicationService).executeIfNew(
                eq(ActivityCompletedMachineryConsumer.CONSUMER_NAME),
                eq("transient-mach-msg"),
                eq("MachineryActivityCompleted"),
                any(Runnable.class)
        );

        when(machineryRepository.incrementOperatingHoursAtomic(eq("mach-error"), eq("farm-1"), eq(3.0), any(Instant.class)))
                .thenThrow(new RuntimeException("Database timeout"));

        sut.handleActivityCompleted(message);

        verify(retryRouter).routeFailure(eq(message), eq(RabbitMqTopologyConfig.MACHINERY_ACTIVITY_COMPLETED_QUEUE), eq("machinery.activity.completed"), any(RuntimeException.class));
        verify(farmFlowMetrics).incrementConsumerFailed("MachineryActivityCompleted");
    }
}
