package com.farmflow.enums;

public enum PlantationStatus {
    PLANNING,
    PLANTED,
    GROWING,
    HARVESTING,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(PlantationStatus next) {
        if (this == next) {
            return true;
        }
        if (next == FAILED) {
            return true;
        }
        return switch (this) {
            case PLANNING -> next == PLANTED;
            case PLANTED -> next == GROWING;
            case GROWING -> next == HARVESTING;
            case HARVESTING -> next == COMPLETED;
            case COMPLETED, FAILED -> false;
        };
    }

    public static PlantationStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new com.farmflow.exceptions.BusinessException("Plantation status cannot be null or blank");
        }
        try {
            return PlantationStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new com.farmflow.exceptions.BusinessException("Invalid plantation status: " + value);
        }
    }
}


