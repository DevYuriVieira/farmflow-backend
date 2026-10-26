package com.farmflow.event.cloudevents;

import java.time.Instant;
import java.util.Objects;

public record MachineryMaintenanceScheduledData(
        String machineryId,
        String farmId,
        String name,
        String model,
        String reason,
        String expectedDate,
        Double currentHours,
        Instant scheduledAt
) {
    public MachineryMaintenanceScheduledData {
        Objects.requireNonNull(machineryId, "machineryId must not be null");
        Objects.requireNonNull(farmId, "farmId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(currentHours, "currentHours must not be null");
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");

        if (machineryId.isBlank()) throw new IllegalArgumentException("machineryId must not be blank");
        if (farmId.isBlank()) throw new IllegalArgumentException("farmId must not be blank");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        if (currentHours < 0.0) throw new IllegalArgumentException("currentHours must not be negative");
    }
}
