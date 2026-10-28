package com.farmflow.service;

import com.farmflow.config.FarmFlowMetrics;
import com.farmflow.entity.FarmActivity;
import com.farmflow.entity.SagaAuditLog;
import com.farmflow.enums.ActivityStatus;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.repository.ActivityRepository;
import com.farmflow.repository.SagaAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!supply & !iot & !fleet")
public class SagaTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutService.class);
    private static final Duration SAGA_TIMEOUT_DURATION = Duration.ofMinutes(15);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private TransactionTemplate transactionTemplate;

    public static final String DEFAULT_DEV_MAINTENANCE_TOKEN = "farmflow-internal-system-maintenance-token-v1";

    @Autowired(required = false)
    private org.springframework.core.env.Environment environment;

    @Value("${farmflow.system.maintenance-token:farmflow-internal-system-maintenance-token-v1}")
    private String maintenanceToken;

    private volatile Boolean postgresEngine;

    private final ActivityRepository activityRepository;
    private final SagaAuditLogRepository sagaAuditLogRepository;
    private final OutboxService outboxService;
    private final FarmFlowMetrics farmFlowMetrics;

    @jakarta.annotation.PostConstruct
    public void validateProductionSecurity() {
        boolean isProd = isProductionEnvironment();
        if (isProd) {
            if (maintenanceToken == null || maintenanceToken.isBlank()
                    || DEFAULT_DEV_MAINTENANCE_TOKEN.equals(maintenanceToken)
                    || maintenanceToken.length() < 32) {
                throw new IllegalStateException("CRITICAL SECURITY VIOLATION: Production profile active with missing, default, or weak farmflow.system.maintenance-token! Token must be injected via secret store and >= 32 characters.");
            }
        }
        if (isProd && entityManager != null && isPostgreSql()) {
            validateDatabaseTokenHash();
        }
    }

    private void validateDatabaseTokenHash() {
        try {
            Boolean defaultAccepted = (Boolean) entityManager.createNativeQuery(
                    "SELECT verify_system_maintenance_token(:checkToken)")
                    .setParameter("checkToken", DEFAULT_DEV_MAINTENANCE_TOKEN)
                    .getSingleResult();
            if (Boolean.TRUE.equals(defaultAccepted)) {
                throw new IllegalStateException("CRITICAL SECURITY VIOLATION: Database system_maintenance_auth still contains the default development token hash! The token must be rotated via rotate_system_maintenance_token before running in production.");
            }
            Boolean configuredAccepted = (Boolean) entityManager.createNativeQuery(
                    "SELECT verify_system_maintenance_token(:token)")
                    .setParameter("token", maintenanceToken)
                    .getSingleResult();
            if (!Boolean.TRUE.equals(configuredAccepted)) {
                throw new IllegalStateException("CRITICAL SECURITY VIOLATION: Configured farmflow.system.maintenance-token does not match the active hash in the database!");
            }
        } catch (IllegalStateException ise) {
            throw ise;
        } catch (Exception ex) {
            log.error("CRITICAL SECURITY VIOLATION: Database token hash verification failed during production startup: {}", ex.getMessage(), ex);
            throw new IllegalStateException("CRITICAL SECURITY VIOLATION: Database token hash verification failed during production startup: " + ex.getMessage(), ex);
        }
    }

    private static final long SAGA_TIMEOUT_JOB_ADVISORY_LOCK_ID = 8492049182390192L;

    @Scheduled(fixedDelay = 60000)
    public void checkAndCompensateTimedOutSagas() {
        executeInTransaction(() -> {
            if (isPostgreSql()) {
                Boolean acquired = tryAcquireAdvisoryLock(SAGA_TIMEOUT_JOB_ADVISORY_LOCK_ID);
                if (Boolean.FALSE.equals(acquired)) {
                    log.debug("Another cluster instance holds the saga timeout advisory lock. Skipping execution gracefully.");
                    return null;
                }
            }

            Instant cutoff = Instant.now().minus(SAGA_TIMEOUT_DURATION);
            List<FarmActivity> timedOutActivities = activityRepository
                    .findByStatusAndUpdatedAtBefore(ActivityStatus.PENDING_CONFIRMATION, cutoff);

            if (timedOutActivities == null || timedOutActivities.isEmpty()) {
                return null;
            }

            log.warn("Detected {} activities in PENDING_CONFIRMATION exceeding timeout ({}). Triggering automatic compensation.",
                    timedOutActivities.size(), SAGA_TIMEOUT_DURATION);

            for (FarmActivity activity : timedOutActivities) {
                try {
                    compensateSingleActivity(activity);
                } catch (Exception e) {
                    log.error("Failed to compensate timed-out activity {}: {}", activity.getId(), e.getMessage(), e);
                }
            }
            return null;
        });
    }

    private Boolean tryAcquireAdvisoryLock(long lockId) {
        try {
            return (Boolean) entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(:lockId)")
                    .setParameter("lockId", lockId)
                    .getSingleResult();
        } catch (Exception e) {
            log.trace("Advisory lock not applicable or dialect unsupported; relying on optimistic locking: {}", e.getMessage());
            return true;
        }
    }

    private void compensateSingleActivity(FarmActivity activity) {
        String reason = "Saga confirmation timeout expired (> " + SAGA_TIMEOUT_DURATION.toMinutes() + "m)";
        activity.compensateInputReservation(reason);
        activityRepository.save(activity);

        SagaAuditLog auditLog = SagaAuditLog.builder()
                .farmId(activity.getFarmId())
                .activityId(activity.getId())
                .sagaType("ACTIVITY_INPUT_RESERVATION")
                .transitionFrom(ActivityStatus.PENDING_CONFIRMATION.name())
                .transitionTo(ActivityStatus.IN_PROGRESS.name())
                .reason(reason)
                .correlationId(UUID.randomUUID().toString())
                .executedAt(Instant.now())
                .build();
        sagaAuditLogRepository.save(auditLog);

        CloudEvent<Map<String, Object>> event = CloudEvent.of(
                "https://farmflow.com/core-service",
                "com.farmflow.activity.compensated.v1",
                activity.getFarmId(),
                UUID.randomUUID().toString(),
                Map.of(
                        "activityId", activity.getId(),
                        "farmId", activity.getFarmId(),
                        "reason", reason,
                        "status", ActivityStatus.IN_PROGRESS.name()
                )
        );
        outboxService.recordCloudEvent(event);

        farmFlowMetrics.incrementSagaTimeout("ActivityInputReservation");
        log.info("Activity {} automatically compensated via saga timeout handler", activity.getId());
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        TransactionTemplate template = this.transactionTemplate;
        if (template == null && this.transactionManager != null) {
            template = new TransactionTemplate(this.transactionManager);
        }
        if (template != null) {
            return template.execute(status -> {
                enableSystemMaintenanceContext();
                try {
                    return action.get();
                } finally {
                    clearSystemMaintenanceContext();
                }
            });
        } else {
            enableSystemMaintenanceContext();
            try {
                return action.get();
            } finally {
                clearSystemMaintenanceContext();
            }
        }
    }

    private void clearSystemMaintenanceContext() {
        if (entityManager == null) {
            return;
        }
        try {
            if (isPostgreSql()) {
                try {
                    entityManager.createNativeQuery("SELECT disable_system_maintenance_context()")
                            .getSingleResult();
                    log.trace("Sanitized PostgreSQL system maintenance RLS bypass via disable_system_maintenance_context()");
                    return;
                } catch (Exception e) {
                    log.trace("disable_system_maintenance_context() unavailable, falling back to set_config: {}", e.getMessage());
                }
            }
            entityManager.createNativeQuery("SELECT set_config('app.system_maintenance_token', '', true)")
                    .getSingleResult();
            log.trace("Sanitized PostgreSQL system maintenance RLS bypass token for connection return");
        } catch (Exception e) {
            log.trace("Suppressed clearing of system maintenance context: {}", e.getMessage());
        }
    }

    private void enableSystemMaintenanceContext() {
        if (entityManager == null) {
            return;
        }
        boolean isPg = isPostgreSql();
        try {
            if (isProductionEnvironment()) {
                if (maintenanceToken == null || maintenanceToken.isBlank()
                        || DEFAULT_DEV_MAINTENANCE_TOKEN.equals(maintenanceToken)
                        || maintenanceToken.length() < 32) {
                    throw new SecurityException("CRITICAL: Saga timeout job cannot operate in production with missing, default, or weak maintenance token");
                }
            }
            String token = (maintenanceToken != null && !maintenanceToken.isBlank())
                    ? maintenanceToken
                    : DEFAULT_DEV_MAINTENANCE_TOKEN;

            if (isPg) {
                try {
                    Boolean enabled = (Boolean) entityManager.createNativeQuery("SELECT enable_system_maintenance_context(:token)")
                            .setParameter("token", token)
                            .getSingleResult();
                    if (Boolean.TRUE.equals(enabled)) {
                        log.debug("Audited and configured PostgreSQL system maintenance RLS context for saga timeout job");
                        return;
                    } else {
                        if (isProductionEnvironment()) {
                            throw new SecurityException("Fail-closed: audited maintenance context elevation was rejected; unaudited fallback is disabled in production");
                        }
                        log.warn("enable_system_maintenance_context returned false, falling back to unaudited set_config (non-production only)");
                    }
                } catch (SecurityException se) {
                    throw se;
                } catch (Exception e) {
                    if (isProductionEnvironment()) {
                        throw new SecurityException("Fail-closed: audited maintenance procedure unavailable; unaudited fallback is disabled in production", e);
                    }
                    log.warn("enable_system_maintenance_context() procedure unavailable, falling back to unaudited set_config (non-production only): {}", e.getMessage());
                }
            }

            entityManager.createNativeQuery("SELECT set_config('app.system_maintenance_token', :token, true)")
                    .setParameter("token", token)
                    .getSingleResult();
            log.debug("Configured PostgreSQL system maintenance RLS bypass token for saga timeout job");
        } catch (SecurityException se) {
            log.error("CRITICAL: Failed to apply system RLS maintenance token for saga timeout job: {}", se.getMessage());
            throw se;
        } catch (Exception e) {
            if (isPg || isProductionEnvironment()) {
                log.error("CRITICAL: Failed to apply system RLS maintenance token for saga timeout job: {}", e.getMessage());
                throw new SecurityException("Fail-closed: Saga timeout job cannot operate under RLS without valid maintenance token context", e);
            }
            log.trace("System maintenance RLS bypass not applicable or dialect unsupported: {}", e.getMessage());
        }
    }

    private boolean isProductionEnvironment() {
        if (environment == null) {
            return false;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPostgreSql() {
        if (postgresEngine == null) {
            synchronized (this) {
                if (postgresEngine == null) {
                    if (entityManager == null) {
                        return false;
                    }
                    try {
                        org.hibernate.Session session = entityManager.unwrap(org.hibernate.Session.class);
                        session.doWork(conn -> {
                            String productName = conn.getMetaData().getDatabaseProductName();
                            postgresEngine = productName != null && productName.toLowerCase().contains("postgres");
                        });
                    } catch (Exception e) {
                        log.warn("Failed to detect database engine metadata in SagaTimeoutService; assuming fail-closed PostgreSQL engine: {}", e.getMessage());
                        return true;
                    }
                }
            }
        }
        return Boolean.TRUE.equals(postgresEngine);
    }
}
