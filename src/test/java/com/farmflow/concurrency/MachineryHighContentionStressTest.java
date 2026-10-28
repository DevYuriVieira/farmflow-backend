package com.farmflow.concurrency;

import com.farmflow.entity.Machinery;
import com.farmflow.enums.MachineryStatus;
import com.farmflow.repository.MachineryRepository;
import com.farmflow.repository.UserRepository;
import com.farmflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Concorrência de Alta Contenção: Horímetro Monotônico & Atomic In-Place sob 100 Virtual Threads")
public class MachineryHighContentionStressTest extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MachineryHighContentionStressTest.class);

    @Autowired
    private MachineryRepository machineryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        machineryRepository.deleteAll();
        var admin = userRepository.findByEmail("admin-stress-test@farmflow.com")
                .orElseGet(() -> userRepository.save(com.farmflow.entity.User.builder()
                        .name("Admin Stress Test")
                        .email("admin-stress-test@farmflow.com")
                        .password("secret1234567")
                        .role("ADMIN")
                        .build()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null)
        );
    }

    @Test
    @DisplayName("Alta Contenção: 100 Virtual Threads simultâneas incrementando o mesmo horímetro com zero lost updates")
    void shouldAccumulateOperatingHoursUnder100ConcurrentVirtualThreadsWithoutLostUpdate() throws Exception {
        int virtualThreadsCount = 100;
        double incrementDelta = 0.25;
        double initialHours = 250.00;

        Machinery machine = Machinery.builder()
                .farmId("farm-stress-01")
                .name("Pulverizador Autopropelido Uniport")
                .model("Uniport 3030")
                .type("SPRAYER")
                .operatingHours(initialHours)
                .status(MachineryStatus.OPERATIONAL)
                .build();
        Machinery saved = machineryRepository.saveAndFlush(machine);
        String machineId = saved.getId();
        long initialVersion = saved.getVersion() != null ? saved.getVersion() : 0L;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch readyLatch = new CountDownLatch(virtualThreadsCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(virtualThreadsCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failureCounter = new AtomicInteger(0);
        List<Long> latenciesNanos = Collections.synchronizedList(new ArrayList<>());

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        for (int i = 0; i < virtualThreadsCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startGate.await();
                    long start = System.nanoTime();

                    Integer rowsUpdated = txTemplate.execute(status ->
                            machineryRepository.incrementOperatingHoursAtomic(
                                    machineId,
                                    "farm-stress-01",
                                    incrementDelta,
                                    Instant.now()
                            )
                    );

                    long duration = System.nanoTime() - start;
                    latenciesNanos.add(duration);

                    if (rowsUpdated != null && rowsUpdated == 1) {
                        successCounter.incrementAndGet();
                    } else {
                        failureCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Concurrent update failed", e);
                    failureCounter.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(readyLatch.await(10, TimeUnit.SECONDS));

        long benchmarkStart = System.currentTimeMillis();
        startGate.countDown();

        boolean finishedInTime = doneLatch.await(30, TimeUnit.SECONDS);
        long totalDurationMs = System.currentTimeMillis() - benchmarkStart;
        executor.shutdown();

        assertTrue(finishedInTime);
        assertEquals(0, failureCounter.get());
        assertEquals(virtualThreadsCount, successCounter.get());

        Machinery finalMachinery = machineryRepository.findById(machineId).orElseThrow();
        double expectedFinalHours = initialHours + (virtualThreadsCount * incrementDelta);
        long expectedFinalVersion = initialVersion + virtualThreadsCount;

        assertEquals(expectedFinalHours, finalMachinery.getOperatingHours(), 0.0001);
        assertEquals(expectedFinalVersion, finalMachinery.getVersion());

        // Métricas de Latência
        latenciesNanos.sort(Long::compareTo);
        long p50Ms = TimeUnit.NANOSECONDS.toMillis(latenciesNanos.get((int) (latenciesNanos.size() * 0.50)));
        long p95Ms = TimeUnit.NANOSECONDS.toMillis(latenciesNanos.get((int) (latenciesNanos.size() * 0.95)));
        long p99Ms = TimeUnit.NANOSECONDS.toMillis(latenciesNanos.get((int) (latenciesNanos.size() * 0.99)));
        double throughputOpsSec = (virtualThreadsCount / (double) totalDurationMs) * 1000.0;

        log.info("=== RESULTADOS DO ESTRESSE DE ALTA CONTENÇÃO (100 VIRTUAL THREADS) ===");
        log.info("Duração Total: {}ms | Throughput: {} ops/sec", totalDurationMs, String.format("%.2f", throughputOpsSec));
        log.info("Latência p50: {}ms | p95: {}ms | p99: {}ms", p50Ms, p95Ms, p99Ms);
        log.info("Horímetro Inicial: {} -> Final: {} (Esperado: {})", initialHours, finalMachinery.getOperatingHours(), expectedFinalHours);
        log.info("Versão Inicial: {} -> Final: {}", initialVersion, finalMachinery.getVersion());

        assertTrue(p95Ms < 2000, "p95 sob 100 threads em concorrência atômica deve ser inferior a 2000ms");
    }
}
