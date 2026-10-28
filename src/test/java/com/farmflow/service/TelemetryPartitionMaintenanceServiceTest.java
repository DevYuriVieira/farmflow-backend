package com.farmflow.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TelemetryPartitionMaintenanceServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private TelemetryPartitionMaintenanceService sut;

    @BeforeEach
    void setUp() {
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.getSingleResult()).thenReturn(true);
    }

    @Test
    @DisplayName("deve executar rotina de criacao de particoes futuras para os proximos 3 meses")
    void shouldEnsureFuturePartitionsForNextThreeMonths() {
        sut.ensureFuturePartitions();
        verify(entityManager, times(4)).createNativeQuery(contains("create_telemetry_partition"));
        verify(query, times(4)).getSingleResult();
    }

    @Test
    @DisplayName("deve criar particao individual para determinado YearMonth com limites de range")
    void shouldCreateSpecificPartitionForYearMonth() {
        YearMonth target = YearMonth.of(2027, 6);
        sut.createPartitionIfNotExists(target);
        verify(entityManager).createNativeQuery(contains("create_telemetry_partition"));
        verify(query).setParameter("name", "telemetry_readings_2027_06");
        verify(query).setParameter("from", "2027-06-01T00:00Z");
        verify(query).setParameter("to", "2027-07-01T00:00Z");
        verify(query).getSingleResult();
    }

    @Test
    @DisplayName("deve desanexar particao expirada com chamada a detach_telemetry_partition")
    void shouldDetachSpecificPartitionForYearMonth() {
        YearMonth target = YearMonth.of(2022, 1);
        boolean detached = sut.detachPartition(target);
        assertTrue(detached);
        verify(entityManager).createNativeQuery(contains("detach_telemetry_partition"));
        verify(query).setParameter("name", "telemetry_readings_2022_01");
        verify(query).getSingleResult();
    }

    @Test
    @DisplayName("deve inspecionar anomalias na particao default e retornar contagem de linhas")
    void shouldInspectDefaultPartitionAnomaliesAndReturnRowCount() {
        when(entityManager.createNativeQuery(contains("get_telemetry_default_partition_row_count")))
                .thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);

        long count = sut.inspectDefaultPartitionAnomalies();
        assertEquals(0L, count);
        verify(entityManager).createNativeQuery(contains("get_telemetry_default_partition_row_count"));
    }

    @Test
    @DisplayName("deve executar ciclo de vida completo de manutencao de particoes")
    void shouldRunFullPartitionLifecycleMaintenance() {
        when(entityManager.createNativeQuery(contains("get_telemetry_default_partition_row_count")))
                .thenReturn(query);
        when(query.getSingleResult()).thenReturn(5L);

        sut.runFullPartitionLifecycleMaintenance();
        verify(entityManager, atLeast(4)).createNativeQuery(contains("create_telemetry_partition"));
        verify(entityManager, atLeast(1)).createNativeQuery(contains("detach_telemetry_partition"));
        verify(entityManager).createNativeQuery(contains("get_telemetry_default_partition_row_count"));
    }
}
