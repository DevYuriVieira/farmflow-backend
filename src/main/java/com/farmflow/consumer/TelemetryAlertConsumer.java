package com.farmflow.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.entity.FarmActivity;
import com.farmflow.repository.ActivityRepository;
import com.farmflow.service.MessageDeduplicationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class TelemetryAlertConsumer {

    public static final String CONSUMER_NAME = "TelemetryAlertConsumer";
    private static final Logger log = LoggerFactory.getLogger(TelemetryAlertConsumer.class);

    private final MessageDeduplicationService deduplicationService;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;
    private final FarmFlowMetrics farmFlowMetrics;
    private final AmqpRetryRouter retryRouter;

    @RabbitListener(queues = RabbitMqTopologyConfig.TELEMETRY_ALERTS_QUEUE)
    public void handleTelemetryAlert(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        String traceParent = (String) message.getMessageProperties().getHeader(CorrelationIdFilter.TRACEPARENT_HEADER);
        if (traceParent != null && !traceParent.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_TRACEPARENT_KEY, traceParent);
        }
        String correlationId = message.getMessageProperties().getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID_KEY, correlationId);
        }

        try {
            if (messageId == null || messageId.isBlank()) {
                log.error("Poison message detected in telemetry queue: missing mandatory messageId. Routing to DLQ.");
                retryRouter.routePoison(message, RabbitMqTopologyConfig.TELEMETRY_ALERTS_QUEUE, "MISSING_MESSAGE_ID");
                farmFlowMetrics.incrementConsumerFailed("TelemetryAlert");
                return;
            }

            if (payload.isBlank() || !payload.startsWith("{")) {
                log.error("Poison message detected in telemetry queue: id={}. Routing to DLQ.", messageId);
                retryRouter.routePoison(message, RabbitMqTopologyConfig.TELEMETRY_ALERTS_QUEUE, "MALFORMED_JSON_PAYLOAD");
                farmFlowMetrics.incrementConsumerFailed("TelemetryAlert");
                return;
            }

            boolean processed = deduplicationService.executeIfNew(
                    CONSUMER_NAME,
                    messageId,
                    "TelemetryAlert",
                    () -> applyBusinessEffect(payload, messageId)
            );

            if (!processed) {
                farmFlowMetrics.incrementConsumerDuplicate("TelemetryAlert");
                log.info("Telemetry alert duplicate discarded: messageId={}", messageId);
                return;
            }

            farmFlowMetrics.incrementConsumerProcessed("TelemetryAlert");
            log.info("Successfully processed telemetry alert: messageId={} payload={}", messageId, payload);
        } catch (Exception e) {
            log.error("Error processing telemetry alert message {}: {}", messageId, e.getMessage(), e);
            farmFlowMetrics.incrementConsumerFailed("TelemetryAlert");
            if (e instanceof IllegalArgumentException || e.getCause() instanceof IllegalArgumentException) {
                retryRouter.routePoison(message, RabbitMqTopologyConfig.TELEMETRY_ALERTS_QUEUE, e.getMessage());
            } else {
                retryRouter.routeFailure(message, RabbitMqTopologyConfig.TELEMETRY_ALERTS_QUEUE, "telemetry.alerts", e);
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_TRACEPARENT_KEY);
            MDC.remove(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        }
    }

    private void applyBusinessEffect(String payload, String messageId) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode readingNode = root.has("reading")
                    ? root.get("reading")
                    : (root.has("data") ? root.get("data") : root);

            String sensorId = readingNode.hasNonNull("sensorId") ? readingNode.get("sensorId").asText() : "unknown-sensor";
            String farmId = readingNode.hasNonNull("farmId")
                    ? readingNode.get("farmId").asText()
                    : (root.hasNonNull("tenantId")
                    ? root.get("tenantId").asText()
                    : (root.hasNonNull("tenantid") ? root.get("tenantid").asText() : null));
            String plantationId = readingNode.hasNonNull("plantationId") ? readingNode.get("plantationId").asText() : "unassigned";

            if (farmId == null || farmId.isBlank()) {
                throw new IllegalArgumentException("Missing required farmId in telemetry alert payload: " + payload);
            }

            FarmActivity activity = FarmActivity.builder()
                    .farmId(farmId)
                    .plantationId(plantationId)
                    .title("Telemetry Alert Inspection: " + sensorId)
                    .type("inspection")
                    .priority("high")
                    .date(LocalDate.now().toString())
                    .notes("Auto-generated by " + CONSUMER_NAME + " for message " + messageId)
                    .completed(false)
                    .build();

            activityRepository.save(activity);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply telemetry alert business effect for message " + messageId, e);
        }
    }
}