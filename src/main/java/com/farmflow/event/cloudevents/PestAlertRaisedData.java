package com.farmflow.event.cloudevents;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record PestAlertRaisedData(
        String scoutingId,
        String farmId,
        String plantationId,
        String pestType,
        String severity,
        String recommendedAction,
        Instant observedAt
) {
    private static final Set<String> ALLOWED_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    public PestAlertRaisedData {
        Objects.requireNonNull(scoutingId, "scoutingId must not be null");
        Objects.requireNonNull(farmId, "farmId must not be null");
        Objects.requireNonNull(plantationId, "plantationId must not be null");
        Objects.requireNonNull(pestType, "pestType must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");

        if (scoutingId.isBlank()) throw new IllegalArgumentException("scoutingId must not be blank");
        if (farmId.isBlank()) throw new IllegalArgumentException("farmId must not be blank");
        if (plantationId.isBlank()) throw new IllegalArgumentException("plantationId must not be blank");
        if (pestType.isBlank()) throw new IllegalArgumentException("pestType must not be blank");
        if (!ALLOWED_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("severity must be one of: " + ALLOWED_SEVERITIES);
        }
    }
}
