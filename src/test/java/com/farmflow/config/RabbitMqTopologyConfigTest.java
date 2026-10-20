package com.farmflow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMqTopologyConfigTest {

    private RabbitMqTopologyConfig config;

    @BeforeEach
    void setUp() {
        config = new RabbitMqTopologyConfig();
    }

    @Test
    void shouldConfigureMainAndDlxExchanges() {
        TopicExchange mainExchange = config.mainExchange();
        assertNotNull(mainExchange);
        assertEquals("farmflow.events", mainExchange.getName());
        assertTrue(mainExchange.isDurable());

        DirectExchange dlxExchange = config.dlxExchange();
        assertNotNull(dlxExchange);
        assertEquals("farmflow.events.dlx", dlxExchange.getName());
        assertTrue(dlxExchange.isDurable());
    }

    @Test
    void shouldConfigureBusinessQueuesWithDlx() {
        Queue telemetryQueue = config.telemetryAlertsQueue();
        assertEquals("farmflow.telemetry.alerts", telemetryQueue.getName());
        assertEquals("farmflow.events.dlx", telemetryQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("telemetry.alerts.retry.10s", telemetryQueue.getArguments().get("x-dead-letter-routing-key"));

        Queue pestQueue = config.pestDetectedQueue();
        assertEquals("farmflow.pest.detected", pestQueue.getName());
        assertEquals("farmflow.events.dlx", pestQueue.getArguments().get("x-dead-letter-exchange"));
    }

    @Test
    void shouldConfigureStagedTtlRetryQueuesAndDlq() {
        Queue retry10s = config.retry10sQueue();
        assertEquals(10000, retry10s.getArguments().get("x-message-ttl"));
        assertEquals("farmflow.events", retry10s.getArguments().get("x-dead-letter-exchange"));

        Queue retry30s = config.retry30sQueue();
        assertEquals(30000, retry30s.getArguments().get("x-message-ttl"));

        Queue retry90s = config.retry90sQueue();
        assertEquals(90000, retry90s.getArguments().get("x-message-ttl"));

        Queue dlq = config.dlqQueue();
        assertEquals("farmflow.events.dlq", dlq.getName());
        assertTrue(dlq.isDurable());
    }
}