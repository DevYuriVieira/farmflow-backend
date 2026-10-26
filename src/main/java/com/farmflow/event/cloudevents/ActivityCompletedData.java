package com.farmflow.event.cloudevents;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ActivityCompletedData(
        String activityId,
        String farmId,
        String plantationId,
        String activityType,
        String title,
        Instant completedAt,
        List<InputUsedItem> inputsUsed,
        String machineryId,
        Double hoursUsed
) {
    public ActivityCompletedData(
            String activityId,
            String farmId,
            String plantationId,
            String activityType,
            String title,
            Instant completedAt,
            List<InputUsedItem> inputsUsed
    ) {
        this(activityId, farmId, plantationId, activityType, title, completedAt, inputsUsed, null, null);
    }

    public ActivityCompletedData {
        Objects.requireNonNull(activityId, "activityId must not be null");
        Objects.requireNonNull(farmId, "farmId must not be null");
        Objects.requireNonNull(activityType, "activityType must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");

        if (activityId.isBlank()) throw new IllegalArgumentException("activityId must not be blank");
        if (farmId.isBlank()) throw new IllegalArgumentException("farmId must not be blank");
        if (activityType.isBlank()) throw new IllegalArgumentException("activityType must not be blank");
        if (title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (hoursUsed != null && hoursUsed < 0) throw new IllegalArgumentException("hoursUsed must not be negative");

        inputsUsed = inputsUsed == null ? List.of() : List.copyOf(inputsUsed);
    }
}
