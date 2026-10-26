package com.farmflow.event.cloudevents;

import java.time.Instant;
import java.util.Objects;

public record MachineryHoursUpdatedData(
        String machineryId,
        String farmId,
        Double previousHours,
        Double newHours,
        Instant updatedAt
) {
    public MachineryHoursUpdatedData {
        Objects.requireNonNull(machineryId, "machineryId must not be null");
        Objects.requireNonNull(farmId, "farmId must not be null");
        Objects.requireNonNull(previousHours, "previousHours must not be null");
        Objects.requireNonNull(newHours, "newHours must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (machineryId.isBlank()) throw new IllegalArgumentException("machineryId must not be blank");
        if (farmId.isBlank()) throw new IllegalArgumentException("farmId must not be blank");
        if (previousHours < 0.0) throw new IllegalArgumentException("previousHours must not be negative");
        if (newHours < 0.0) throw new IllegalArgumentException("newHours must not be negative");
    }
}
