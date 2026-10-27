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

        Queue inputReservedQueue = config.inputReservedQueue();
        assertEquals("farmflow.supply.input.reserved", inputReservedQueue.getName());
        assertEquals("farmflow.events.dlx", inputReservedQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("supply.input.reserved.retry.10s", inputReservedQueue.getArguments().get("x-dead-letter-routing-key"));

        Queue machineryActivityQueue = config.machineryActivityCompletedQueue();
        assertEquals("farmflow.machinery.activity.completed", machineryActivityQueue.getName());
        assertEquals("farmflow.events.dlx", machineryActivityQueue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("machinery.activity.completed.retry.10s", machineryActivityQueue.getArguments().get("x-dead-letter-routing-key"));
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

    @Test
    void shouldConfigureAllRetryAndDlqBindings() {
        TopicExchange mainExchange = config.mainExchange();
        DirectExchange dlxExchange = config.dlxExchange();

        Queue telemetryQueue = config.telemetryAlertsQueue();
        Queue pestQueue = config.pestDetectedQueue();
        Queue machineryQueue = config.machineryMaintenanceQueue();
        Queue inputReservedQueue = config.inputReservedQueue();
        Queue machineryActivityQueue = config.machineryActivityCompletedQueue();

        Queue retry10s = config.retry10sQueue();
        Queue retry30s = config.retry30sQueue();
        Queue retry90s = config.retry90sQueue();
        Queue dlq = config.dlqQueue();

        assertEquals("telemetry.alert.#", config.telemetryBinding(telemetryQueue, mainExchange).getRoutingKey());
        assertEquals("telemetry.alerts.#", config.telemetryRetryReturnBinding(telemetryQueue, mainExchange).getRoutingKey());
        assertEquals("pest.detected.#", config.pestBinding(pestQueue, mainExchange).getRoutingKey());
        assertEquals("machinery.maintenance.#", config.machineryBinding(machineryQueue, mainExchange).getRoutingKey());
        assertEquals("supply.input.reserved.#", config.inputReservedBinding(inputReservedQueue, mainExchange).getRoutingKey());
        assertEquals("supply.input.reserved.retry.#", config.inputReservedRetryReturnBinding(inputReservedQueue, mainExchange).getRoutingKey());
        assertEquals("activity.#", config.machineryActivityBinding(machineryActivityQueue, mainExchange).getRoutingKey());
        assertEquals("machinery.activity.completed.retry.#", config.machineryActivityRetryReturnBinding(machineryActivityQueue, mainExchange).getRoutingKey());

        assertEquals("telemetry.alerts.retry.10s", config.retry10sBinding(retry10s, dlxExchange).getRoutingKey());
        assertEquals("pest.detected.retry.10s", config.retry10sPestBinding(retry10s, dlxExchange).getRoutingKey());
        assertEquals("machinery.maintenance.retry.10s", config.retry10sMachineryBinding(retry10s, dlxExchange).getRoutingKey());
        assertEquals("supply.input.reserved.retry.10s", config.retry10sInputReservedBinding(retry10s, dlxExchange).getRoutingKey());
        assertEquals("machinery.activity.completed.retry.10s", config.retry10sMachineryActivityCompletedBinding(retry10s, dlxExchange).getRoutingKey());

        assertEquals("telemetry.alerts.retry.30s", config.retry30sBinding(retry30s, dlxExchange).getRoutingKey());
        assertEquals("pest.detected.retry.30s", config.retry30sPestBinding(retry30s, dlxExchange).getRoutingKey());
        assertEquals("machinery.maintenance.retry.30s", config.retry30sMachineryBinding(retry30s, dlxExchange).getRoutingKey());
        assertEquals("supply.input.reserved.retry.30s", config.retry30sInputReservedBinding(retry30s, dlxExchange).getRoutingKey());
        assertEquals("machinery.activity.completed.retry.30s", config.retry30sMachineryActivityCompletedBinding(retry30s, dlxExchange).getRoutingKey());

        assertEquals("telemetry.alerts.retry.90s", config.retry90sBinding(retry90s, dlxExchange).getRoutingKey());
        assertEquals("pest.detected.retry.90s", config.retry90sPestBinding(retry90s, dlxExchange).getRoutingKey());
        assertEquals("machinery.maintenance.retry.90s", config.retry90sMachineryBinding(retry90s, dlxExchange).getRoutingKey());
        assertEquals("supply.input.reserved.retry.90s", config.retry90sInputReservedBinding(retry90s, dlxExchange).getRoutingKey());
        assertEquals("machinery.activity.completed.retry.90s", config.retry90sMachineryActivityCompletedBinding(retry90s, dlxExchange).getRoutingKey());

        assertEquals("events.dlq", config.dlqBinding(dlq, dlxExchange).getRoutingKey());
    }
}