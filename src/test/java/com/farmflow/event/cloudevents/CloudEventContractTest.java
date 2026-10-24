package com.farmflow.event.cloudevents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloudEventContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("CloudEvent.of: deve instanciar envelope com UUID e specversion 1.0")
    void shouldCreateCloudEventWithDefaultMetadata() {
        ActivityCompletedData data = new ActivityCompletedData(
                "act-1", "farm-1", "plt-1", "HARVEST", "Colheita de Milho",
                Instant.now(), List.of(new InputUsedItem("item-1", 50.0, "kg"))
        );

        CloudEvent<ActivityCompletedData> event = CloudEvent.of(
                "https://farmflow.com/core-service",
                "com.farmflow.activity.completed.v1",
                "tenant-farm-1",
                "corr-123",
                data
        );

        assertNotNull(event.id());
        assertFalse(event.id().isBlank());
        assertEquals("1.0", event.specversion());
        assertEquals("https://farmflow.com/core-service", event.source());
        assertEquals("com.farmflow.activity.completed.v1", event.type());
        assertEquals("tenant-farm-1", event.tenantId());
        assertEquals("corr-123", event.correlationId());
        assertEquals(data, event.data());
    }

    @Test
    @DisplayName("CloudEvent: deve rejeitar parâmetros nulos ou vazios no construtor compacto")
    void shouldEnforceInvariantsOnCloudEvent() {
        assertThrows(NullPointerException.class, () ->
                new CloudEvent<>(null, "src", "type", "1.0", Instant.now(), "tenant", "corr", "data")
        );
        assertThrows(IllegalArgumentException.class, () ->
                new CloudEvent<>("  ", "src", "type", "1.0", Instant.now(), "tenant", "corr", "data")
        );
        assertThrows(IllegalArgumentException.class, () ->
                new CloudEvent<>("id-1", "", "type", "1.0", Instant.now(), "tenant", "corr", "data")
        );
        assertThrows(IllegalArgumentException.class, () ->
                new CloudEvent<>("id-1", "src", "type", "1.0", Instant.now(), "  ", "corr", "data")
        );
    }

    @Test
    @DisplayName("ActivityCompleted: serialização e validação contra schema v1")
    void shouldSerializeAndValidateActivityCompletedEvent() throws Exception {
        ActivityCompletedData data = new ActivityCompletedData(
                "act-99", "farm-uberaba", "plt-12", "FERTILIZATION", "Adubação Foliar",
                Instant.parse("2026-10-23T16:30:00Z"),
                List.of(new InputUsedItem("inv-100", 25.5, "litros"))
        );

        CloudEvent<ActivityCompletedData> event = new CloudEvent<>(
                "evt-act-99", "https://farmflow.com/core-service",
                "com.farmflow.activity.completed.v1", "1.0",
                Instant.parse("2026-10-23T16:30:05Z"),
                "farm-uberaba", "corr-act-99", data
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertSchemaConformance("/contracts/events/activity-completed-v1.json", node);
        assertEquals("act-99", node.path("data").path("activityId").asText());
        assertEquals(25.5, node.path("data").path("inputsUsed").get(0).path("quantity").asDouble());
    }

    @Test
    @DisplayName("PestAlertRaised: serialização e validação contra schema v1")
    void shouldSerializeAndValidatePestAlertRaisedEvent() throws Exception {
        PestAlertRaisedData data = new PestAlertRaisedData(
                "scout-55", "farm-cerrado", "plt-4", "Spodoptera frugiperda",
                "HIGH", "Aplicação de biodefensivo urgente",
                Instant.parse("2026-10-23T17:00:00Z")
        );

        CloudEvent<PestAlertRaisedData> event = new CloudEvent<>(
                "evt-pest-55", "https://farmflow.com/scouting-service",
                "com.farmflow.pest.alert.raised.v1", "1.0",
                Instant.parse("2026-10-23T17:00:01Z"),
                "farm-cerrado", "corr-pest-55", data
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertSchemaConformance("/contracts/events/pest-alert-raised-v1.json", node);
        assertEquals("HIGH", node.path("data").path("severity").asText());
    }

    @Test
    @DisplayName("PestAlertRaised: deve rejeitar severity inválida")
    void shouldRejectInvalidPestSeverity() {
        assertThrows(IllegalArgumentException.class, () ->
                new PestAlertRaisedData("scout-1", "f-1", "plt-1", "Spodoptera", "EXTREME", "Ação", Instant.now())
        );
    }

    @Test
    @DisplayName("InputReserved: serialização e validação contra schema v1")
    void shouldSerializeAndValidateInputReservedEvent() throws Exception {
        InputReservedData data = new InputReservedData(
                "res-88", "farm-goias", "act-99", "item-npk-04-14-08",
                500.0, "kg", Instant.parse("2026-10-23T17:15:00Z")
        );

        CloudEvent<InputReservedData> event = new CloudEvent<>(
                "evt-res-88", "https://farmflow.com/supply-service",
                "com.farmflow.supply.input.reserved.v1", "1.0",
                Instant.parse("2026-10-23T17:15:02Z"),
                "farm-goias", "corr-res-88", data
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertSchemaConformance("/contracts/events/input-reserved-v1.json", node);
        assertEquals(500.0, node.path("data").path("quantity").asDouble());
    }

    @Test
    @DisplayName("InputReserved e InputUsedItem: devem rejeitar quantidade menor ou igual a zero")
    void shouldRejectNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                new InputReservedData("res-1", "f-1", "a-1", "item-1", 0.0, "kg", Instant.now())
        );
        assertThrows(IllegalArgumentException.class, () ->
                new InputReservedData("res-1", "f-1", "a-1", "item-1", -10.0, "kg", Instant.now())
        );
        assertThrows(IllegalArgumentException.class, () ->
                new InputUsedItem("item-1", -1.0, "kg")
        );
    }

    private void assertSchemaConformance(String schemaResourcePath, JsonNode payloadNode) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(schemaResourcePath)) {
            assertNotNull(is, "Schema resource must exist: " + schemaResourcePath);
            JsonNode schemaNode = objectMapper.readTree(is);

            JsonNode requiredFields = schemaNode.path("required");
            assertTrue(requiredFields.isArray(), "Schema required must be an array");
            for (JsonNode reqField : requiredFields) {
                String fieldName = reqField.asText();
                assertTrue(payloadNode.hasNonNull(fieldName), "Payload must contain required field: " + fieldName);
            }

            JsonNode dataRequired = schemaNode.path("properties").path("data").path("required");
            if (dataRequired.isArray()) {
                JsonNode dataNode = payloadNode.path("data");
                assertTrue(dataNode.isObject(), "data must be an object");
                for (JsonNode reqDataField : dataRequired) {
                    String fieldName = reqDataField.asText();
                    assertTrue(dataNode.hasNonNull(fieldName), "data payload must contain required field: " + fieldName);
                }
            }
        }
    }
}
