package com.farmflow.event;

import com.farmflow.entity.TelemetryReading;

public record TelemetryAlertEvent(
        TelemetryReading reading,
        String alertReason,
        String severity
) {
}
