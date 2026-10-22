package com.farmflow.resilience;

import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.controller.HealthProbeController;
import com.farmflow.entity.OutboxMessage;
import com.farmflow.service.OutboxProcessor;
import com.farmflow.service.OutboxService;
import com.farmflow.service.RabbitMqEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailureInjectionAndResilienceTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private ApplicationEventPublisher localEventPublisher;

    @Mock
    private RabbitMqEventPublisher rabbitMqEventPublisher;

    @Mock
    private FarmFlowMetrics farmFlowMetrics;

    @Mock
    private DataSource dataSource;

    @Mock
    private ConnectionFactory rabbitConnectionFactory;

    @Mock
    private Connection connection;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    @Test
    void shouldAccumulateOutboxBacklogGracefullyWhenBrokerIsUnreachable() {
        OutboxMessage pendingMessage = OutboxMessage.builder()
                .eventType("TelemetryAlert")
                .payload("{\"readingId\":\"r-100\"}")
                .traceParent("trace-100")
                .occurredOn(Instant.now())
                .attempts(0)
                .build();
        pendingMessage.setId("msg-100");

        when(outboxService.fetchUnprocessedBatch(3, 20)).thenReturn(List.of(pendingMessage));
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused to RabbitMQ:5672")))
                .when(rabbitMqEventPublisher).publish(anyString(), anyString(), any(), any(), any());

        assertDoesNotThrow(() -> outboxProcessor.processOutboxMessages());

        verify(outboxService, times(1)).markFailed(eq("msg-100"), anyString());
        verify(farmFlowMetrics, times(1)).incrementOutboxFailed();
        verify(outboxService, never()).markProcessed(anyString());
        assertTrue(outboxProcessor.isCircuitOpen());
    }

    @Test
    void shouldHaltBatchAndTripCircuitBreakerWhenBrokerConnectivityFails() {
        OutboxMessage msg1 = OutboxMessage.builder()
                .id("msg-first")
                .eventType("TelemetryAlert")
                .payload("{}")
                .occurredOn(Instant.now())
                .attempts(0)
                .build();
        OutboxMessage msg2 = OutboxMessage.builder()
                .id("msg-second")
                .eventType("TelemetryAlert")
                .payload("{}")
                .occurredOn(Instant.now())
                .attempts(0)
                .build();

        when(outboxService.fetchUnprocessedBatch(3, 20)).thenReturn(List.of(msg1, msg2));
        doThrow(new AmqpConnectException(new java.net.ConnectException("Broker offline")))
                .when(rabbitMqEventPublisher).publish(anyString(), anyString(), eq("msg-first"), any(), any());

        outboxProcessor.processOutboxMessages();

        verify(outboxService, times(1)).markFailed(eq("msg-first"), anyString());
        verify(outboxService, never()).markFailed(eq("msg-second"), anyString());
        verify(rabbitMqEventPublisher, never()).publish(anyString(), anyString(), eq("msg-second"), any(), any());
        assertTrue(outboxProcessor.isCircuitOpen());

        outboxProcessor.processOutboxMessages();
        verify(outboxService, times(1)).fetchUnprocessedBatch(3, 20);
    }

    @Test
    void shouldDrainOutboxBacklogToZeroWhenBrokerRecovers() {
        OutboxMessage pendingMessage = OutboxMessage.builder()
                .eventType("TelemetryAlert")
                .payload("{\"readingId\":\"r-200\"}")
                .traceParent("trace-200")
                .occurredOn(Instant.now())
                .attempts(1)
                .build();
        pendingMessage.setId("msg-200");

        when(outboxService.fetchUnprocessedBatch(3, 20))
                .thenReturn(List.of(pendingMessage))
                .thenReturn(Collections.emptyList());

        outboxProcessor.processOutboxMessages();

        verify(rabbitMqEventPublisher, times(1)).publish(eq("telemetry.alert.critical"), eq("{\"readingId\":\"r-200\"}"), eq("msg-200"), isNull(), eq("trace-200"));
        verify(outboxService, times(1)).markProcessed("msg-200");
        verify(farmFlowMetrics, times(1)).incrementOutboxPublished();
    }

    @Test
    void shouldReportDegradedStatusWhenBrokerIsDownWithoutKillingService() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        doThrow(new AmqpConnectException(new java.net.ConnectException("Broker offline")))
                .when(rabbitConnectionFactory).createConnection();

        HealthProbeController probeController = new HealthProbeController(dataSource, rabbitConnectionFactory);
        ResponseEntity<Map<String, Object>> response = probeController.dependencies();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("DEGRADED", response.getBody().get("status"));
        assertEquals("HEALTHY", ((Map<?, ?>) response.getBody().get("dependencies")).get("database"));
        assertEquals("UNHEALTHY", ((Map<?, ?>) response.getBody().get("dependencies")).get("rabbitmq"));
    }

    @Test
    void shouldPreventOverlappingRelayExecutionWhenBulkheadIsAcquired() throws Exception {
        OutboxMessage msg = OutboxMessage.builder()
                .id("msg-slow")
                .eventType("TelemetryAlert")
                .payload("{}")
                .occurredOn(Instant.now())
                .attempts(0)
                .build();

        java.util.concurrent.CountDownLatch startedLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finishLatch = new java.util.concurrent.CountDownLatch(1);

        when(outboxService.fetchUnprocessedBatch(3, 20)).thenReturn(List.of(msg));
        doAnswer(invocation -> {
            startedLatch.countDown();
            finishLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        }).when(rabbitMqEventPublisher).publish(anyString(), anyString(), any(), any(), any());

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.submit(() -> outboxProcessor.processOutboxMessages());

        assertTrue(startedLatch.await(2, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(outboxProcessor.isRelayBusy());

        outboxProcessor.processOutboxMessages();

        finishLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS));

        assertFalse(outboxProcessor.isRelayBusy());
        verify(outboxService, times(1)).fetchUnprocessedBatch(3, 20);
    }
}