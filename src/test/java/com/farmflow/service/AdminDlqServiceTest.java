package com.farmflow.service;

import com.farmflow.config.RabbitMqTopologyConfig;
import com.farmflow.consumer.AmqpRetryRouter;
import com.farmflow.dto.AdminDtos.DlqReplayRequest;
import com.farmflow.dto.AdminDtos.DlqReplayResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminDlqServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private AdminDlqService adminDlqService;

    @Test
    @DisplayName("replayMessages: retorna 0 quando DLQ está vazia")
    void shouldReturnZeroWhenDlqIsEmpty() {
        when(rabbitTemplate.receive(RabbitMqTopologyConfig.DLQ_QUEUE, 500)).thenReturn(null);

        DlqReplayResponse response = adminDlqService.replayMessages(new DlqReplayRequest(10, null));

        assertNotNull(response);
        assertEquals(0, response.replayedCount());
        assertTrue(response.replayedMessageIds().isEmpty());
        assertEquals("COMPLETED", response.status());
    }

    @Test
    @DisplayName("replayMessages: repassa mensagens da DLQ para fila original limpando cabeçalhos de erro")
    void shouldReplayMessagesBackToOriginalQueue() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-dlq-1");
        props.setHeader(AmqpRetryRouter.HEADER_ORIGINAL_QUEUE, "farmflow.activities.completed");
        props.setHeader(AmqpRetryRouter.HEADER_RETRY_COUNT, 3);
        props.setHeader(AmqpRetryRouter.HEADER_FAILURE_TYPE, "EXHAUSTED_RETRIES");
        props.setHeader(AmqpRetryRouter.HEADER_EXCEPTION_MESSAGE, "Connection refused");

        Message msg = new Message("{\"event\":\"test\"}".getBytes(StandardCharsets.UTF_8), props);

        when(rabbitTemplate.receive(RabbitMqTopologyConfig.DLQ_QUEUE, 500))
                .thenReturn(msg)
                .thenReturn(null);

        DlqReplayResponse response = adminDlqService.replayMessages(new DlqReplayRequest(5, null));

        assertEquals(1, response.replayedCount());
        assertEquals("msg-dlq-1", response.replayedMessageIds().get(0));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq("farmflow.activities.completed"), messageCaptor.capture());

        Message replayed = messageCaptor.getValue();
        assertEquals(Integer.valueOf(0), replayed.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_RETRY_COUNT));
        assertNull(replayed.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_FAILURE_TYPE));
        assertNull(replayed.getMessageProperties().getHeader(AmqpRetryRouter.HEADER_EXCEPTION_MESSAGE));
        assertNotNull(replayed.getMessageProperties().getHeader("x-replayed-at"));
    }

    @Test
    @DisplayName("replayMessages: permite sobrescrever fila de destino via targetQueue")
    void shouldReplayMessagesToTargetQueueOverride() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-dlq-2");
        props.setHeader(AmqpRetryRouter.HEADER_ORIGINAL_QUEUE, "farmflow.activities.completed");

        Message msg = new Message("{}".getBytes(StandardCharsets.UTF_8), props);

        when(rabbitTemplate.receive(RabbitMqTopologyConfig.DLQ_QUEUE, 500))
                .thenReturn(msg)
                .thenReturn(null);

        DlqReplayResponse response = adminDlqService.replayMessages(new DlqReplayRequest(1, "manual.staging.queue"));

        assertEquals(1, response.replayedCount());
        verify(rabbitTemplate).send(eq(""), eq("manual.staging.queue"), any(Message.class));
    }
}
