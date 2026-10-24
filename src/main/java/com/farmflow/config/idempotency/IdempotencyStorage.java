package com.farmflow.config.idempotency;

public interface IdempotencyStorage {

    IdempotencyRecord get(String key);

    boolean putIfAbsentInProgress(String key, long now);

    void save(String key, IdempotencyRecord record);

    void remove(String key);

    void evictExpired(long now, long ttlMs);
}
