package com.farmflow.config.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class JdbcIdempotencyStorageTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private JdbcIdempotencyStorage sut;

    @Test
    @DisplayName("get: deve retornar registro mapeado quando chave existir")
    void shouldReturnRecordWhenKeyExists() {
        IdempotencyRecord expected = new IdempotencyRecord(200, "application/json", new byte[]{1, 2}, "hash1", false, 1000L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("k1"), any(Timestamp.class)))
                .thenReturn(List.of(expected));

        IdempotencyRecord actual = sut.get("k1");

        assertNotNull(actual);
        assertEquals(200, actual.status());
        assertEquals("application/json", actual.contentType());
        assertEquals("hash1", actual.requestHash());
    }

    @Test
    @DisplayName("get: deve retornar nulo quando chave não existir ou estiver expirada")
    void shouldReturnNullWhenKeyDoesNotExist() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("k2"), any(Timestamp.class)))
                .thenReturn(Collections.emptyList());

        assertNull(sut.get("k2"));
    }

    @Test
    @DisplayName("putIfAbsentInProgress: deve retornar true quando inserção atômica tiver sucesso")
    void shouldReturnTrueWhenInsertSucceeds() {
        when(jdbcTemplate.update(anyString(), eq("k3"), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(1);

        assertTrue(sut.putIfAbsentInProgress("k3", System.currentTimeMillis()));
    }

    @Test
    @DisplayName("putIfAbsentInProgress: deve retornar false quando chave já existir (conflito)")
    void shouldReturnFalseWhenKeyAlreadyExists() {
        when(jdbcTemplate.update(anyString(), eq("k4"), any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(0);

        assertFalse(sut.putIfAbsentInProgress("k4", System.currentTimeMillis()));
    }

    @Test
    @DisplayName("save: deve atualizar registro de idempotência com resposta processada")
    void shouldUpdateIdempotencyRecord() {
        IdempotencyRecord record = new IdempotencyRecord(201, "application/json", new byte[]{3, 4}, "hash2", false, 2000L);

        sut.save("k5", record);

        verify(jdbcTemplate).update(
                anyString(),
                eq(201),
                eq("application/json"),
                eq(record.body()),
                eq("hash2"),
                eq("k5")
        );
    }

    @Test
    @DisplayName("remove: deve deletar chave da tabela")
    void shouldDeleteKeyFromDatabase() {
        sut.remove("k6");

        verify(jdbcTemplate).update(anyString(), eq("k6"));
    }

    @Test
    @DisplayName("evictExpired: deve expurgar registros onde expires_at < now")
    void shouldEvictExpiredKeys() {
        sut.evictExpired(System.currentTimeMillis(), 86400000L);

        verify(jdbcTemplate).update(anyString(), any(Timestamp.class));
    }
}
