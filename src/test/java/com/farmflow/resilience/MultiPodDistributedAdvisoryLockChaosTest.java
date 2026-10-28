package com.farmflow.resilience;

import com.farmflow.entity.OutboxMessage;
import com.farmflow.enums.OutboxStatus;
import com.farmflow.repository.OutboxRepository;
import com.farmflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Resiliência & Chaos Testing Multi-Pod: Advisory Locks Transacionais & Outbox Concorrente")
public class MultiPodDistributedAdvisoryLockChaosTest extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(MultiPodDistributedAdvisoryLockChaosTest.class);
    private static final long SAGA_LOCK_ID = 8492049182390192L;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private boolean isPostgreSql() {
        try (Connection conn = dataSource.getConnection()) {
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            return dbProduct != null && dbProduct.toLowerCase().contains("postgres");
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(isPostgreSql(), "PostgreSQL transaction advisory locks require PostgreSQL engine");
    }

    @Test
    @DisplayName("Chaos Multi-Pod: Simulação de 3 nós com lock transacional e recuperação instantânea pós-crash do líder")
    void shouldCoordinateClusterViaAdvisoryXactLockAndHandlePodCrashCleanly() throws Exception {
        // Conexão física 1 = Pod 1 (Líder inicial)
        // Conexão física 2 = Pod 2 (Réplica concorrente)
        // Conexão física 3 = Pod 3 (Réplica concorrente)

        try (Connection pod1Conn = dataSource.getConnection();
             Connection pod2Conn = dataSource.getConnection();
             Connection pod3Conn = dataSource.getConnection()) {

            pod1Conn.setAutoCommit(false);
            pod2Conn.setAutoCommit(false);
            pod3Conn.setAutoCommit(false);

            boolean pod1Acquired = tryAcquireAdvisoryLock(pod1Conn, SAGA_LOCK_ID);
            assertTrue(pod1Acquired);

            long pod2Start = System.nanoTime();
            boolean pod2Acquired = tryAcquireAdvisoryLock(pod2Conn, SAGA_LOCK_ID);
            long pod2DurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pod2Start);

            long pod3Start = System.nanoTime();
            boolean pod3Acquired = tryAcquireAdvisoryLock(pod3Conn, SAGA_LOCK_ID);
            long pod3DurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pod3Start);

            assertFalse(pod2Acquired);
            assertFalse(pod3Acquired);
            assertTrue(pod2DurationMs < 50);
            assertTrue(pod3DurationMs < 50);

            pod1Conn.rollback();
            pod1Conn.close();

            boolean pod2AcquiredPostCrash = tryAcquireAdvisoryLock(pod2Conn, SAGA_LOCK_ID);
            assertTrue(pod2AcquiredPostCrash);

            boolean pod3AcquiredWhilePod2Holds = tryAcquireAdvisoryLock(pod3Conn, SAGA_LOCK_ID);
            assertFalse(pod3AcquiredWhilePod2Holds);

            pod2Conn.commit();
            pod3Conn.rollback();
        }
    }

    @Test
    @DisplayName("Outbox Multi-Pod: 3 Workers concorrentes processando fila com FOR UPDATE SKIP LOCKED sem colisão")
    void shouldDistributeOutboxProcessingAcrossMultiPodsWithoutCollisionUsingSkipLocked() throws Exception {
        int totalMessages = 60;
        int batchSize = 20;
        int workerCount = 3;

        outboxRepository.deleteAll();

        List<OutboxMessage> messages = new ArrayList<>();
        for (int i = 0; i < totalMessages; i++) {
            messages.add(OutboxMessage.builder()
                    .routingKey("activity.created")
                    .eventType("com.farmflow.activity.created.v1")
                    .payload("{\"index\": " + i + "}")
                    .occurredOn(Instant.now().minusSeconds(totalMessages - i))
                    .status(OutboxStatus.PENDING)
                    .attempts(0)
                    .build());
        }
        outboxRepository.saveAllAndFlush(messages);

        assertEquals(totalMessages, outboxRepository.countPendingBacklog());

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(workerCount);

        Set<String> allClaimedIds = Collections.synchronizedSet(new HashSet<>());
        Map<Integer, List<String>> workerClaimedMap = new ConcurrentHashMap<>();

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (int w = 0; w < workerCount; w++) {
            final int workerId = w;
            executor.submit(() -> {
                try {
                    startGate.await();
                    TransactionTemplate tx = new TransactionTemplate(transactionManager, def);
                    tx.execute(status -> {
                        List<OutboxMessage> claimed = outboxRepository.findPendingForClaim(
                                Instant.now(),
                                3,
                                batchSize
                        );
                        List<String> ids = claimed.stream().map(OutboxMessage::getId).toList();
                        workerClaimedMap.put(workerId, ids);
                        allClaimedIds.addAll(ids);

                        claimed.forEach(m -> m.setStatus(OutboxStatus.PROCESSING));
                        outboxRepository.saveAll(claimed);
                        return null;
                    });
                } catch (Exception e) {
                    log.error("Worker {} failed in outbox claiming", workerId, e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Os 3 workers concorrentes devem concluir o claiming em menos de 15 segundos");

        // 3. Asseverações Estritas de Não-Colisão
        assertEquals(batchSize, workerClaimedMap.get(0).size(), "Worker 0 deve capturar exatamente 20 mensagens");
        assertEquals(batchSize, workerClaimedMap.get(1).size(), "Worker 1 deve capturar exatamente 20 mensagens");
        assertEquals(batchSize, workerClaimedMap.get(2).size(), "Worker 2 deve capturar exatamente 20 mensagens");

        assertEquals(totalMessages, allClaimedIds.size(),
                "ZERO COLISÃO: A união de todas as mensagens capturadas deve ser exatamente 60 sem duplicação!");
    }

    private boolean tryAcquireAdvisoryLock(Connection conn, long lockId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT pg_try_advisory_xact_lock(?)")) {
            ps.setLong(1, lockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        }
        return false;
    }
}
