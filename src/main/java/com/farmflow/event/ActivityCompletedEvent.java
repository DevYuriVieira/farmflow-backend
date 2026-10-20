package com.farmflow.event;

import com.farmflow.entity.FarmActivity;

public record ActivityCompletedEvent(FarmActivity activity) {
}
