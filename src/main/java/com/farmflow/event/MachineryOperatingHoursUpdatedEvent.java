package com.farmflow.event;

import com.farmflow.entity.Machinery;

import java.time.Instant;

public record MachineryOperatingHoursUpdatedEvent(
        Machinery machinery,
        Double previousHours,
        Double newHours,
        Instant occurredOn
) {
    public MachineryOperatingHoursUpdatedEvent(Machinery machinery, Double previousHours, Double newHours) {
        this(machinery, previousHours, newHours, Instant.now());
    }
}