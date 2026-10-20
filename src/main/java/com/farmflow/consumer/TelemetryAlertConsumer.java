package com.farmflow.consumer;

import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.service.MessageDeduplicationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class TelemetryAlertConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryAlertConsumer.class);

    private final MessageDeduplicationService deduplicationService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMqTopologyConfig.TELEMETRY_ALERTS_QUEUE)
    public void handleTelemetryAlert(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        if (payload.isBlank() || !payload.startsWith("{")) {
            log.error("Poison message detected in telemetry queue: id={}. Routing to DLQ.", messageId);
            routeToDlq(message, "MALFORMED_JSON_PAYLOAD");
            return;
        }

        boolean shouldProcess = deduplicationService.processIfNew(messageId, "TelemetryAlert");
        if (!shouldProcess) {
            log.info("Telemetry alert duplicate discarded: messageId={}", messageId);
            return;
        }

        log.info("Successfully processed telemetry alert: messageId={} payload={}", messageId, payload);
    }

    private void routeToDlq(Message message, String reason) {
        rabbitTemplate.send(
                RabbitMqTopologyConfig.DLX_EXCHANGE,
                "events.dlq",
                message
        );
    }
}