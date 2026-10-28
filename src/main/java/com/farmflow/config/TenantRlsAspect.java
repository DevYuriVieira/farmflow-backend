package com.farmflow.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantRlsAspect {

    private static final Logger log = LoggerFactory.getLogger(TenantRlsAspect.class);
    private static final String TENANT_RLS_RESOURCE_KEY = "TENANT_RLS_SESSION_APPLIED";

    @PersistenceContext
    private EntityManager entityManager;

    private volatile Boolean postgresEngine;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || within(@org.springframework.stereotype.Service *)")
    public Object enforceTenantRls(ProceedingJoinPoint joinPoint) throws Throwable {
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isBlank() && TransactionSynchronizationManager.isActualTransactionActive()) {
            Object appliedTenant = TransactionSynchronizationManager.getResource(TENANT_RLS_RESOURCE_KEY);
            if (appliedTenant != null && !tenantId.equals(appliedTenant)) {
                throw new SecurityException("Cross-tenant transaction contamination detected: active transaction already bound to tenant "
                        + appliedTenant + ", refusing to rebind to tenant " + tenantId);
            }
            if (appliedTenant == null) {
                applyTenantRls(tenantId);
            }
        }
        return joinPoint.proceed();
    }

    private void applyTenantRls(String tenantId) {
        boolean isPg = isPostgreSql();
        try {
            entityManager.createNativeQuery("SELECT set_config('app.current_farm_id', :farmId, true)")
                    .setParameter("farmId", tenantId)
                    .getSingleResult();
            if (!TransactionSynchronizationManager.hasResource(TENANT_RLS_RESOURCE_KEY)) {
                TransactionSynchronizationManager.bindResource(TENANT_RLS_RESOURCE_KEY, tenantId);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (TransactionSynchronizationManager.hasResource(TENANT_RLS_RESOURCE_KEY)) {
                            TransactionSynchronizationManager.unbindResource(TENANT_RLS_RESOURCE_KEY);
                        }
                    }
                });
            }
            log.debug("Configured PostgreSQL RLS session app.current_farm_id={}", tenantId);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            if (isPg) {
                log.error("CRITICAL: Failed to enforce PostgreSQL RLS session for tenant {}: {}", tenantId, e.getMessage());
                throw new SecurityException("Fail-closed: Failed to set PostgreSQL RLS session context for tenant " + tenantId, e);
            }
            log.trace("Tenant RLS set_config skipped or unsupported dialect: {}", e.getMessage());
        }
    }

    private boolean isPostgreSql() {
        if (postgresEngine == null) {
            synchronized (this) {
                if (postgresEngine == null) {
                    try {
                        Session session = entityManager.unwrap(Session.class);
                        session.doWork(conn -> {
                            String productName = conn.getMetaData().getDatabaseProductName();
                            postgresEngine = productName != null && productName.toLowerCase().contains("postgres");
                        });
                    } catch (Exception e) {
                        log.warn("Failed to detect database engine metadata; assuming fail-closed PostgreSQL engine: {}", e.getMessage());
                        return true;
                    }
                }
            }
        }
        return Boolean.TRUE.equals(postgresEngine);
    }
}

