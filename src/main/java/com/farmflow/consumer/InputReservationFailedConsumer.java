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

    private void applyBusinessEffect(String payload, String messageId) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataNode = root.hasNonNull("data") ? root.get("data") : root;

            String activityId = dataNode.hasNonNull("activityId") ? dataNode.get("activityId").asText() : null;
            String farmId = dataNode.hasNonNull("farmId") ? dataNode.get("farmId").asText() :
                    (root.hasNonNull("tenantId") ? root.get("tenantId").asText() : null);
            String itemId = dataNode.hasNonNull("itemId") ? dataNode.get("itemId").asText() : "unknown-item";

            if (farmId == null || farmId.isBlank()) {
                throw new IllegalArgumentException("Missing required farmId in input reservation failed payload: " + payload);
            }
            if (activityId == null || activityId.isBlank()) {
                throw new IllegalArgumentException("Missing required activityId in input reservation failed payload: " + payload);
            }

            activityRepository.findById(activityId).ifPresent(activity -> {
                activity.reopen();
                String currentNotes = activity.getNotes() != null ? activity.getNotes() : "";
                String compensationNote = "[COMPENSATED: Insufficient inventory for item " + itemId + "]";
                String updatedNotes = currentNotes.isBlank() ? compensationNote : currentNotes + " " + compensationNote;
                activity.updateDetails(
                        activity.getTitle(),
                        activity.getActivityType(),
                        activity.getDate(),
                        activity.getActivityPriority(),
                        updatedNotes,
                        activity.getCostEstimate()
                );
                activityRepository.save(activity);
                log.info("Activity {} successfully compensated and reopened due to stock deficit for itemId={}", activityId, itemId);
            });
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply input reservation failed compensation for message " + messageId, e);
        }
    }
}
