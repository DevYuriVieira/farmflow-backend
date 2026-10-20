package com.farmflow.service;

import com.farmflow.config.RabbitMqTopologyConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RabbitMqEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, Object payload, String correlationId) {
        String messageId = correlationId != null && !correlationId.isBlank()
                ? correlationId
                : UUID.randomUUID().toString();

        rabbitTemplate.convertAndSend(
                RabbitMqTopologyConfig.MAIN_EXCHANGE,
                routingKey,
                payload,
                message -> {
                    message.getMessageProperties().setMessageId(messageId);
                    message.getMessageProperties().setCorrelationId(messageId);
                    message.getMessageProperties().setHeader("traceparent", messageId);
                    return message;
                }
        );
        log.info("Published event to exchange={} routingKey={} messageId={}",
                RabbitMqTopologyConfig.MAIN_EXCHANGE, routingKey, messageId);
    }
}