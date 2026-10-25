package com.farmflow.domain;

import com.farmflow.entity.TelemetryReading;
import com.farmflow.enums.TelemetryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryReadingDomainTest {

    @Test
    void shouldCreateTelemetryReadingSuccessfully() {
        Instant now = Instant.now();
        TelemetryReading reading = TelemetryReading.builder()
                .farmId("farm-001")
                .plantationId("plantation-001")
                .sensorId("sensor-soil-01")
                .type(TelemetryType.SOIL_MOISTURE)
                .value(18.5)
                .unit("%")
                .batteryLevel("92%")
                .timestamp(now)
                .build();

        assertEquals("farm-001", reading.getFarmId());
        assertEquals("sensor-soil-01", reading.getSensorId());
        assertEquals(TelemetryType.SOIL_MOISTURE, reading.getType());
        assertEquals(18.5, reading.getValue());
        assertEquals("92%", reading.getBatteryLevel());
        assertEquals(now, reading.getTimestamp());
    }

    @Test
    void shouldRecognizeLowSoilMoistureCondition() {
        TelemetryReading reading = TelemetryReading.builder()
                .farmId("farm-001")
                .type(TelemetryType.SOIL_MOISTURE)
                .value(14.0)
                .build();

        assertTrue(reading.getValue() < 20.0);
    }

    @Test
    void shouldRecognizeHighTemperatureCondition() {
        TelemetryReading reading = TelemetryReading.builder()
                .farmId("farm-001")
                .type(TelemetryType.TEMPERATURE)
                .value(41.2)
                .build();

        assertTrue(reading.getValue() > 38.0);
    }

    @Test
    void shouldReturnTypedQuantityFromReading() {
        TelemetryReading reading = TelemetryReading.builder()
                .farmId("farm-001")
                .type(TelemetryType.SOIL_MOISTURE)
                .value(18.5)
                .unit("%")
                .build();

        assertNotNull(reading.getReadingQuantity());
        assertEquals(18.5, reading.getReadingQuantity().amount());
        assertEquals("%", reading.getReadingQuantity().unit());
    }
}