package com.farmflow.config.idempotency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryIdempotencyStorage implements IdempotencyStorage {

    private final Map<String, IdempotencyRecord> storage = new ConcurrentHashMap<>();

    @Override
    public IdempotencyRecord get(String key) {
        return storage.get(key);
    }

    @Override
    public boolean putIfAbsentInProgress(String key, long now) {
        return storage.putIfAbsent(key, IdempotencyRecord.inProgress(now)) == null;
    }

    @Override
    public void save(String key, IdempotencyRecord record) {
        storage.put(key, record);
    }

    @Override
    public void remove(String key) {
        storage.remove(key);
    }

    @Override
    public void evictExpired(long now, long ttlMs) {
        if (storage.size() > 10000) {
            storage.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > ttlMs);
        }
    }
}
