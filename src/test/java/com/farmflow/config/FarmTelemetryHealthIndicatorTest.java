package com.farmflow.config;

import com.farmflow.repository.TelemetryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmTelemetryHealthIndicatorTest {

    @Mock
    private TelemetryRepository telemetryRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private FarmTelemetryHealthIndicator healthIndicator;

    @Test
    @DisplayName("health: retorna UP com SKIPPED_ON_H2 quando banco não é PostgreSQL")
    void shouldReturnUpWhenH2Database() throws Exception {
        when(telemetryRepository.count()).thenReturn(150L);

        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> callback = inv.getArgument(0);
            Connection conn = mock(Connection.class);
            when(conn.getMetaData()).thenReturn(metaData);
            return callback.doInConnection(conn);
        });

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(150L, health.getDetails().get("totalTelemetryReadings"));
        assertEquals("SKIPPED_ON_H2", health.getDetails().get("partitionVerification"));
    }

    @Test
    @DisplayName("health: retorna UP com OPTIMAL quando PostgreSQL tem partições ativas")
    void shouldReturnUpWhenPostgresWithActivePartitions() throws Exception {
        when(telemetryRepository.count()).thenReturn(5000L);

        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> callback = inv.getArgument(0);
            Connection conn = mock(Connection.class);
            when(conn.getMetaData()).thenReturn(metaData);
            return callback.doInConnection(conn);
        });

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
                .thenReturn(3);

        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("OPTIMAL", health.getDetails().get("partitionStatus"));
        assertEquals(3, health.getDetails().get("activePartitionsCount"));
    }

    @Test
    @DisplayName("health: retorna DEGRADED quando PostgreSQL não possui partições futuras")
    void shouldReturnDegradedWhenFuturePartitionsMissing() throws Exception {
        when(telemetryRepository.count()).thenReturn(100L);

        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> callback = inv.getArgument(0);
            Connection conn = mock(Connection.class);
            when(conn.getMetaData()).thenReturn(metaData);
            return callback.doInConnection(conn);
        });

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any(), any()))
                .thenReturn(0);

        Health health = healthIndicator.health();

        assertEquals("DEGRADED", health.getStatus().getCode());
        assertNotNull(health.getDetails().get("error"));
    }
}
