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
                .when(rabbitMqEventPublisher).publish(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> outboxProcessor.processOutboxMessages());

        verify(outboxService, times(1)).markFailed(eq("msg-100"), anyString());
        verify(farmFlowMetrics, times(1)).incrementOutboxFailed();
        verify(outboxService, never()).markProcessed(anyString());
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

        verify(rabbitMqEventPublisher, times(1)).publish(eq("telemetry.alert.critical"), eq("{\"readingId\":\"r-200\"}"), eq("trace-200"));
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
}