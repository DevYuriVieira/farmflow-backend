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

    private void applyBusinessEffect(String payload, String messageId) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataNode = root.hasNonNull("data") ? root.get("data") : root;
            JsonNode activityNode = dataNode.hasNonNull("activity") ? dataNode.get("activity") : dataNode;

            String machineryId = dataNode.hasNonNull("machineryId") ? dataNode.get("machineryId").asText() :
                    (activityNode.hasNonNull("machineryId") ? activityNode.get("machineryId").asText() : null);

            double hoursUsed = dataNode.hasNonNull("hoursUsed") ? dataNode.get("hoursUsed").asDouble() :
                    (activityNode.hasNonNull("hoursUsed") ? activityNode.get("hoursUsed").asDouble() : 0.0);

            if (machineryId == null || machineryId.isBlank() || hoursUsed <= 0.0) {
                log.debug("No machinery hours to increment for activity event messageId={}", messageId);
                return;
            }

            machineryRepository.findById(machineryId).ifPresent(machinery -> {
                double currentHours = machinery.getOperatingHours() != null ? machinery.getOperatingHours() : 0.0;
                double newHours = currentHours + hoursUsed;
                machineryRepository.updateOperatingHoursMonotonically(machineryId, newHours, Instant.now());
                log.info("Machinery {} operating hours updated monotonically from {} to {} for messageId={}",
                        machineryId, currentHours, newHours, messageId);
            });
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply machinery activity completed business effect for message " + messageId, e);
        }
    }
}
