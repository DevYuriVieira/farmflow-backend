package com.farmflow.event;

import com.farmflow.entity.PestScoutingRecord;

public record PestDetectedEvent(PestScoutingRecord record) {
}
