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

@Component
@RequiredArgsConstructor
public class InputReservationFailedConsumer {

    public static final String CONSUMER_NAME = "InputReservationFailedConsumer";
    private static final Logger log = LoggerFactory.getLogger(InputReservationFailedConsumer.class);

    private final MessageDeduplicationService deduplicationService;
    private final ActivityRepository activityRepository;
    private final com.farmflow.repository.SagaAuditLogRepository sagaAuditLogRepository;
    private final com.farmflow.service.OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final FarmFlowMetrics farmFlowMetrics;
    private final AmqpRetryRouter retryRouter;

    @RabbitListener(queues = RabbitMqTopologyConfig.INPUT_RESERVATION_FAILED_QUEUE)
    public void handleInputReservationFailed(Message message) {
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
                log.error("Poison message detected in input reservation failed queue: missing mandatory messageId. Routing to DLQ.");
                retryRouter.routePoison(message, RabbitMqTopologyConfig.INPUT_RESERVATION_FAILED_QUEUE, "MISSING_MESSAGE_ID");
                farmFlowMetrics.incrementConsumerFailed("InputReservationFailed");
                return;
            }

            if (payload.isBlank() || !payload.startsWith("{")) {
                log.error("Poison message detected in input reservation failed queue: id={}. Routing to DLQ.", messageId);
                retryRouter.routePoison(message, RabbitMqTopologyConfig.INPUT_RESERVATION_FAILED_QUEUE, "MALFORMED_JSON_PAYLOAD");
                farmFlowMetrics.incrementConsumerFailed("InputReservationFailed");
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
                        "InputReservationFailed",
                        () -> applyBusinessEffect(payload, messageId)
                );

                if (!processed) {
                    farmFlowMetrics.incrementConsumerDuplicate("InputReservationFailed");
                    log.info("Input reservation failed event duplicate discarded: messageId={}", messageId);
                    return;
                }

                farmFlowMetrics.incrementConsumerProcessed("InputReservationFailed");
                log.info("Successfully processed input reservation failed compensation: messageId={}", messageId);
            } finally {
                com.farmflow.config.TenantContext.clear();
            }
        } catch (Exception e) {
            log.error("Error processing input reservation failed message {}: {}", messageId, e.getMessage(), e);
            farmFlowMetrics.incrementConsumerFailed("InputReservationFailed");
            if (e instanceof IllegalArgumentException || e.getCause() instanceof IllegalArgumentException) {
                retryRouter.routePoison(message, RabbitMqTopologyConfig.INPUT_RESERVATION_FAILED_QUEUE, e.getMessage());
            } else {
                retryRouter.routeFailure(message, RabbitMqTopologyConfig.INPUT_RESERVATION_FAILED_QUEUE, "supply.input.reservation.failed", e);
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

            String activityId = dataNode.hasNonNull("activityId") ? dataNode.get("activityId").asText() : null;
            String farmId = dataNode.hasNonNull("farmId") ? dataNode.get("farmId").asText() :
                    (root.hasNonNull("tenantId") ? root.get("tenantId").asText() : null);
            String itemId = dataNode.hasNonNull("itemId") ? dataNode.get("itemId").asText() : "unknown-item";
            String reason = dataNode.hasNonNull("reason") ? dataNode.get("reason").asText() : "Stock deficit for item " + itemId;

            if (farmId == null || farmId.isBlank()) {
                throw new IllegalArgumentException("Missing required farmId in input reservation failed payload: " + payload);
            }
            if (activityId == null || activityId.isBlank()) {
                throw new IllegalArgumentException("Missing required activityId in input reservation failed payload: " + payload);
            }

            com.farmflow.entity.FarmActivity activity = activityRepository.findByIdAndFarmId(activityId, farmId)
                    .orElseThrow(() -> new IllegalArgumentException("Activity not found or tenant mismatch: id=" + activityId + ", farmId=" + farmId));

            activity.compensateInputReservation(reason);
            activityRepository.save(activity);

            com.farmflow.entity.SagaAuditLog auditLog = com.farmflow.entity.SagaAuditLog.builder()
                    .farmId(farmId)
                    .activityId(activityId)
                    .sagaType("ACTIVITY_INPUT_RESERVATION")
                    .transitionFrom(com.farmflow.enums.ActivityStatus.PENDING_CONFIRMATION.name())
                    .transitionTo(com.farmflow.enums.ActivityStatus.IN_PROGRESS.name())
                    .reason(reason)
                    .correlationId(messageId)
                    .executedAt(java.time.Instant.now())
                    .build();
            sagaAuditLogRepository.save(auditLog);

            com.farmflow.event.cloudevents.CloudEvent<java.util.Map<String, Object>> event = com.farmflow.event.cloudevents.CloudEvent.of(
                    "https://farmflow.com/core-service",
                    "com.farmflow.activity.compensated.v1",
                    farmId,
                    messageId,
                    java.util.Map.of(
                            "activityId", activityId,
                            "farmId", farmId,
                            "itemId", itemId,
                            "reason", reason,
                            "status", com.farmflow.enums.ActivityStatus.IN_PROGRESS.name()
                    )
            );
            outboxService.recordCloudEvent(event);

            log.info("Activity {} successfully compensated back to IN_PROGRESS due to reason: {}", activityId, reason);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply input reservation failed compensation for message " + messageId, e);
        }
    }
}
