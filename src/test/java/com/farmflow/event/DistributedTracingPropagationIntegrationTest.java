package com.farmflow.event;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.consumer.ActivityCompletedConsumer;
import com.farmflow.consumer.AmqpRetryRouter;
import com.farmflow.entity.OutboxMessage;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.repository.OutboxRepository;
import com.farmflow.service.MessageDeduplicationService;
import com.farmflow.service.OutboxProcessor;
import com.farmflow.service.OutboxService;
import com.farmflow.service.RabbitMqEventPublisher;
import com.farmflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Observabilidade: Propagação End-to-End de W3C TraceContext (OpenTelemetry)")
public class DistributedTracingPropagationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ActivityCompletedConsumer activityCompletedConsumer;

    @MockitoBean
    private RabbitMqEventPublisher rabbitMqEventPublisher;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private AmqpRetryRouter retryRouter;

    @MockitoBean
    private MessageDeduplicationService deduplicationService;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @org.junit.jupiter.api.BeforeEach
    void cleanOutboxState() {
        outboxRepository.deleteAll();
        reset(rabbitMqEventPublisher);
    }

    @Test
    @DisplayName("Tracing W3C: Outbox e Publisher preservam e propagam traceparent através da mensageria")
    void shouldPersistAndPropagateW3CTraceParentAcrossOutboxAndPublisher() {
        String testTraceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String testCorrelationId = UUID.randomUUID().toString();

        MDC.put(CorrelationIdFilter.MDC_TRACEPARENT_KEY, testTraceParent);
        MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID_KEY, testCorrelationId);

        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            OutboxMessage saved = tx.execute(status -> {
                CloudEvent<Map<String, Object>> event = CloudEvent.of(
                        "https://farmflow.com/test",
                        "com.farmflow.activity.completed.v1",
                        "farm-trace-01",
                        testCorrelationId,
                        Map.of("sample", "data")
                );
                return outboxService.recordCloudEvent(event);
            });

            assertNotNull(saved);
            assertNotNull(saved.getId());
            assertEquals(testTraceParent, saved.getTraceParent());
            assertEquals(testCorrelationId, saved.getCorrelationId());

            outboxProcessor.processOutboxMessages();

            verify(rabbitMqEventPublisher, atLeastOnce()).publish(
                    anyString(),
                    any(),
                    eq(saved.getId()),
                    eq(testCorrelationId),
                    eq(testTraceParent)
            );
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("Tracing W3C: Consumidor AMQP extrai traceparent do header da mensagem e popula MDC")
    void shouldExtractTraceParentFromAmqpMessageIntoMdc() {
        String testTraceParent = "00-9876543210abcdef9876543210abcdef-fedcba0987654321-01";
        String testCorrelationId = "corr-test-w3c-42";
        String messageId = UUID.randomUUID().toString();

        Message amqpMessage = MessageBuilder
                .withBody("{\"farmId\":\"farm-trace-02\",\"activityId\":\"act-1\",\"status\":\"COMPLETED\"}".getBytes(StandardCharsets.UTF_8))
                .setMessageId(messageId)
                .setCorrelationId(testCorrelationId)
                .setHeader(CorrelationIdFilter.TRACEPARENT_HEADER, testTraceParent)
                .build();

        AtomicReference<String> capturedTraceParent = new AtomicReference<>();
        AtomicReference<String> capturedCorrelationId = new AtomicReference<>();

        when(deduplicationService.executeIfNew(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    capturedTraceParent.set(MDC.get(CorrelationIdFilter.MDC_TRACEPARENT_KEY));
                    capturedCorrelationId.set(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
                    return true;
                });

        activityCompletedConsumer.handleActivityCompleted(amqpMessage);

        assertEquals(testTraceParent, capturedTraceParent.get(), "Consumidor AMQP deve extrair traceparent para o MDC");
        assertEquals(testCorrelationId, capturedCorrelationId.get(), "Consumidor AMQP deve extrair correlationId para o MDC");
    }
}
