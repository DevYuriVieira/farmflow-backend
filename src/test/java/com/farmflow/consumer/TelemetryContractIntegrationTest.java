package com.farmflow.consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflow.dto.TelemetryDtos.CreateTelemetryRequest;
import com.farmflow.entity.FarmActivity;
import com.farmflow.entity.OutboxMessage;
import com.farmflow.entity.TelemetryReading;
import com.farmflow.enums.TelemetryType;
import com.farmflow.repository.ActivityRepository;
import com.farmflow.repository.OutboxRepository;
import com.farmflow.repository.UserRepository;
import com.farmflow.service.MessageDeduplicationService;
import com.farmflow.service.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
class TelemetryContractIntegrationTest {

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private TelemetryAlertConsumer telemetryAlertConsumer;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private MessageDeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        when(deduplicationService.executeIfNew(any(), any(), any(), any(Runnable.class)))
                .thenAnswer(inv -> {
                    Runnable r = inv.getArgument(3);
                    r.run();
                    return true;
                });

        var user = userRepository.findByEmail("contract-test@farmflow.com")
                .orElseGet(() -> userRepository.save(com.farmflow.entity.User.builder()
                        .name("Contract User")
                        .email("contract-test@farmflow.com")
                        .password("secret1234567")
                        .role("ADMIN")
                        .build()));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );
    }

    @Test
    void shouldProperlyMapTelemetryServiceOutboxPayloadToConsumerActivityWithoutDefaults() {
        String testFarmId = "farm-contract-valid";
        String testPlantationId = "plantation-contract-99";
        String testSensorId = "sensor-soil-depth-01";

        CreateTelemetryRequest request = new CreateTelemetryRequest(
                testFarmId,
                testPlantationId,
                testSensorId,
                TelemetryType.SOIL_MOISTURE,
                12.5,
                "%",
                "95%"
        );

        TelemetryReading reading = telemetryService.recordReading(request);
        assertNotNull(reading);

        List<OutboxMessage> outboxMessages = outboxRepository.findAll().stream()
                .filter(m -> "TelemetryAlert".equals(m.getEventType()))
                .toList();
        assertFalse(outboxMessages.isEmpty());
        OutboxMessage latestOutbox = outboxMessages.get(outboxMessages.size() - 1);

        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-contract-test-001");
        Message message = new Message(latestOutbox.getPayload().getBytes(StandardCharsets.UTF_8), props);

        telemetryAlertConsumer.handleTelemetryAlert(message);

        List<FarmActivity> activities = activityRepository.findByFarmId(testFarmId);
        assertFalse(activities.isEmpty());

        FarmActivity createdActivity = activities.get(activities.size() - 1);
        assertEquals(testFarmId, createdActivity.getFarmId());
        assertEquals(testPlantationId, createdActivity.getPlantationId());
        assertTrue(createdActivity.getTitle().contains(testSensorId));
        assertNotEquals("farm-default", createdActivity.getFarmId());
    }

    @Test
    void shouldFailWhenPayloadMissingRequiredFarmId() {
        MessageProperties props = new MessageProperties();
        props.setMessageId("msg-contract-poison-002");
        Message message = new Message("{\"sensorId\":\"s-only\",\"val\":10.0}".getBytes(StandardCharsets.UTF_8), props);

        telemetryAlertConsumer.handleTelemetryAlert(message);

        List<FarmActivity> activities = activityRepository.findAll().stream()
                .filter(a -> "farm-default".equals(a.getFarmId()))
                .toList();
        assertTrue(activities.isEmpty());

        verify(rabbitTemplate, times(1)).send(
                eq(com.farmflow.config.RabbitMqTopologyConfig.DLX_EXCHANGE),
                eq("events.dlq"),
                any(Message.class)
        );
    }
}
