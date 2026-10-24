package com.farmflow.event.cloudevents;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CloudEvent<T>(
        String id,
        String source,
        String type,
        String specversion,
        Instant time,
        String tenantId,
        String correlationId,
        T data
) {
    public CloudEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(specversion, "specversion must not be null");
        Objects.requireNonNull(time, "time must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(data, "data must not be null");

        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        if (type.isBlank()) throw new IllegalArgumentException("type must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (correlationId.isBlank()) throw new IllegalArgumentException("correlationId must not be blank");
    }

    public static <T> CloudEvent<T> of(
            String source,
            String type,
            String tenantId,
            String correlationId,
            T data
    ) {
        return new CloudEvent<>(
                UUID.randomUUID().toString(),
                source,
                type,
                "1.0",
                Instant.now(),
                tenantId,
                correlationId,
                data
        );
    }
}
