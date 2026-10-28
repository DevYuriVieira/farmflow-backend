package com.farmflow.service;

import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.entity.FarmActivity;
import com.farmflow.entity.SagaAuditLog;
import com.farmflow.enums.ActivityPriority;
import com.farmflow.enums.ActivityStatus;
import com.farmflow.enums.ActivityType;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.repository.ActivityRepository;
import com.farmflow.repository.SagaAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SagaTimeoutServiceTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private SagaAuditLogRepository sagaAuditLogRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private FarmFlowMetrics farmFlowMetrics;

    private SagaTimeoutService sut;

    @BeforeEach
    void setUp() {
        sut = new SagaTimeoutService(
                activityRepository,
                sagaAuditLogRepository,
                outboxService,
                farmFlowMetrics
        );
    }

    @Test
    @DisplayName("deve identificar atividades expiradas em PENDING_CONFIRMATION e aplicar compensacao automatica")
    void shouldCompensateTimedOutSagasAutomatically() {
        FarmActivity activity = FarmActivity.builder()
                .id("act-timeout-1")
                .farmId("farm-1")
                .plantationId("plt-1")
                .title("Pulverizacao")
                .type(ActivityType.SPRAYING)
                .priority(ActivityPriority.HIGH)
                .status(ActivityStatus.PENDING_CONFIRMATION)
                .completed(false)
                .build();

        when(activityRepository.findByStatusAndUpdatedAtBefore(eq(ActivityStatus.PENDING_CONFIRMATION), any(Instant.class)))
                .thenReturn(List.of(activity));

        sut.checkAndCompensateTimedOutSagas();

        ArgumentCaptor<FarmActivity> activityCaptor = ArgumentCaptor.forClass(FarmActivity.class);
        verify(activityRepository).save(activityCaptor.capture());
        FarmActivity savedActivity = activityCaptor.getValue();
        assertEquals(ActivityStatus.IN_PROGRESS, savedActivity.getStatus());
        assertFalse(savedActivity.getCompleted());

        ArgumentCaptor<SagaAuditLog> auditCaptor = ArgumentCaptor.forClass(SagaAuditLog.class);
        verify(sagaAuditLogRepository).save(auditCaptor.capture());
        SagaAuditLog savedAudit = auditCaptor.getValue();
        assertEquals("farm-1", savedAudit.getFarmId());
        assertEquals("act-timeout-1", savedAudit.getActivityId());
        assertEquals("PENDING_CONFIRMATION", savedAudit.getTransitionFrom());
        assertEquals("IN_PROGRESS", savedAudit.getTransitionTo());

        verify(outboxService).recordCloudEvent(any(CloudEvent.class));
        verify(farmFlowMetrics).incrementSagaTimeout("ActivityInputReservation");
    }

    @Test
    @DisplayName("nao deve executar acoes quando nao houver atividades expiradas")
    void shouldDoNothingWhenNoActivitiesAreTimedOut() {
        when(activityRepository.findByStatusAndUpdatedAtBefore(eq(ActivityStatus.PENDING_CONFIRMATION), any(Instant.class)))
                .thenReturn(List.of());

        sut.checkAndCompensateTimedOutSagas();

        verify(activityRepository, never()).save(any());
        verify(sagaAuditLogRepository, never()).save(any());
        verify(outboxService, never()).recordCloudEvent(any());
        verify(farmFlowMetrics, never()).incrementSagaTimeout(any());
    }

    @Test
    @DisplayName("Security: Em perfil prod com token default ou fraco deve lancar IllegalStateException no startup")
    void shouldFailClosedInProductionWhenMaintenanceTokenIsInsecureOrDefault() {
        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", "farmflow-internal-system-maintenance-token-v1");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> sut.validateProductionSecurity());
        assertTrue(ex.getMessage().contains("CRITICAL SECURITY VIOLATION"));
    }

    @Test
    @DisplayName("Security: Em perfil prod com token forte customizado deve validar com sucesso")
    void shouldPassInProductionWhenMaintenanceTokenIsStrong() {
        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", "a-very-strong-production-cryptographic-token-32chars");

        assertDoesNotThrow(() -> sut.validateProductionSecurity());
    }

    @Test
    @DisplayName("Security: Em perfil dev ou test com token default nao deve lancar excecao")
    void shouldPassInDevelopmentEvenWithDefaultToken() {
        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"test"});

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", "farmflow-internal-system-maintenance-token-v1");

        assertDoesNotThrow(() -> sut.validateProductionSecurity());
    }

    @Test
    @DisplayName("Security: Startup em producao deve falhar se o banco de dados ainda aceitar o token default")
    void shouldFailClosedWhenDatabaseStillAcceptsDefaultTokenInProduction() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query queryDefault = mock(jakarta.persistence.Query.class);
        when(em.createNativeQuery(contains("verify_system_maintenance_token(:checkToken)")))
                .thenReturn(queryDefault);
        when(queryDefault.setParameter(eq("checkToken"), anyString())).thenReturn(queryDefault);
        when(queryDefault.getSingleResult()).thenReturn(true);

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "postgresEngine", true);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", "a-strong-token-with-at-least-32-characters-configured");

        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> sut.validateProductionSecurity());
        assertTrue(ex.getMessage().contains("Database system_maintenance_auth still contains the default development token hash"));
    }

    @Test
    @DisplayName("Security: Startup em producao deve suceder quando banco rejeita token default e aceita token configurado")
    void shouldSucceedWhenDatabaseRejectsDefaultAndAcceptsConfiguredToken() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query queryDefault = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query queryConfigured = mock(jakarta.persistence.Query.class);

        when(em.createNativeQuery(contains("verify_system_maintenance_token(:checkToken)")))
                .thenReturn(queryDefault);
        when(queryDefault.setParameter(eq("checkToken"), anyString())).thenReturn(queryDefault);
        when(queryDefault.getSingleResult()).thenReturn(false);

        when(em.createNativeQuery(contains("verify_system_maintenance_token(:token)")))
                .thenReturn(queryConfigured);
        when(queryConfigured.setParameter(eq("token"), anyString())).thenReturn(queryConfigured);
        when(queryConfigured.getSingleResult()).thenReturn(true);

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "postgresEngine", true);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", "a-strong-token-with-at-least-32-characters-configured");

        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);

        assertDoesNotThrow(() -> sut.validateProductionSecurity());
    }

    @Test
    @DisplayName("Security: Startup em producao deve falhar closed se query ao banco falhar com erro inesperado")
    void shouldFailClosedWhenDatabaseThrowsExceptionDuringStartupInProduction() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        when(em.createNativeQuery(anyString()))
                .thenThrow(new RuntimeException("Connection pool exhausted / Network timeout"));

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "postgresEngine", true);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", "a-strong-token-with-at-least-32-characters-configured");

        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> sut.validateProductionSecurity());
        assertTrue(ex.getMessage().contains("Database token hash verification failed during production startup"));
    }

    @Test
    @DisplayName("Security: Startup em producao deve falhar se banco rejeitar o token configurado")
    void shouldFailClosedWhenDatabaseRejectsConfiguredTokenInProduction() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query queryDefault = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query queryConfigured = mock(jakarta.persistence.Query.class);

        when(em.createNativeQuery(contains("verify_system_maintenance_token(:checkToken)")))
                .thenReturn(queryDefault);
        when(queryDefault.setParameter(eq("checkToken"), anyString())).thenReturn(queryDefault);
        when(queryDefault.getSingleResult()).thenReturn(false);

        when(em.createNativeQuery(contains("verify_system_maintenance_token(:token)")))
                .thenReturn(queryConfigured);
        when(queryConfigured.setParameter(eq("token"), anyString())).thenReturn(queryConfigured);
        when(queryConfigured.getSingleResult()).thenReturn(false);

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "postgresEngine", true);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", "a-strong-token-with-at-least-32-characters-configured");

        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> sut.validateProductionSecurity());
        assertTrue(ex.getMessage().contains("does not match the active hash in the database"));
    }

    @Test
    @DisplayName("Security: Execucao do job em producao lanca SecurityException se token for default ou fraco")
    void shouldRejectExecutionInProductionWhenTokenIsDefaultOrWeak() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", SagaTimeoutService.DEFAULT_DEV_MAINTENANCE_TOKEN);

        assertThrows(SecurityException.class, () -> sut.checkAndCompensateTimedOutSagas());
    }

    @Test
    @DisplayName("Cluster Concurrency: Pula execucao graciosamente quando outro node possui o advisory lock")
    void shouldSkipSagaTimeoutExecutionWhenAnotherClusterNodeHoldsAdvisoryLock() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query queryLock = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query queryEnable = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query queryDisable = mock(jakarta.persistence.Query.class);

        when(em.createNativeQuery("SELECT enable_system_maintenance_context(:token)")).thenReturn(queryEnable);
        when(queryEnable.setParameter(eq("token"), anyString())).thenReturn(queryEnable);
        when(queryEnable.getSingleResult()).thenReturn(true);

        when(em.createNativeQuery(contains("pg_try_advisory_xact_lock"))).thenReturn(queryLock);
        when(queryLock.setParameter(eq("lockId"), anyLong())).thenReturn(queryLock);
        when(queryLock.getSingleResult()).thenReturn(false);

        when(em.createNativeQuery("SELECT disable_system_maintenance_context()")).thenReturn(queryDisable);
        when(queryDisable.getSingleResult()).thenReturn(true);

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "postgresEngine", true);

        sut.checkAndCompensateTimedOutSagas();

        verify(activityRepository, never()).findByStatusAndUpdatedAtBefore(any(), any());
        verify(activityRepository, never()).save(any());
        verify(outboxService, never()).recordCloudEvent(any());
        verify(queryDisable).getSingleResult();
    }

    @Test
    @DisplayName("Security Sanitization: Execucao de transacao higieniza contexto auditado no finally")
    void shouldSanitizeSystemMaintenanceTokenContextInFinally() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query queryEnable = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query queryLock = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query queryDisable = mock(jakarta.persistence.Query.class);

        when(em.createNativeQuery("SELECT enable_system_maintenance_context(:token)")).thenReturn(queryEnable);
        when(queryEnable.setParameter(eq("token"), anyString())).thenReturn(queryEnable);
        when(queryEnable.getSingleResult()).thenReturn(true);

        when(em.createNativeQuery(contains("pg_try_advisory_xact_lock"))).thenReturn(queryLock);
        when(queryLock.setParameter(eq("lockId"), anyLong())).thenReturn(queryLock);
        when(queryLock.getSingleResult()).thenReturn(true);

        when(em.createNativeQuery("SELECT disable_system_maintenance_context()")).thenReturn(queryDisable);
        when(queryDisable.getSingleResult()).thenReturn(true);

        when(activityRepository.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(java.util.List.of());

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "postgresEngine", true);

        sut.checkAndCompensateTimedOutSagas();

        verify(queryEnable).getSingleResult();
        verify(queryDisable).getSingleResult();
    }

    @Test
    @DisplayName("Security: Em producao deve falhar closed se a procedure auditada de elevacao retornar false")
    void shouldFailClosedInProductionWhenAuditedElevationReturnsFalse() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query queryEnable = mock(jakarta.persistence.Query.class);
        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        when(em.createNativeQuery("SELECT enable_system_maintenance_context(:token)")).thenReturn(queryEnable);
        when(queryEnable.setParameter(eq("token"), anyString())).thenReturn(queryEnable);
        when(queryEnable.getSingleResult()).thenReturn(false);

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "postgresEngine", true);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", "a-very-strong-production-token-at-least-32-chars");

        SecurityException ex = assertThrows(SecurityException.class, () -> sut.checkAndCompensateTimedOutSagas());
        assertTrue(ex.getMessage().contains("Fail-closed: audited maintenance context elevation was rejected"));
    }

    @Test
    @DisplayName("Security: Em producao deve falhar closed se a chamada da procedure auditada lancar excecao")
    void shouldFailClosedInProductionWhenAuditedElevationThrowsException() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        org.springframework.core.env.Environment env = mock(org.springframework.core.env.Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});

        when(em.createNativeQuery("SELECT enable_system_maintenance_context(:token)"))
                .thenThrow(new RuntimeException("Database RPC failure"));

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "environment", env);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "postgresEngine", true);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", "a-very-strong-production-token-at-least-32-chars");

        SecurityException ex = assertThrows(SecurityException.class, () -> sut.checkAndCompensateTimedOutSagas());
        assertTrue(ex.getMessage().contains("Fail-closed: audited maintenance procedure unavailable"));
    }

    @Test
    @DisplayName("Security: Em ambiente nao-prod deve fazer fallback para set_config se procedure auditada falhar")
    void shouldFallbackToSetConfigInNonProductionWhenAuditedProcedureFails() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.Query queryConfig = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query queryLock = mock(jakarta.persistence.Query.class);
        jakarta.persistence.Query queryDisable = mock(jakarta.persistence.Query.class);

        when(em.createNativeQuery("SELECT enable_system_maintenance_context(:token)"))
                .thenThrow(new RuntimeException("Procedure not installed in mock"));

        when(em.createNativeQuery("SELECT set_config('app.system_maintenance_token', :token, true)"))
                .thenReturn(queryConfig);
        when(queryConfig.setParameter(eq("token"), anyString())).thenReturn(queryConfig);
        when(queryConfig.getSingleResult()).thenReturn("ok");

        when(em.createNativeQuery(contains("pg_try_advisory_xact_lock"))).thenReturn(queryLock);
        when(queryLock.setParameter(eq("lockId"), anyLong())).thenReturn(queryLock);
        when(queryLock.getSingleResult()).thenReturn(false);

        when(em.createNativeQuery("SELECT disable_system_maintenance_context()")).thenReturn(queryDisable);
        when(queryDisable.getSingleResult()).thenReturn(true);

        org.springframework.test.util.ReflectionTestUtils.setField(sut, "entityManager", em);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "postgresEngine", true);
        org.springframework.test.util.ReflectionTestUtils.setField(sut, "maintenanceToken", SagaTimeoutService.DEFAULT_DEV_MAINTENANCE_TOKEN);

        assertDoesNotThrow(() -> sut.checkAndCompensateTimedOutSagas());
        verify(queryConfig).getSingleResult();
    }
}
