package com.farmflow.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.event.cloudevents.InputReservationFailedData;
import com.farmflow.repository.InventoryRepository;
import com.farmflow.service.MessageDeduplicationService;
import com.farmflow.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActivityCompletedConsumer {

    public static final String CONSUMER_NAME = "ActivityCompletedConsumer";
    private static final Logger log = LoggerFactory.getLogger(ActivityCompletedConsumer.class);

    private final MessageDeduplicationService deduplicationService;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;
    private final FarmFlowMetrics farmFlowMetrics;
    private final AmqpRetryRouter retryRouter;
    private final OutboxService outboxService;

    @RabbitListener(queues = RabbitMqTopologyConfig.ACTIVITY_COMPLETED_QUEUE)
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
                log.error("Poison message detected in activity completed queue: missing mandatory messageId. Routing to DLQ.");
                retryRouter.routePoison(message, RabbitMqTopologyConfig.ACTIVITY_COMPLETED_QUEUE, "MISSING_MESSAGE_ID");
                farmFlowMetrics.incrementConsumerFailed("ActivityCompleted");
                return;
            }

            if (payload.isBlank() || !payload.startsWith("{")) {
                log.error("Poison message detected in activity completed queue: id={}. Routing to DLQ.", messageId);
                retryRouter.routePoison(message, RabbitMqTopologyConfig.ACTIVITY_COMPLETED_QUEUE, "MALFORMED_JSON_PAYLOAD");
                farmFlowMetrics.incrementConsumerFailed("ActivityCompleted");
                return;
            }

            boolean processed = deduplicationService.executeIfNew(
                    CONSUMER_NAME,
                    messageId,
                    "ActivityCompleted",
                    () -> applyBusinessEffect(payload, messageId)
            );

            if (!processed) {
                farmFlowMetrics.incrementConsumerDuplicate("ActivityCompleted");
                log.info("Activity completed event duplicate discarded: messageId={}", messageId);
                return;
            }

            farmFlowMetrics.incrementConsumerProcessed("ActivityCompleted");
            log.info("Successfully processed activity completed event: messageId={}", messageId);
        } catch (Exception e) {
            log.error("Error processing activity completed message {}: {}", messageId, e.getMessage(), e);
            farmFlowMetrics.incrementConsumerFailed("ActivityCompleted");
            if (e instanceof IllegalArgumentException || e.getCause() instanceof IllegalArgumentException) {
                retryRouter.routePoison(message, RabbitMqTopologyConfig.ACTIVITY_COMPLETED_QUEUE, e.getMessage());
            } else {
                retryRouter.routeFailure(message, RabbitMqTopologyConfig.ACTIVITY_COMPLETED_QUEUE, "activity.completed", e);
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_TRACEPARENT_KEY);
            MDC.remove(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        }
    }

    private void applyBusinessEffect(String payload, String messageId) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataNode = root.hasNonNull("data") ? root.get("data") : root;
            JsonNode activityNode = dataNode.hasNonNull("activity") ? dataNode.get("activity") : dataNode;

            String activityId = activityNode.hasNonNull("activityId") ? activityNode.get("activityId").asText() :
                    (activityNode.hasNonNull("id") ? activityNode.get("id").asText() : null);

            String farmId = activityNode.hasNonNull("farmId") ? activityNode.get("farmId").asText() :
                    (root.hasNonNull("tenantId") ? root.get("tenantId").asText() : null);

            if (farmId == null || farmId.isBlank()) {
                throw new IllegalArgumentException("Missing required farmId in activity completed payload: " + payload);
            }
            if (activityId == null || activityId.isBlank()) {
                throw new IllegalArgumentException("Missing required activityId in activity completed payload: " + payload);
            }

            JsonNode inputsNode = dataNode.hasNonNull("inputsUsed") ? dataNode.get("inputsUsed") :
                    (root.hasNonNull("inputsUsed") ? root.get("inputsUsed") : null);

            if (inputsNode != null && inputsNode.isArray()) {
                for (JsonNode itemNode : inputsNode) {
                    String itemId = itemNode.hasNonNull("itemId") ? itemNode.get("itemId").asText() : null;
                    double quantity = itemNode.hasNonNull("quantity") ? itemNode.get("quantity").asDouble() : 0.0;
                    if (itemId != null && !itemId.isBlank() && quantity > 0) {
                        int updated = inventoryRepository.deductAtomically(itemId, quantity, Instant.now());
                        if (updated == 0) {
                            log.warn("Deduction skipped or insufficient inventory for itemId={} in activityId={}", itemId, activityId);
                            emitInputReservationFailed(farmId, activityId, itemId, quantity, "INSUFFICIENT_STOCK");
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply activity completed business effect for message " + messageId, e);
        }
    }

    private void emitInputReservationFailed(String farmId, String activityId, String itemId, double quantity, String reason) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        InputReservationFailedData data = new InputReservationFailedData(
                UUID.randomUUID().toString(),
                farmId,
                activityId,
                itemId,
                quantity,
                reason,
                Instant.now()
        );

        CloudEvent<InputReservationFailedData> cloudEvent = CloudEvent.of(
                "https://farmflow.com/supply-service",
                "com.farmflow.supply.input.reservation.failed.v1",
                farmId,
                correlationId,
                data
        );
        outboxService.recordCloudEvent(cloudEvent);
    }
}
