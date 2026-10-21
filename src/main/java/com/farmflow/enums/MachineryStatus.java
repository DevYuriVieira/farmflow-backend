package com.farmflow.enums;

import com.farmflow.exceptions.InvalidMachineryStateException;

public enum MachineryStatus {
    OPERATIONAL,
    MAINTENANCE,
    INACTIVE;

    public boolean canTransitionTo(MachineryStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return switch (this) {
            case OPERATIONAL -> target == MAINTENANCE || target == INACTIVE;
            case MAINTENANCE -> target == OPERATIONAL || target == INACTIVE;
            case INACTIVE -> target == OPERATIONAL;
        };
    }

    public static MachineryStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return OPERATIONAL;
        }
        try {
            return MachineryStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidMachineryStateException("Invalid machinery status: " + value);
        }
    }
}
