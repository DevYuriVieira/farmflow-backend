package com.farmflow.event.cloudevents;

import java.time.Instant;
import java.util.Objects;

public record InputReservedData(
        String reservationId,
        String farmId,
        String activityId,
        String itemId,
        Double quantity,
        String unit,
        Instant reservedAt
) {
    public InputReservedData {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        Objects.requireNonNull(farmId, "farmId must not be null");
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        Objects.requireNonNull(reservedAt, "reservedAt must not be null");

        if (reservationId.isBlank()) throw new IllegalArgumentException("reservationId must not be blank");
        if (farmId.isBlank()) throw new IllegalArgumentException("farmId must not be blank");
        if (activityId.isBlank()) throw new IllegalArgumentException("activityId must not be blank");
        if (itemId.isBlank()) throw new IllegalArgumentException("itemId must not be blank");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be strictly positive");
        if (unit.isBlank()) throw new IllegalArgumentException("unit must not be blank");
    }
}
