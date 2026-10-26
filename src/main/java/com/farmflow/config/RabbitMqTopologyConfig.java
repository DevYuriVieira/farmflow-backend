package com.farmflow.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqTopologyConfig {

    public static final String MAIN_EXCHANGE = "farmflow.events";
    public static final String DLX_EXCHANGE = "farmflow.events.dlx";

    public static final String UNROUTABLE_EXCHANGE = "farmflow.unroutable.exchange";
    public static final String UNROUTABLE_QUEUE = "farmflow.unroutable.queue";

    public static final String TELEMETRY_ALERTS_QUEUE = "farmflow.telemetry.alerts";
    public static final String PEST_DETECTED_QUEUE = "farmflow.pest.detected";
    public static final String MACHINERY_MAINTENANCE_QUEUE = "farmflow.machinery.maintenance";
    public static final String ACTIVITY_COMPLETED_QUEUE = "farmflow.activity.completed";
    public static final String INPUT_RESERVATION_FAILED_QUEUE = "farmflow.supply.input.reservation.failed";
    public static final String INPUT_RESERVED_QUEUE = "farmflow.supply.input.reserved";

    public static final String RETRY_10S_QUEUE = "farmflow.events.retry.10s";
    public static final String RETRY_30S_QUEUE = "farmflow.events.retry.30s";
    public static final String RETRY_90S_QUEUE = "farmflow.events.retry.90s";
    public static final String DLQ_QUEUE = "farmflow.events.dlq";

    public static final String ROUTING_TELEMETRY = "telemetry.alert.#";
    public static final String ROUTING_TELEMETRY_RETRY_RETURN = "telemetry.alerts.#";
    public static final String ROUTING_PEST = "pest.detected.#";
    public static final String ROUTING_MACHINERY = "machinery.maintenance.#";
    public static final String ROUTING_ACTIVITY = "activity.#";
    public static final String ROUTING_INPUT_RESERVATION_FAILED = "supply.input.reservation.failed.#";
    public static final String ROUTING_INPUT_RESERVATION_FAILED_RETRY_RETURN = "supply.input.reservation.failed.retry.#";
    public static final String ROUTING_INPUT_RESERVED = "supply.input.reserved.#";
    public static final String ROUTING_INPUT_RESERVED_RETRY_RETURN = "supply.input.reserved.retry.#";

    @Bean
    public FanoutExchange unroutableExchange() {
        return new FanoutExchange(UNROUTABLE_EXCHANGE, true, false);
    }

    @Bean
    public Queue unroutableQueue() {
        return new Queue(UNROUTABLE_QUEUE, true, false, false);
    }

    @Bean
    public Binding unroutableBinding(Queue unroutableQueue, FanoutExchange unroutableExchange) {
        return BindingBuilder.bind(unroutableQueue).to(unroutableExchange);
    }

    @Bean
    public TopicExchange mainExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("alternate-exchange", UNROUTABLE_EXCHANGE);
        return new TopicExchange(MAIN_EXCHANGE, true, false, args);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue telemetryAlertsQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "telemetry.alerts.retry.10s");
        return new Queue(TELEMETRY_ALERTS_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue pestDetectedQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "pest.detected.retry.10s");
        return new Queue(PEST_DETECTED_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue machineryMaintenanceQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "machinery.maintenance.retry.10s");
        return new Queue(MACHINERY_MAINTENANCE_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue activityCompletedQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "activity.completed.retry.10s");
        return new Queue(ACTIVITY_COMPLETED_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue inputReservationFailedQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "supply.input.reservation.failed.retry.10s");
        return new Queue(INPUT_RESERVATION_FAILED_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue inputReservedQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "supply.input.reserved.retry.10s");
        return new Queue(INPUT_RESERVED_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue retry10sQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 10000);
        args.put("x-dead-letter-exchange", MAIN_EXCHANGE);
        return new Queue(RETRY_10S_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue retry30sQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 30000);
        args.put("x-dead-letter-exchange", MAIN_EXCHANGE);
        return new Queue(RETRY_30S_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue retry90sQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 90000);
        args.put("x-dead-letter-exchange", MAIN_EXCHANGE);
        return new Queue(RETRY_90S_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue dlqQueue() {
        return new Queue(DLQ_QUEUE, true, false, false);
    }

    @Bean
    public Binding telemetryBinding(Queue telemetryAlertsQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(telemetryAlertsQueue).to(mainExchange).with(ROUTING_TELEMETRY);
    }

    @Bean
    public Binding telemetryRetryReturnBinding(Queue telemetryAlertsQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(telemetryAlertsQueue).to(mainExchange).with(ROUTING_TELEMETRY_RETRY_RETURN);
    }

    @Bean
    public Binding pestBinding(Queue pestDetectedQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(pestDetectedQueue).to(mainExchange).with(ROUTING_PEST);
    }

    @Bean
    public Binding machineryBinding(Queue machineryMaintenanceQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(machineryMaintenanceQueue).to(mainExchange).with(ROUTING_MACHINERY);
    }

    @Bean
    public Binding activityBinding(Queue activityCompletedQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(activityCompletedQueue).to(mainExchange).with(ROUTING_ACTIVITY);
    }

    @Bean
    public Binding inputReservationFailedBinding(Queue inputReservationFailedQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(inputReservationFailedQueue).to(mainExchange).with(ROUTING_INPUT_RESERVATION_FAILED);
    }

    @Bean
    public Binding inputReservationFailedRetryReturnBinding(Queue inputReservationFailedQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(inputReservationFailedQueue).to(mainExchange).with(ROUTING_INPUT_RESERVATION_FAILED_RETRY_RETURN);
    }

    @Bean
    public Binding inputReservedBinding(Queue inputReservedQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(inputReservedQueue).to(mainExchange).with(ROUTING_INPUT_RESERVED);
    }

    @Bean
    public Binding inputReservedRetryReturnBinding(Queue inputReservedQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(inputReservedQueue).to(mainExchange).with(ROUTING_INPUT_RESERVED_RETRY_RETURN);
    }

    @Bean
    public Binding retry10sBinding(Queue retry10sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry10sQueue).to(dlxExchange).with("telemetry.alerts.retry.10s");
    }

    @Bean
    public Binding retry10sPestBinding(Queue retry10sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry10sQueue).to(dlxExchange).with("pest.detected.retry.10s");
    }

    @Bean
    public Binding retry10sMachineryBinding(Queue retry10sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry10sQueue).to(dlxExchange).with("machinery.maintenance.retry.10s");
    }

    @Bean
    public Binding retry10sActivityBinding(Queue retry10sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry10sQueue).to(dlxExchange).with("activity.completed.retry.10s");
    }

    @Bean
    public Binding retry10sInputReservationFailedBinding(Queue retry10sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry10sQueue).to(dlxExchange).with("supply.input.reservation.failed.retry.10s");
    }

    @Bean
    public Binding retry10sInputReservedBinding(Queue retry10sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry10sQueue).to(dlxExchange).with("supply.input.reserved.retry.10s");
    }

    @Bean
    public Binding retry30sBinding(Queue retry30sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry30sQueue).to(dlxExchange).with("telemetry.alerts.retry.30s");
    }

    @Bean
    public Binding retry30sPestBinding(Queue retry30sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry30sQueue).to(dlxExchange).with("pest.detected.retry.30s");
    }

    @Bean
    public Binding retry30sMachineryBinding(Queue retry30sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry30sQueue).to(dlxExchange).with("machinery.maintenance.retry.30s");
    }

    @Bean
    public Binding retry30sActivityBinding(Queue retry30sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry30sQueue).to(dlxExchange).with("activity.completed.retry.30s");
    }

    @Bean
    public Binding retry30sInputReservationFailedBinding(Queue retry30sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry30sQueue).to(dlxExchange).with("supply.input.reservation.failed.retry.30s");
    }

    @Bean
    public Binding retry30sInputReservedBinding(Queue retry30sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry30sQueue).to(dlxExchange).with("supply.input.reserved.retry.30s");
    }

    @Bean
    public Binding retry90sBinding(Queue retry90sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry90sQueue).to(dlxExchange).with("telemetry.alerts.retry.90s");
    }

    @Bean
    public Binding retry90sPestBinding(Queue retry90sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry90sQueue).to(dlxExchange).with("pest.detected.retry.90s");
    }

    @Bean
    public Binding retry90sMachineryBinding(Queue retry90sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry90sQueue).to(dlxExchange).with("machinery.maintenance.retry.90s");
    }

    @Bean
    public Binding retry90sActivityBinding(Queue retry90sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry90sQueue).to(dlxExchange).with("activity.completed.retry.90s");
    }

    @Bean
    public Binding retry90sInputReservationFailedBinding(Queue retry90sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry90sQueue).to(dlxExchange).with("supply.input.reservation.failed.retry.90s");
    }

    @Bean
    public Binding retry90sInputReservedBinding(Queue retry90sQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(retry90sQueue).to(dlxExchange).with("supply.input.reserved.retry.90s");
    }

    @Bean
    public Binding dlqBinding(Queue dlqQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlqQueue).to(dlxExchange).with("events.dlq");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        template.setReturnsCallback(returned -> {
            org.slf4j.LoggerFactory.getLogger(RabbitMqTopologyConfig.class).error(
                    "AMQP Unroutable Message returned by broker! replyCode={} replyText={} exchange={} routingKey={}",
                    returned.getReplyCode(), returned.getReplyText(), returned.getExchange(), returned.getRoutingKey()
            );
        });
        return template;
    }
}