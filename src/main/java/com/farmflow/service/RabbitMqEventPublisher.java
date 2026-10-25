package com.farmflow.service;

import com.farmflow.config.RabbitMqTopologyConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RabbitMqEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqEventPublisher.class);
    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, Object payload, String messageId, String correlationId, String traceParent) {
        String actualMessageId = (messageId != null && !messageId.isBlank())
                ? messageId
                : UUID.randomUUID().toString();

        String actualCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : actualMessageId;

        CorrelationData correlationData = new CorrelationData(actualMessageId);

        rabbitTemplate.convertAndSend(
                RabbitMqTopologyConfig.MAIN_EXCHANGE,
                routingKey,
                payload,
                message -> {
                    message.getMessageProperties().setMessageId(actualMessageId);
                    message.getMessageProperties().setCorrelationId(actualCorrelationId);
                    if (traceParent != null && !traceParent.isBlank()) {
                        message.getMessageProperties().setHeader("traceparent", traceParent);
                    }
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                },
                correlationData
        );

        verifyPublisherConfirm(correlationData, actualMessageId);

        log.info("Published event to exchange={} routingKey={} messageId={} correlationId={}",
                RabbitMqTopologyConfig.MAIN_EXCHANGE, routingKey, actualMessageId, actualCorrelationId);
    }

    public void publish(String routingKey, Object payload, String correlationId) {
        publish(routingKey, payload, null, correlationId, null);
    }

    private void verifyPublisherConfirm(CorrelationData correlationData, String messageId) {
        if (correlationData.getFuture() == null) {
            return;
        }
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (confirm != null && !confirm.ack()) {
                throw new AmqpException("Broker rejected message publication (NACK) for messageId=" + messageId
                        + ", reason=" + confirm.reason());
            }
            org.springframework.amqp.core.ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                log.error("Message was returned by broker as unroutable: messageId={}, replyCode={}, replyText={}, exchange={}, routingKey={}",
                        messageId, returned.getReplyCode(), returned.getReplyText(), returned.getExchange(), returned.getRoutingKey());
                throw new AmqpException("Message was unroutable (basic.return): messageId=" + messageId
                        + ", replyCode=" + returned.getReplyCode() + ", replyText=" + returned.getReplyText());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while awaiting publisher confirm for messageId=" + messageId, e);
        } catch (AmqpException e) {
            throw e;
        } catch (Exception e) {
            throw new AmqpException("Publisher confirm timeout or failure for messageId=" + messageId, e);
        }
    }
}