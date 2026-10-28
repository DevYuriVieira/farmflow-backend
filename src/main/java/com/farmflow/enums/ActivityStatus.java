package com.farmflow.enums;

public enum ActivityStatus {
    PLANNED,
    IN_PROGRESS,
    PENDING_CONFIRMATION,
    COMPLETED,
    CANCELLED;

    public static ActivityStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return IN_PROGRESS;
        }
        try {
            return ActivityStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return IN_PROGRESS;
        }
    }
}
