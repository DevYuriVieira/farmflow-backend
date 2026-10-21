package com.farmflow.event;

import com.farmflow.entity.Machinery;

import java.time.Instant;

public record MachineryMaintenanceScheduledEvent(
        Machinery machinery,
        String reason,
        String expectedDate,
        Instant occurredOn
) {
    public MachineryMaintenanceScheduledEvent(Machinery machinery, String reason, String expectedDate) {
        this(machinery, reason, expectedDate, Instant.now());
    }
}