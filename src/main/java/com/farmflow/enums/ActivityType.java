package com.farmflow.enums;

public enum ActivityType {
    PLANTING,
    IRRIGATION,
    SPRAYING,
    FERTILIZATION,
    HARVEST,
    SCOUTING,
    MAINTENANCE,
    INSPECTION,
    OTHER;

    public static ActivityType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        String normalized = value.trim().toUpperCase();
        if ("HARVESTING".equals(normalized)) {
            return HARVEST;
        }
        try {
            return ActivityType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
