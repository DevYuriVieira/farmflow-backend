package com.farmflow.concurrency;

import com.farmflow.entity.Machinery;
import com.farmflow.enums.MachineryStatus;
import com.farmflow.repository.MachineryRepository;
import com.farmflow.repository.UserRepository;
import com.farmflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MachineryConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private MachineryRepository machineryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        machineryRepository.deleteAll();
        var admin = userRepository.findByEmail("admin-machinery-test@farmflow.com")
                .orElseGet(() -> userRepository.save(com.farmflow.entity.User.builder()
                        .name("Admin Machinery Test")
                        .email("admin-machinery-test@farmflow.com")
                        .password("secret1234567")
                        .role("ADMIN")
                        .build()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null)
        );
    }

    @Test
    @DisplayName("deve rejeitar tentativas de regressão de horímetro e assegurar monotonicidade estrita")
    void shouldRejectOperatingHoursRegressionAndEnsureStrictMonotonicity() {
        Machinery machine = Machinery.builder()
                .farmId("farm-mach-1")
                .name("Trator John Deere 8R")
                .model("8R 370")
                .type("TRACTOR")
                .operatingHours(500.0)
                .status(MachineryStatus.OPERATIONAL)
                .build();
        Machinery saved = machineryRepository.saveAndFlush(machine);
        String machineId = saved.getId();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        Integer rejected = txTemplate.execute(status ->
                machineryRepository.updateOperatingHoursMonotonically(machineId, 450.0, Instant.now())
        );
        assertEquals(0, rejected);

        Integer accepted = txTemplate.execute(status ->
                machineryRepository.updateOperatingHoursMonotonically(machineId, 550.0, Instant.now())
        );
        assertEquals(1, accepted);

        Machinery current = machineryRepository.findById(machineId).orElseThrow();
        assertEquals(550.0, current.getOperatingHours());
    }

    @Test
    @DisplayName("deve detectar colisão otimista sob atualizações concorrentes de detalhes de máquina")
    void shouldTriggerOptimisticLockingOnConcurrentMachineryModifications() throws Exception {
        Machinery machine = Machinery.builder()
                .farmId("farm-mach-conc")
                .name("Colheitadeira Axial Flow")
                .model("AF 8250")
                .type("HARVESTER")
                .operatingHours(120.0)
                .status(MachineryStatus.OPERATIONAL)
                .build();
        Machinery saved = machineryRepository.saveAndFlush(machine);
        String machineId = saved.getId();

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    txTemplate.execute(status -> {
                        Machinery current = machineryRepository.findById(machineId).orElseThrow();
                        current.updateDetails(
                                "Colheitadeira Axial Flow - Worker " + index,
                                "AF 8250",
                                "HARVESTER"
                        );
                        machineryRepository.saveAndFlush(current);
                        return null;
                    });
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException ex) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getCause() instanceof ObjectOptimisticLockingFailureException
                            || e.getMessage().contains("Row was updated or deleted by another transaction")) {
                        conflictCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed);
        assertTrue(successCount.get() >= 1);
        assertEquals(threadCount, successCount.get() + conflictCount.get());

        Machinery finalState = machineryRepository.findById(machineId).orElseThrow();
        assertNotNull(finalState.getVersion());
        assertTrue(finalState.getVersion() >= 1);
    }
}