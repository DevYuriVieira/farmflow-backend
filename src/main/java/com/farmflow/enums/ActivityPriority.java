package com.farmflow.enums;

public enum ActivityPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT;

    public static ActivityPriority fromString(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return ActivityPriority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
