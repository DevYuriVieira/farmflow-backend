package com.farmflow.event.cloudevents;

import java.time.Instant;
import java.util.Objects;

public record InputReservationFailedData(
        String failureId,
        String farmId,
        String activityId,
        String itemId,
        Double requestedQuantity,
        String reason,
        Instant failedAt
) {
    public InputReservationFailedData {
        Objects.requireNonNull(failureId, "failureId must not be null");
        Objects.requireNonNull(farmId, "farmId must not be null");
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(requestedQuantity, "requestedQuantity must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");

        if (failureId.isBlank()) throw new IllegalArgumentException("failureId must not be blank");
        if (farmId.isBlank()) throw new IllegalArgumentException("farmId must not be blank");
        if (activityId.isBlank()) throw new IllegalArgumentException("activityId must not be blank");
        if (itemId.isBlank()) throw new IllegalArgumentException("itemId must not be blank");
        if (requestedQuantity <= 0) throw new IllegalArgumentException("requestedQuantity must be strictly positive");
        if (reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
    }
}
