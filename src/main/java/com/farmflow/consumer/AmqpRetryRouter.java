package com.farmflow.consumer;

import com.farmflow.config.RabbitMqTopologyConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AmqpRetryRouter {

    private static final Logger log = LoggerFactory.getLogger(AmqpRetryRouter.class);

    public static final String HEADER_RETRY_COUNT = "x-retry-count";
    public static final String HEADER_ORIGINAL_QUEUE = "x-original-queue";
    public static final String HEADER_EXCEPTION_MESSAGE = "x-exception-message";
    public static final String HEADER_FAILED_AT = "x-failed-at";
    public static final String HEADER_FAILURE_TYPE = "x-failure-type";

    public static final String FAILURE_TYPE_POISON = "POISON_MESSAGE";
    public static final String FAILURE_TYPE_RETRY_10S = "RETRY_STAGE_10S";
    public static final String FAILURE_TYPE_RETRY_30S = "RETRY_STAGE_30S";
    public static final String FAILURE_TYPE_RETRY_90S = "RETRY_STAGE_90S";
    public static final String FAILURE_TYPE_EXHAUSTED = "EXHAUSTED_RETRIES";

    private final RabbitTemplate rabbitTemplate;

    public int getRetryCount(Message message) {
        MessageProperties properties = message.getMessageProperties();
        Object countHeader = properties.getHeader(HEADER_RETRY_COUNT);
        if (countHeader instanceof Number number) {
            return number.intValue();
        }
        List<Map<String, ?>> xDeath = properties.getXDeathHeader();
        if (xDeath != null && !xDeath.isEmpty()) {
            long total = 0;
            for (Map<String, ?> death : xDeath) {
                Object count = death.get("count");
                if (count instanceof Number n) {
                    total += n.longValue();
                }
            }
            return (int) total;
        }
        return 0;
    }

    public void routeFailure(Message message, String originalQueue, String flowPrefix, Throwable cause) {
        int currentAttempt = getRetryCount(message);
        int nextAttempt = currentAttempt + 1;
        String errorMessage = cause != null && cause.getMessage() != null ? cause.getMessage() : "Unknown consumer execution error";

        Message preparedMessage = enrichMessage(message, nextAttempt, originalQueue, errorMessage);

        String routingKey;
        String failureType;

        if (currentAttempt == 0) {
            routingKey = flowPrefix + ".retry.10s";
            failureType = FAILURE_TYPE_RETRY_10S;
        } else if (currentAttempt == 1) {
            routingKey = flowPrefix + ".retry.30s";
            failureType = FAILURE_TYPE_RETRY_30S;
        } else if (currentAttempt == 2) {
            routingKey = flowPrefix + ".retry.90s";
            failureType = FAILURE_TYPE_RETRY_90S;
        } else {
            routingKey = "events.dlq";
            failureType = FAILURE_TYPE_EXHAUSTED;
        }

        preparedMessage.getMessageProperties().setHeader(HEADER_FAILURE_TYPE, failureType);

        log.warn("Routing failed message id={} (attempt={}) from queue={} to exchange={} key={} failureType={}",
                message.getMessageProperties().getMessageId(),
                nextAttempt,
                originalQueue,
                RabbitMqTopologyConfig.DLX_EXCHANGE,
                routingKey,
                failureType
        );

        rabbitTemplate.send(
                RabbitMqTopologyConfig.DLX_EXCHANGE,
                routingKey,
                preparedMessage
        );
    }

    public void routePoison(Message message, String originalQueue, String reason) {
        Message preparedMessage = enrichMessage(message, 0, originalQueue, reason);
        preparedMessage.getMessageProperties().setHeader(HEADER_FAILURE_TYPE, FAILURE_TYPE_POISON);

        log.error("Routing poison message id={} from queue={} to DLQ key=events.dlq reason={}",
                message.getMessageProperties().getMessageId(),
                originalQueue,
                reason
        );

        rabbitTemplate.send(
                RabbitMqTopologyConfig.DLX_EXCHANGE,
                "events.dlq",
                preparedMessage
        );
    }

    private Message enrichMessage(Message sourceMessage, int attempt, String originalQueue, String reason) {
        MessageProperties sourceProps = sourceMessage.getMessageProperties();
        MessageProperties targetProps = new MessageProperties();

        if (sourceProps.getHeaders() != null) {
            targetProps.getHeaders().putAll(sourceProps.getHeaders());
        }

        targetProps.setMessageId(sourceProps.getMessageId());
        targetProps.setCorrelationId(sourceProps.getCorrelationId());
        targetProps.setContentType(sourceProps.getContentType());
        targetProps.setContentEncoding(sourceProps.getContentEncoding());

        targetProps.setHeader(HEADER_RETRY_COUNT, attempt);
        targetProps.setHeader(HEADER_ORIGINAL_QUEUE, originalQueue);
        targetProps.setHeader(HEADER_EXCEPTION_MESSAGE, reason);
        targetProps.setHeader(HEADER_FAILED_AT, Instant.now().toString());

        return new Message(sourceMessage.getBody(), targetProps);
    }
}
