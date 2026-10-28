package com.farmflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Explicit transaction management configuration ensuring that Spring's
 * TransactionInterceptor executes with higher precedence (order = LOWEST_PRECEDENCE - 50)
 * than TenantRlsAspect (order = LOWEST_PRECEDENCE).
 *
 * This guarantees that every @Transactional boundary opens its physical database transaction
 * BEFORE TenantRlsAspect verifies the transaction state and sets the PostgreSQL session context.
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 50)
public class TransactionManagementConfig {
}
