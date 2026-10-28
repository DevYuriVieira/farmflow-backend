package com.farmflow.service;

import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.consumer.AmqpRetryRouter;
import com.farmflow.dto.AdminDtos.DlqReplayRequest;
import com.farmflow.dto.AdminDtos.DlqReplayResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminDlqService {

    private static final Logger log = LoggerFactory.getLogger(AdminDlqService.class);

    private final RabbitTemplate rabbitTemplate;

    public DlqReplayResponse replayMessages(DlqReplayRequest request) {
        int limit = (request != null && request.maxMessages() != null && request.maxMessages() > 0)
                ? Math.min(request.maxMessages(), 500)
                : 50;

        String targetOverride = (request != null && request.targetQueue() != null && !request.targetQueue().isBlank())
                ? request.targetQueue().trim()
                : null;

        List<String> replayedIds = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            Message message = rabbitTemplate.receive(RabbitMqTopologyConfig.DLQ_QUEUE, 500);
            if (message == null) {
                break;
            }

            MessageProperties props = message.getMessageProperties();
            String originalQueue = (String) props.getHeader(AmqpRetryRouter.HEADER_ORIGINAL_QUEUE);
            String destinationQueue = targetOverride != null ? targetOverride : originalQueue;

            if (destinationQueue == null || destinationQueue.isBlank()) {
                log.warn("Cannot replay message from DLQ without target queue or original queue header. Leaving unrouted.");
                continue;
            }

            props.setHeader("x-replayed-at", Instant.now().toString());
            props.setHeader(AmqpRetryRouter.HEADER_RETRY_COUNT, 0);
            props.getHeaders().remove(AmqpRetryRouter.HEADER_FAILURE_TYPE);
            props.getHeaders().remove(AmqpRetryRouter.HEADER_EXCEPTION_MESSAGE);

            rabbitTemplate.send("", destinationQueue, message);

            String messageId = props.getMessageId() != null
                    ? props.getMessageId()
                    : "replayed-" + UUID.randomUUID();
            replayedIds.add(messageId);
            log.info("Replayed message id={} from DLQ to queue={}", messageId, destinationQueue);
        }

        return new DlqReplayResponse(
                replayedIds.size(),
                replayedIds,
                Instant.now(),
                "COMPLETED"
        );
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.amqp.rabbit.core.RabbitAdmin rabbitAdmin;

    public long getDlqMessageCount() {
        if (rabbitAdmin == null) {
            return 0L;
        }
        try {
            java.util.Properties props = rabbitAdmin.getQueueProperties(RabbitMqTopologyConfig.DLQ_QUEUE);
            if (props != null) {
                Object count = props.get(org.springframework.amqp.rabbit.core.RabbitAdmin.QUEUE_MESSAGE_COUNT);
                if (count instanceof Number number) {
                    return number.longValue();
                }
            }
        } catch (Exception e) {
            log.trace("Suppressed failure fetching DLQ message count: {}", e.getMessage());
        }
        return 0L;
    }
}
