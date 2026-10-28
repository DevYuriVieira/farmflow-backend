package com.farmflow.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.repository.MachineryRepository;
import com.farmflow.service.MessageDeduplicationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ActivityCompletedMachineryConsumer {

    public static final String CONSUMER_NAME = "ActivityCompletedMachineryConsumer";
    private static final Logger log = LoggerFactory.getLogger(ActivityCompletedMachineryConsumer.class);

    private final MessageDeduplicationService deduplicationService;
    private final MachineryRepository machineryRepository;
    private final ObjectMapper objectMapper;
    private final FarmFlowMetrics farmFlowMetrics;
    private final AmqpRetryRouter retryRouter;

    @RabbitListener(queues = RabbitMqTopologyConfig.MACHINERY_ACTIVITY_COMPLETED_QUEUE)
    public void handleActivityCompleted(Message message) {
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
                log.error("Poison message detected in machinery activity completed queue: missing mandatory messageId. Routing to DLQ.");
                retryRouter.routePoison(message, RabbitMqTopologyConfig.MACHINERY_ACTIVITY_COMPLETED_QUEUE, "MISSING_MESSAGE_ID");
                farmFlowMetrics.incrementConsumerFailed("MachineryActivityCompleted");
                return;
            }

            if (payload.isBlank() || !payload.startsWith("{")) {
                log.error("Poison message detected in machinery activity completed queue: id={}. Routing to DLQ.", messageId);
                retryRouter.routePoison(message, RabbitMqTopologyConfig.MACHINERY_ACTIVITY_COMPLETED_QUEUE, "MALFORMED_JSON_PAYLOAD");
                farmFlowMetrics.incrementConsumerFailed("MachineryActivityCompleted");
                return;
            }

            String tenantId = extractTenantId(payload);
            if (tenantId != null && !tenantId.isBlank()) {
                com.farmflow.config.TenantContext.setTenantId(tenantId);
            }

            try {
                boolean processed = deduplicationService.executeIfNew(
                        CONSUMER_NAME,
                        messageId,
                        "MachineryActivityCompleted",
                        () -> applyBusinessEffect(payload, messageId)
                );

                if (!processed) {
                    farmFlowMetrics.incrementConsumerDuplicate("MachineryActivityCompleted");
                    log.info("Machinery activity completed event duplicate discarded: messageId={}", messageId);
                    return;
                }

                farmFlowMetrics.incrementConsumerProcessed("MachineryActivityCompleted");
                log.info("Successfully processed machinery activity completed event: messageId={}", messageId);
            } finally {
                com.farmflow.config.TenantContext.clear();
            }
        } catch (Exception e) {
            log.error("Error processing machinery activity completed message {}: {}", messageId, e.getMessage(), e);
            farmFlowMetrics.incrementConsumerFailed("MachineryActivityCompleted");
            if (e instanceof IllegalArgumentException || e.getCause() instanceof IllegalArgumentException) {
                retryRouter.routePoison(message, RabbitMqTopologyConfig.MACHINERY_ACTIVITY_COMPLETED_QUEUE, e.getMessage());
            } else {
                retryRouter.routeFailure(message, RabbitMqTopologyConfig.MACHINERY_ACTIVITY_COMPLETED_QUEUE, "machinery.activity.completed", e);
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_TRACEPARENT_KEY);
            MDC.remove(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        }
    }

    private String extractTenantId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataNode = root.hasNonNull("data") ? root.get("data") : root;
            if (dataNode.hasNonNull("farmId")) {
                return dataNode.get("farmId").asText();
            }
            if (root.hasNonNull("tenantId")) {
                return root.get("tenantId").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void applyBusinessEffect(String payload, String messageId) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataNode = root.hasNonNull("data") ? root.get("data") : root;
            JsonNode activityNode = dataNode.hasNonNull("activity") ? dataNode.get("activity") : dataNode;

            String farmId = dataNode.hasNonNull("farmId") ? dataNode.get("farmId").asText() :
                    (activityNode.hasNonNull("farmId") ? activityNode.get("farmId").asText() :
                            (root.hasNonNull("tenantId") ? root.get("tenantId").asText() : null));

            String machineryId = dataNode.hasNonNull("machineryId") ? dataNode.get("machineryId").asText() :
                    (activityNode.hasNonNull("machineryId") ? activityNode.get("machineryId").asText() : null);

            JsonNode hoursNode = dataNode.hasNonNull("hoursUsed") ? dataNode.get("hoursUsed") :
                    (activityNode.hasNonNull("hoursUsed") ? activityNode.get("hoursUsed") : null);

            if (machineryId == null || machineryId.isBlank()) {
                log.debug("No machinery assigned for activity completed event messageId={}", messageId);
                return;
            }

            if (farmId == null || farmId.isBlank()) {
                throw new IllegalArgumentException("Missing mandatory farmId in activity completed payload: messageId=" + messageId);
            }

            if (hoursNode == null || !hoursNode.isNumber()) {
                throw new IllegalArgumentException("Non-numeric or missing hoursUsed in activity completed payload: messageId=" + messageId);
            }

            double hoursUsed = hoursNode.asDouble();
            if (Double.isNaN(hoursUsed) || Double.isInfinite(hoursUsed) || hoursUsed <= 0.0 || hoursUsed > 24.0) {
                throw new IllegalArgumentException("Invalid hoursUsed value " + hoursUsed + " in messageId=" + messageId);
            }

            int rowsUpdated = machineryRepository.incrementOperatingHoursAtomic(machineryId, farmId, hoursUsed, Instant.now());
            if (rowsUpdated == 0) {
                throw new IllegalArgumentException("Machinery not found or tenant mismatch: id=" + machineryId + ", farmId=" + farmId);
            }

            log.info("Machinery {} operating hours atomically incremented by {} for messageId={}",
                    machineryId, hoursUsed, messageId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply machinery activity completed business effect for message " + messageId, e);
        }
    }
}
