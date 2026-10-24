package com.farmflow.config.idempotency;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public class JdbcIdempotencyStorage implements IdempotencyStorage {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIdempotencyStorage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<IdempotencyRecord> ROW_MAPPER = (rs, rowNum) -> new IdempotencyRecord(
            rs.getInt("status"),
            rs.getString("content_type"),
            rs.getBytes("response_body"),
            rs.getString("request_hash"),
            rs.getBoolean("in_progress"),
            rs.getTimestamp("created_at").toInstant().toEpochMilli()
    );

    @Override
    public IdempotencyRecord get(String key) {
        try {
            List<IdempotencyRecord> results = jdbcTemplate.query(
                    "SELECT status, content_type, response_body, request_hash, in_progress, created_at " +
                    "FROM idempotency_keys WHERE key = ? AND expires_at > ?",
                    ROW_MAPPER,
                    key,
                    Timestamp.from(Instant.now())
            );
            return results.isEmpty() ? null : results.getFirst();
        } catch (DataAccessException e) {
            return null;
        }
    }

    @Override
    @Transactional
    public boolean putIfAbsentInProgress(String key, long now) {
        try {
            Instant createdAt = Instant.ofEpochMilli(now);
            Instant expiresAt = createdAt.plusSeconds(86400);

            int inserted = jdbcTemplate.update(
                    "INSERT INTO idempotency_keys (key, status, in_progress, created_at, expires_at) " +
                    "VALUES (?, 0, TRUE, ?, ?) ON CONFLICT (key) DO NOTHING",
                    key,
                    Timestamp.from(createdAt),
                    Timestamp.from(expiresAt)
            );
            return inserted > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    @Override
    @Transactional
    public void save(String key, IdempotencyRecord record) {
        try {
            jdbcTemplate.update(
                    "UPDATE idempotency_keys SET status = ?, content_type = ?, response_body = ?, " +
                    "request_hash = ?, in_progress = FALSE WHERE key = ?",
                    record.status(),
                    record.contentType(),
                    record.body(),
                    record.requestHash(),
                    key
            );
        } catch (DataAccessException ignored) {
        }
    }

    @Override
    @Transactional
    public void remove(String key) {
        try {
            jdbcTemplate.update("DELETE FROM idempotency_keys WHERE key = ?", key);
        } catch (DataAccessException ignored) {
        }
    }

    @Override
    @Transactional
    public void evictExpired(long now, long ttlMs) {
        try {
            jdbcTemplate.update("DELETE FROM idempotency_keys WHERE expires_at < ?", Timestamp.from(Instant.now()));
        } catch (DataAccessException ignored) {
        }
    }
}
