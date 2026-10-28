package com.farmflow.rls;

import com.farmflow.config.TenantContext;
import com.farmflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class RlsTenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private boolean isPostgreSql() {
        try {
            String dbProduct = jdbcTemplate.execute((java.sql.Connection conn) ->
                    conn.getMetaData().getDatabaseProductName()
            );
            return dbProduct != null && dbProduct.toLowerCase().contains("postgres");
        } catch (Exception e) {
            return false;
        }
    }

    private void enforceNonSuperuserRole() {
        jdbcTemplate.execute("SET LOCAL ROLE farmflow_app");
    }

    private void setTenantSession(String tenantId) {
        jdbcTemplate.queryForObject("SELECT set_config('app.current_farm_id', ?, true)", String.class, tenantId);
    }

    @BeforeEach
    void setupRlsData() {
        Assumptions.assumeTrue(isPostgreSql(), "RLS strict verification requires a PostgreSQL engine");
    }

    @Test
    @DisplayName("Least Privilege: Role farmflow_app não deve possuir privilégios de superusuário")
    void shouldEnforceLeastPrivilegeOnApplicationRole() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            assertEquals("farmflow_app", jdbcTemplate.queryForObject("SELECT current_user", String.class));
            Boolean superuser = jdbcTemplate.queryForObject(
                    "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);
            assertEquals(Boolean.FALSE, superuser, "Application role must never be superuser nor BYPASSRLS");
            return null;
        });
    }

    @Test
    @DisplayName("RLS Fail-Closed: Query sem WHERE farm_id com Tenant A retorna 0 linhas do Tenant B")
    void shouldIsolateDataBetweenTenantsWhenRlsEnforced() {
        String tenantA = "farm-rls-a-" + UUID.randomUUID();
        String tenantB = "farm-rls-b-" + UUID.randomUUID();

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantA);
            jdbcTemplate.update("""
                INSERT INTO plantations (id, created_at, updated_at, version, farm_id, name, crop_type, area_hectares, status)
                VALUES (?, NOW(), NOW(), 0, ?, 'Talhão A', 'SOYBEAN', 50.0, 'ACTIVE')
            """, UUID.randomUUID().toString(), tenantA);
            return null;
        });

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantB);
            jdbcTemplate.update("""
                INSERT INTO plantations (id, created_at, updated_at, version, farm_id, name, crop_type, area_hectares, status)
                VALUES (?, NOW(), NOW(), 0, ?, 'Talhão B', 'CORN', 75.0, 'ACTIVE')
            """, UUID.randomUUID().toString(), tenantB);
            return null;
        });

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantA);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plantations WHERE farm_id = ?",
                    Integer.class,
                    tenantB
            );
            assertEquals(0, count, "Tenant A cannot see Tenant B records even when querying with explicit tenant B param under RLS");

            Integer totalVisibleWithoutWhere = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plantations",
                    Integer.class
            );
            assertEquals(1, totalVisibleWithoutWhere, "Tenant A must only see its own records without WHERE clause");
            return null;
        });
    }

    @Test
    @DisplayName("RLS Fail-Closed: Sem contexto de farm_id setado retorna 0 linhas")
    void shouldReturnZeroRowsWhenTenantContextNotSet() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession("");
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plantations", Integer.class);
            assertEquals(0, count, "Fail-closed: unauthenticated connection must see 0 rows");
            return null;
        });
    }

    @Test
    @DisplayName("RLS WITH CHECK: INSERT com farm_id divergente do contexto é estritamente rejeitado")
    void shouldRejectCrossTenantInsertUnderWithCheck() {
        String tenantA = "farm-rls-auth-" + UUID.randomUUID();
        String tenantB = "farm-rls-spoofed-" + UUID.randomUUID();

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantA);
            assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.update("""
                    INSERT INTO plantations (id, created_at, updated_at, version, farm_id, name, crop_type, area_hectares, status)
                    VALUES (?, NOW(), NOW(), 0, ?, 'Talhão Invasor', 'SOYBEAN', 25.0, 'ACTIVE')
                """, UUID.randomUUID().toString(), tenantB);
            }, "PostgreSQL RLS WITH CHECK policy violation expected on cross-tenant insert");
            return null;
        });
    }

    @Test
    @DisplayName("RLS Virtual Threads Concurrency: Isolamento estrito de dados e contexto entre threads concorrentes")
    void shouldPreventCrossTenantLeakageAcrossConcurrentVirtualThreads() throws Exception {
        int threadsCount = 10;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(threadsCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < threadsCount; i++) {
            final String tenantId = "farm-loom-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    TenantContext.setTenantId(tenantId);
                    transactionTemplate.execute(status -> {
                        enforceNonSuperuserRole();
                        setTenantSession(tenantId);

                        // Insert tenant-specific plantation under virtual thread
                        jdbcTemplate.update("""
                            INSERT INTO plantations (id, created_at, updated_at, version, farm_id, name, crop_type, area_hectares, status)
                            VALUES (?, NOW(), NOW(), 0, ?, 'Talhão Concorrente', 'SOYBEAN', 10.0, 'ACTIVE')
                        """, UUID.randomUUID().toString(), tenantId);

                        String currentConfig = jdbcTemplate.queryForObject(
                                "SELECT current_setting('app.current_farm_id', true)",
                                String.class
                        );

                        Integer visibleCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM plantations WHERE farm_id = ?",
                                Integer.class,
                                tenantId
                        );

                        if (tenantId.equals(currentConfig)
                                && tenantId.equals(TenantContext.getTenantId())
                                && visibleCount != null && visibleCount == 1) {
                            successCounter.incrementAndGet();
                        }
                        return null;
                    });
                } finally {
                    TenantContext.clear();
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Virtual threads did not finish within timeout");
        assertEquals(threadsCount, successCounter.get(), "Context or data was leaked or corrupted across virtual threads");
    }

    @Test
    @DisplayName("RLS Cryptographic Token: Spoofing app.system_bypass_rls ou token inválido não permite bypass")
    void shouldRejectUnauthorizedSystemMaintenanceBypass() {
        String tenantA = "farm-act-" + UUID.randomUUID();
        String tenantB = "farm-act-" + UUID.randomUUID();

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantA);
            jdbcTemplate.update("""
                INSERT INTO activities (id, created_at, updated_at, version, farm_id, title, type, priority, status, completed)
                VALUES (?, NOW(), NOW(), 0, ?, 'Atividade A', 'PLANTING', 'HIGH', 'IN_PROGRESS', false)
            """, UUID.randomUUID().toString(), tenantA);
            return null;
        });

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantB);
            // Attacker attempts legacy bypass or spoofed token
            jdbcTemplate.execute("SELECT set_config('app.system_bypass_rls', 'true', true)");
            jdbcTemplate.execute("SELECT set_config('app.system_maintenance_token', 'invalid-token-123', true)");

            Integer countTenantA = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activities WHERE farm_id = ?",
                    Integer.class,
                    tenantA
            );
            assertEquals(0, countTenantA, "Spoofed token or legacy bypass GUC must be strictly rejected by RLS");
            return null;
        });
    }

    @Test
    @DisplayName("RLS Cryptographic Token: Token legítimo permite bypass autorizado para manutenção da Saga")
    void shouldAllowAuthorizedSystemMaintenanceBypassWithValidToken() {
        String tenantA = "farm-act-valid-" + UUID.randomUUID();

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantA);
            jdbcTemplate.update("""
                INSERT INTO activities (id, created_at, updated_at, version, farm_id, title, type, priority, status, completed)
                VALUES (?, NOW(), NOW(), 0, ?, 'Atividade Valid Token', 'PLANTING', 'HIGH', 'IN_PROGRESS', false)
            """, UUID.randomUUID().toString(), tenantA);
            return null;
        });

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            // No tenant session set, but authorized maintenance token injected
            jdbcTemplate.execute("SELECT set_config('app.system_maintenance_token', 'farmflow-internal-system-maintenance-token-v1', true)");

            Integer countTenantA = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activities WHERE farm_id = ?",
                    Integer.class,
                    tenantA
            );
            assertEquals(1, countTenantA, "Authorized system maintenance token must allow cross-tenant reading under RLS policy");
            return null;
        });
    }

    @Test
    @DisplayName("Security: Tabela system_maintenance_auth deve ser inacessível para farmflow_app")
    void shouldDenyApplicationRoleAccessToSystemMaintenanceAuth() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM system_maintenance_auth", Integer.class);
            }, "Application role farmflow_app must be denied access (REVOKE ALL) on system_maintenance_auth");
            return null;
        });
    }

    @Test
    @DisplayName("Partition Security: telemetry_readings não pertence a farmflow_app e possui FORCE RLS")
    void shouldEnforcePartitionOwnershipAndForceRls() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            String owner = jdbcTemplate.queryForObject("""
                SELECT r.rolname
                FROM pg_class c
                JOIN pg_roles r ON r.oid = c.relowner
                WHERE c.relname = 'telemetry_readings'
            """, String.class);
            assertNotEquals("farmflow_app", owner, "Table telemetry_readings must NOT be owned by application role");

            Boolean forceRls = jdbcTemplate.queryForObject("""
                SELECT relforcerowsecurity
                FROM pg_class
                WHERE relname = 'telemetry_readings'
            """, Boolean.class);
            assertEquals(Boolean.TRUE, forceRls, "Table telemetry_readings must have relforcerowsecurity enabled");
            return null;
        });
    }

    @Test
    @DisplayName("Partition Security: Partições filhas de telemetry_readings não pertencem a farmflow_app e têm FORCE RLS")
    void shouldEnforceChildPartitionOwnershipAndForceRls() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            Integer unforcedCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_inherits i
                JOIN pg_class c ON c.oid = i.inhrelid
                JOIN pg_roles r ON r.oid = c.relowner
                WHERE i.inhparent = 'telemetry_readings'::regclass
                  AND (r.rolname = 'farmflow_app' OR c.relforcerowsecurity = false)
            """, Integer.class);
            assertEquals(0, unforcedCount, "No telemetry child partition may be owned by farmflow_app or have relforcerowsecurity disabled");
            return null;
        });
    }

    @Test
    @DisplayName("Security: Rotação de token via rotate_system_maintenance_token é permitida para admin e rejeitada para app")
    void shouldAllowTokenRotationByAdminAndRejectApplicationRole() {
        String newSecret = "new-rotated-super-strong-token-32chars";

        transactionTemplate.execute(status -> {
            // 1. As admin (current session user), rotation succeeds
            Boolean rotated = jdbcTemplate.queryForObject(
                    "SELECT rotate_system_maintenance_token(?)", Boolean.class, newSecret);
            assertEquals(Boolean.TRUE, rotated);

            // Verify old default token is no longer accepted
            Boolean oldAccepted = jdbcTemplate.queryForObject(
                    "SELECT verify_system_maintenance_token('farmflow-internal-system-maintenance-token-v1')", Boolean.class);
            assertEquals(Boolean.FALSE, oldAccepted, "Old default token must be rejected after rotation");

            // Verify new rotated token is accepted
            Boolean newAccepted = jdbcTemplate.queryForObject(
                    "SELECT verify_system_maintenance_token(?)", Boolean.class, newSecret);
            assertEquals(Boolean.TRUE, newAccepted, "Rotated token must be accepted");

            // 2. As application role, calling rotate_system_maintenance_token is strictly denied
            enforceNonSuperuserRole();
            assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.execute("SELECT rotate_system_maintenance_token('hacker-attempt-token-32chars-long')");
            }, "Application role farmflow_app must be denied access to rotate_system_maintenance_token");

            // Rollback rotation so other tests retain the test fixture token
            status.setRollbackOnly();
            return null;
        });
    }

    @Test
    @DisplayName("Partition Security: farmflow_app não pode executar DISABLE ROW LEVEL SECURITY em telemetry_readings")
    void shouldPreventApplicationRoleFromDisablingRlsOnTelemetryReadings() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.execute("ALTER TABLE telemetry_readings DISABLE ROW LEVEL SECURITY");
            }, "Application role must NOT have table ownership privileges to disable RLS");
            return null;
        });
    }

    @Test
    @DisplayName("Partition RLS: Isolamento estrito de dados entre tenants na tabela particionada telemetry_readings")
    void shouldEnforceRlsOnPartitionedTelemetryReadings() {
        String tenantA = "farm-telem-a-" + UUID.randomUUID();
        String tenantB = "farm-telem-b-" + UUID.randomUUID();

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantA);
            jdbcTemplate.update("""
                INSERT INTO telemetry_readings (id, created_at, updated_at, version, farm_id, sensor_id, reading_type, reading_value, unit_measure, recorded_at)
                VALUES (?, NOW(), NOW(), 0, ?, 'sensor-soil-1', 'SOIL_MOISTURE', 42.5, '%', NOW())
            """, UUID.randomUUID().toString(), tenantA);
            return null;
        });

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantB);
            jdbcTemplate.update("""
                INSERT INTO telemetry_readings (id, created_at, updated_at, version, farm_id, sensor_id, reading_type, reading_value, unit_measure, recorded_at)
                VALUES (?, NOW(), NOW(), 0, ?, 'sensor-soil-2', 'SOIL_MOISTURE', 38.0, '%', NOW())
            """, UUID.randomUUID().toString(), tenantB);
            return null;
        });

        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession(tenantA);
            Integer countTenantB = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM telemetry_readings WHERE farm_id = ?",
                    Integer.class,
                    tenantB
            );
            assertEquals(0, countTenantB, "Tenant A cannot read Tenant B telemetry readings from partitioned table");

            Integer totalTenantA = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM telemetry_readings",
                    Integer.class
            );
            assertEquals(1, totalTenantA, "Tenant A can only see its own telemetry readings under RLS");
            return null;
        });
    }

    @Test
    @DisplayName("Partition Lifecycle: farmflow_app desanexa particao e inspeciona contagem da particao default")
    void shouldAllowApplicationRoleToDetachTelemetryPartitionAndInspectDefaultCount() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();

            Boolean created = jdbcTemplate.queryForObject(
                    "SELECT create_telemetry_partition('telemetry_readings_2028_01', '2028-01-01 00:00:00+00', '2028-02-01 00:00:00+00')",
                    Boolean.class
            );
            assertEquals(Boolean.TRUE, created);

            Boolean detached = jdbcTemplate.queryForObject(
                    "SELECT detach_telemetry_partition('telemetry_readings_2028_01')",
                    Boolean.class
            );
            assertEquals(Boolean.TRUE, detached);

            Long defaultCount = jdbcTemplate.queryForObject(
                    "SELECT get_telemetry_default_partition_row_count()",
                    Long.class
            );
            assertNotNull(defaultCount);
            assertTrue(defaultCount >= 0);

            status.setRollbackOnly();
            return null;
        });
    }

    @Test
    @DisplayName("Security Audit: 100% das funções SECURITY DEFINER devem fixar search_path estrito e imutável")
    void shouldEnforceSearchPathOnAllSecurityDefinerFunctions() {
        transactionTemplate.execute(status -> {
            java.util.List<String> vulnerableFunctions = jdbcTemplate.query("""
                SELECT p.proname
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public'
                  AND p.prosecdef = true
                  AND (p.proconfig IS NULL OR NOT (p.proconfig @> ARRAY['search_path=public, pg_temp']))
            """, (rs, rowNum) -> rs.getString("proname"));

            assertTrue(vulnerableFunctions.isEmpty(),
                    "Vulnerabilidade de Search-Path Hijacking detectada nas funções SECURITY DEFINER: " + vulnerableFunctions);
            return null;
        });
    }

    @Test
    @DisplayName("Partition Least Privilege: farmflow_app não pode acessar partição filha diretamente sem passar pela tabela raiz")
    void shouldDenyDirectAccessToTelemetryChildPartitionsForApplicationRole() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            setTenantSession("farm-telem-child-" + UUID.randomUUID());

            assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.execute("SELECT * FROM telemetry_readings_2026_10 LIMIT 1");
            }, "Direct access to child partition must be denied to enforce access via partitioned root table");

            assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.execute("SELECT * FROM telemetry_readings_default LIMIT 1");
            }, "Direct access to default child partition must also be denied to enforce access via partitioned root table");

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM telemetry_readings WHERE recorded_at >= '2026-10-01 00:00:00+00' AND recorded_at < '2026-11-01 00:00:00+00'",
                    Integer.class
            );
            assertNotNull(count);
            return null;
        });
    }

    @Test
    @DisplayName("Security & Audit: Elevação de contexto é auditada em system_maintenance_audit_trail (inacessível para farmflow_app)")
    void shouldAuditMaintenanceContextElevationAndDenyDirectAccessToAuditTrail() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();
            Boolean elevated = jdbcTemplate.queryForObject(
                    "SELECT enable_system_maintenance_context('farmflow-internal-system-maintenance-token-v1')",
                    Boolean.class
            );
            assertEquals(Boolean.TRUE, elevated);

            Boolean cleared = jdbcTemplate.queryForObject(
                    "SELECT disable_system_maintenance_context()",
                    Boolean.class
            );
            assertEquals(Boolean.TRUE, cleared);

            assertThrows(DataAccessException.class, () -> {
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM system_maintenance_audit_trail", Integer.class);
            });

            return null;
        });

        transactionTemplate.execute(status -> {
            Integer auditCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM system_maintenance_audit_trail WHERE action = 'ELEVATE_MAINTENANCE_CONTEXT' AND success = true",
                    Integer.class
            );
            assertNotNull(auditCount);
            assertTrue(auditCount >= 1);
            return null;
        });
    }

    @Test
    @DisplayName("Partition Lifecycle: farmflow_app executa archive_and_drop_telemetry_partition para purga de partição histórica")
    void shouldAllowApplicationRoleToArchiveAndDropTelemetryPartition() {
        transactionTemplate.execute(status -> {
            enforceNonSuperuserRole();

            Boolean created = jdbcTemplate.queryForObject(
                    "SELECT create_telemetry_partition('telemetry_readings_2029_01', '2029-01-01 00:00:00+00', '2029-02-01 00:00:00+00')",
                    Boolean.class
            );
            assertEquals(Boolean.TRUE, created);

            Boolean purged = jdbcTemplate.queryForObject(
                    "SELECT archive_and_drop_telemetry_partition('telemetry_readings_2029_01')",
                    Boolean.class
            );
            assertEquals(Boolean.TRUE, purged);

            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_class WHERE relname = 'telemetry_readings_2029_01'",
                    Integer.class
            );
            assertEquals(0, exists);

            return null;
        });
    }
}
