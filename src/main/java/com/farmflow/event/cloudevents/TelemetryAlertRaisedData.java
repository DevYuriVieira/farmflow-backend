package com.farmflow.event.cloudevents;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record TelemetryAlertRaisedData(
        String readingId,
        String farmId,
        String plantationId,
        String sensorId,
        String telemetryType,
        Double value,
        String unit,
        String severity,
        String alertReason,
        Instant observedAt
) {
    private static final Set<String> ALLOWED_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public TelemetryAlertRaisedData {
        Objects.requireNonNull(readingId, "readingId must not be null");
        Objects.requireNonNull(farmId, "farmId must not be null");
        Objects.requireNonNull(sensorId, "sensorId must not be null");
        Objects.requireNonNull(telemetryType, "telemetryType must not be null");
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(alertReason, "alertReason must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");

        if (readingId.isBlank()) throw new IllegalArgumentException("readingId must not be blank");
        if (farmId.isBlank()) throw new IllegalArgumentException("farmId must not be blank");
        if (sensorId.isBlank()) throw new IllegalArgumentException("sensorId must not be blank");
        if (telemetryType.isBlank()) throw new IllegalArgumentException("telemetryType must not be blank");
        if (unit.isBlank()) throw new IllegalArgumentException("unit must not be blank");
        if (alertReason.isBlank()) throw new IllegalArgumentException("alertReason must not be blank");
        if (!ALLOWED_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("severity must be one of: " + ALLOWED_SEVERITIES);
        }
    }
}
