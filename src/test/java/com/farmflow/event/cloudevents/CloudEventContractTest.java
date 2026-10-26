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

    @Test
    @DisplayName("TelemetryAlertRaised: serialização e validação contra schema v1")
    void shouldSerializeAndValidateTelemetryAlertRaisedEvent() throws Exception {
        TelemetryAlertRaisedData data = new TelemetryAlertRaisedData(
                "tel-1", "farm-1", "plt-1", "sensor-soil-01", "SOIL_MOISTURE",
                14.5, "%", "CRITICAL", "Umidade abaixo do ponto de murcha",
                Instant.parse("2026-10-26T10:00:00Z")
        );

        CloudEvent<TelemetryAlertRaisedData> event = new CloudEvent<>(
                "evt-tel-1", "https://farmflow.com/iot-service",
                "com.farmflow.telemetry.alert.raised.v1", "1.0",
                Instant.parse("2026-10-26T10:00:01Z"),
                "farm-1", "corr-tel-1", data
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertSchemaConformance("/contracts/events/telemetry-alert-raised-v1.json", node);
        assertEquals("sensor-soil-01", node.path("data").path("sensorId").asText());
        assertEquals("CRITICAL", node.path("data").path("severity").asText());
        assertEquals(14.5, node.path("data").path("value").asDouble());
    }

    @Test
    @DisplayName("TelemetryAlertRaised: deve rejeitar severity inválida ou campos em branco")
    void shouldRejectInvalidTelemetryData() {
        assertThrows(IllegalArgumentException.class, () ->
                new TelemetryAlertRaisedData("t-1", "f-1", "p-1", "s-1", "TEMP", 40.0, "C", "INVALID_SEV", "Motivo", Instant.now())
        );
        assertThrows(IllegalArgumentException.class, () ->
                new TelemetryAlertRaisedData("", "f-1", "p-1", "s-1", "TEMP", 40.0, "C", "HIGH", "Motivo", Instant.now())
        );
    }

    @Test
    @DisplayName("MachineryMaintenanceScheduled: serialização e validação contra schema v1")
    void shouldSerializeAndValidateMachineryMaintenanceScheduledEvent() throws Exception {
        MachineryMaintenanceScheduledData data = new MachineryMaintenanceScheduledData(
                "mach-1", "farm-1", "Trator John Deere 8R", "8R 370",
                "Revisão preventiva 500h", "2026-11-01", 498.5,
                Instant.parse("2026-10-26T11:00:00Z")
        );

        CloudEvent<MachineryMaintenanceScheduledData> event = new CloudEvent<>(
                "evt-mach-maint-1", "https://farmflow.com/fleet-service",
                "com.farmflow.machinery.maintenance.scheduled.v1", "1.0",
                Instant.parse("2026-10-26T11:00:02Z"),
                "farm-1", "corr-maint-1", data
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertSchemaConformance("/contracts/events/machinery-maintenance-scheduled-v1.json", node);
        assertEquals("mach-1", node.path("data").path("machineryId").asText());
        assertEquals(498.5, node.path("data").path("currentHours").asDouble());
    }

    @Test
    @DisplayName("MachineryMaintenanceScheduled: deve rejeitar horas negativas ou campos em branco")
    void shouldRejectInvalidMachineryMaintenanceData() {
        assertThrows(IllegalArgumentException.class, () ->
                new MachineryMaintenanceScheduledData("m-1", "f-1", "Trator", "8R", "Revisao", "2026-11-01", -5.0, Instant.now())
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MachineryMaintenanceScheduledData("  ", "f-1", "Trator", "8R", "Revisao", "2026-11-01", 100.0, Instant.now())
        );
    }

    @Test
    @DisplayName("MachineryHoursUpdated: serialização e validação contra schema v1")
    void shouldSerializeAndValidateMachineryHoursUpdatedEvent() throws Exception {
        MachineryHoursUpdatedData data = new MachineryHoursUpdatedData(
                "mach-2", "farm-1", 350.0, 362.5,
                Instant.parse("2026-10-26T12:00:00Z")
        );

        CloudEvent<MachineryHoursUpdatedData> event = new CloudEvent<>(
                "evt-mach-hrs-1", "https://farmflow.com/fleet-service",
                "com.farmflow.machinery.hours.updated.v1", "1.0",
                Instant.parse("2026-10-26T12:00:01Z"),
                "farm-1", "corr-hrs-1", data
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertSchemaConformance("/contracts/events/machinery-hours-updated-v1.json", node);
        assertEquals(350.0, node.path("data").path("previousHours").asDouble());
        assertEquals(362.5, node.path("data").path("newHours").asDouble());
    }

    @Test
    @DisplayName("MachineryHoursUpdated: deve rejeitar horas negativas ou campos em branco")
    void shouldRejectInvalidMachineryHoursData() {
        assertThrows(IllegalArgumentException.class, () ->
                new MachineryHoursUpdatedData("m-1", "f-1", -1.0, 10.0, Instant.now())
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MachineryHoursUpdatedData("m-1", "f-1", 10.0, -5.0, Instant.now())
        );
        assertThrows(IllegalArgumentException.class, () ->
                new MachineryHoursUpdatedData("", "f-1", 10.0, 20.0, Instant.now())
        );
    }

    @Test
    @DisplayName("InputReservationFailed: deve serializar conforme schema canônico")
    void shouldSerializeAndValidateInputReservationFailedCloudEventAgainstJsonSchema() throws Exception {
        InputReservationFailedData data = new InputReservationFailedData(
                "fail-100", "farm-mg", "act-200", "item-fert-01",
                75.0, "INSUFFICIENT_STOCK", Instant.parse("2026-10-26T12:00:00Z")
        );

        CloudEvent<InputReservationFailedData> event = new CloudEvent<>(
                "evt-fail-1", "https://farmflow.com/supply-service",
                "com.farmflow.supply.input.reservation.failed.v1", "1.0",
                Instant.parse("2026-10-26T12:00:01Z"),
                "farm-mg", "corr-fail-1", data
        );

        String json = objectMapper.writeValueAsString(event);
        JsonNode node = objectMapper.readTree(json);

        assertSchemaConformance("/contracts/events/input-reservation-failed-v1.json", node);
        assertEquals("INSUFFICIENT_STOCK", node.path("data").path("reason").asText());
        assertEquals(75.0, node.path("data").path("requestedQuantity").asDouble());
        assertEquals("item-fert-01", node.path("data").path("itemId").asText());
    }

    @Test
    @DisplayName("InputReservationFailed: deve rejeitar quantidade negativa ou campos em branco")
    void shouldRejectInvalidInputReservationFailedData() {
        assertThrows(IllegalArgumentException.class, () ->
                new InputReservationFailedData("f-1", "farm-1", "act-1", "item-1", -10.0, "OUT_OF_STOCK", Instant.now())
        );
        assertThrows(IllegalArgumentException.class, () ->
                new InputReservationFailedData("f-1", "farm-1", "act-1", "item-1", 0.0, "OUT_OF_STOCK", Instant.now())
        );
        assertThrows(IllegalArgumentException.class, () ->
                new InputReservationFailedData(" ", "farm-1", "act-1", "item-1", 10.0, "OUT_OF_STOCK", Instant.now())
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
