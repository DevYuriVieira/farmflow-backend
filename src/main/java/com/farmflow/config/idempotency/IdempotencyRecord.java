package com.farmflow.config.idempotency;

public record IdempotencyRecord(
        int status,
        String contentType,
        byte[] body,
        String requestHash,
        boolean inProgress,
        long createdAt
) {
    public static IdempotencyRecord inProgress(long now) {
        return new IdempotencyRecord(0, null, null, null, true, now);
    }
}
