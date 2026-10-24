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
public class PestScoutingConsumer {

    public static final String CONSUMER_NAME = "PestScoutingConsumer";
    private static final Logger log = LoggerFactory.getLogger(PestScoutingConsumer.class);

    private final MessageDeduplicationService deduplicationService;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;
    private final FarmFlowMetrics farmFlowMetrics;
    private final AmqpRetryRouter retryRouter;

    @RabbitListener(queues = RabbitMqTopologyConfig.PEST_DETECTED_QUEUE)
    public void handlePestDetected(Message message) {
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
                log.error("Poison message detected in pest detected queue: missing mandatory messageId. Routing to DLQ.");
                retryRouter.routePoison(message, RabbitMqTopologyConfig.PEST_DETECTED_QUEUE, "MISSING_MESSAGE_ID");
                farmFlowMetrics.incrementConsumerFailed("PestDetected");
                return;
            }

            if (payload.isBlank() || !payload.startsWith("{")) {
                log.error("Poison message detected in pest detected queue: id={}. Routing to DLQ.", messageId);
                retryRouter.routePoison(message, RabbitMqTopologyConfig.PEST_DETECTED_QUEUE, "MALFORMED_JSON_PAYLOAD");
                farmFlowMetrics.incrementConsumerFailed("PestDetected");
                return;
            }

            boolean processed = deduplicationService.executeIfNew(
                    CONSUMER_NAME,
                    messageId,
                    "PestDetected",
                    () -> applyBusinessEffect(payload, messageId)
            );

            if (!processed) {
                farmFlowMetrics.incrementConsumerDuplicate("PestDetected");
                log.info("Pest detected event duplicate discarded: messageId={}", messageId);
                return;
            }

            farmFlowMetrics.incrementConsumerProcessed("PestDetected");
            log.info("Successfully processed pest detected alert: messageId={} payload={}", messageId, payload);
        } catch (Exception e) {
            log.error("Error processing pest detected message {}: {}", messageId, e.getMessage(), e);
            farmFlowMetrics.incrementConsumerFailed("PestDetected");
            if (e instanceof IllegalArgumentException || e.getCause() instanceof IllegalArgumentException) {
                retryRouter.routePoison(message, RabbitMqTopologyConfig.PEST_DETECTED_QUEUE, e.getMessage());
            } else {
                retryRouter.routeFailure(message, RabbitMqTopologyConfig.PEST_DETECTED_QUEUE, "pest.detected", e);
            }
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_TRACEPARENT_KEY);
            MDC.remove(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        }
    }

    private void applyBusinessEffect(String payload, String messageId) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode recordNode = root.has("record") ? root.get("record") : root;

            String farmId = recordNode.hasNonNull("farmId") ? recordNode.get("farmId").asText() :
                    (root.hasNonNull("tenantId") ? root.get("tenantId").asText() : null);

            if (farmId == null || farmId.isBlank()) {
                throw new IllegalArgumentException("Missing required farmId in pest detected payload: " + payload);
            }

            String plantationId = recordNode.hasNonNull("plantationId")
                    ? recordNode.get("plantationId").asText()
                    : (recordNode.hasNonNull("field") ? recordNode.get("field").asText() : "field-default");
            String pestName = recordNode.hasNonNull("pestName")
                    ? recordNode.get("pestName").asText()
                    : (recordNode.hasNonNull("pest") ? recordNode.get("pest").asText() : "Pest Alert");

            FarmActivity activity = FarmActivity.builder()
                    .farmId(farmId)
                    .plantationId(plantationId)
                    .title("Pest Alert Inspection: " + pestName)
                    .type("scouting")
                    .priority("high")
                    .date(LocalDate.now().toString())
                    .notes("Auto-generated by " + CONSUMER_NAME + " for message " + messageId)
                    .completed(false)
                    .build();

            activityRepository.save(activity);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply pest scouting business effect for message " + messageId, e);
        }
    }
}