package com.farmflow.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantRlsAspectTest {

    private static final String TENANT_RLS_RESOURCE_KEY = "TENANT_RLS_SESSION_APPLIED";

    @Mock
    private EntityManager entityManager;

    @Mock
    private Session session;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private Query nativeQuery;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @InjectMocks
    private TenantRlsAspect sut;

    @BeforeEach
    void setUp() throws Throwable {
        TenantContext.clear();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        when(entityManager.unwrap(Session.class)).thenReturn(session);
        doAnswer(invocation -> {
            Work work = invocation.getArgument(0);
            work.execute(connection);
            return null;
        }).when(session).doWork(any(Work.class));
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getSingleResult()).thenReturn("ok");

        when(joinPoint.proceed()).thenReturn("success");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (TransactionSynchronizationManager.hasResource(TENANT_RLS_RESOURCE_KEY)) {
            TransactionSynchronizationManager.unbindResource(TENANT_RLS_RESOURCE_KEY);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("Anti-Contaminação: Rejeita rebinding para outro tenant na mesma transação física")
    void shouldThrowSecurityExceptionOnCrossTenantContamination() {
        TransactionSynchronizationManager.bindResource(TENANT_RLS_RESOURCE_KEY, "tenant-alpha");
        TenantContext.setTenantId("tenant-beta");

        SecurityException ex = assertThrows(SecurityException.class, () -> sut.enforceTenantRls(joinPoint));
        assertTrue(ex.getMessage().contains("Cross-tenant transaction contamination detected"));
        assertTrue(ex.getMessage().contains("tenant-alpha"));
        assertTrue(ex.getMessage().contains("tenant-beta"));
    }

    @Test
    @DisplayName("Deduplicação: Não reinvoca set_config se a transação física já possui o mesmo tenant vinculado")
    void shouldDeduplicateTenantSettingWithinSameTransaction() throws Throwable {
        TransactionSynchronizationManager.bindResource(TENANT_RLS_RESOURCE_KEY, "tenant-alpha");
        TenantContext.setTenantId("tenant-alpha");

        Object result = sut.enforceTenantRls(joinPoint);

        assertEquals("success", result);
        verify(entityManager, never()).createNativeQuery(anyString());
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Fail-Closed: Lança SecurityException se set_config falhar em engine PostgreSQL")
    void shouldThrowSecurityExceptionWhenSetConfigFailsOnPostgreSql() {
        TenantContext.setTenantId("tenant-alpha");
        when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("Connection reset"));

        SecurityException ex = assertThrows(SecurityException.class, () -> sut.enforceTenantRls(joinPoint));
        assertTrue(ex.getMessage().contains("Fail-closed: Failed to set PostgreSQL RLS session context"));
    }

    @Test
    @DisplayName("Fail-Closed: Assume engine PostgreSQL se a detecção de metadados falhar com erro")
    void shouldAssumePostgreSqlFailClosedWhenMetadataThrowsException() throws Throwable {
        TenantContext.setTenantId("tenant-alpha");
        doThrow(new RuntimeException("JDBC driver metadata error")).when(session).doWork(any(Work.class));
        when(nativeQuery.getSingleResult()).thenThrow(new RuntimeException("SQL syntax error"));

        SecurityException ex = assertThrows(SecurityException.class, () -> sut.enforceTenantRls(joinPoint));
        assertTrue(ex.getMessage().contains("Fail-closed"));
    }

    @Test
    @DisplayName("Sucesso: Aplica tenant RLS e vincula o recurso transacional perfeitamente")
    void shouldApplyTenantRlsAndBindResourceSuccessfully() throws Throwable {
        TenantContext.setTenantId("tenant-bravo");

        Object result = sut.enforceTenantRls(joinPoint);

        assertEquals("success", result);
        verify(entityManager).createNativeQuery(contains("app.current_farm_id"));
        verify(nativeQuery).setParameter("farmId", "tenant-bravo");
        assertTrue(TransactionSynchronizationManager.hasResource(TENANT_RLS_RESOURCE_KEY));
        assertEquals("tenant-bravo", TransactionSynchronizationManager.getResource(TENANT_RLS_RESOURCE_KEY));
    }
}
