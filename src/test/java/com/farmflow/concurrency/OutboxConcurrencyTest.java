package com.farmflow.concurrency;

import com.farmflow.entity.OutboxMessage;
import com.farmflow.enums.OutboxStatus;
import com.farmflow.repository.OutboxRepository;
import com.farmflow.service.OutboxService;
import com.farmflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OutboxConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
    }

    @Test
    void shouldNeverClaimDuplicateMessagesAcrossConcurrentRelayInstances() throws Exception {
        int totalMessages = 20;
        int batchSize = 10;
        for (int i = 0; i < totalMessages; i++) {
            OutboxMessage msg = OutboxMessage.builder()
                    .eventType("TelemetryAlert")
                    .routingKey("telemetry.alert.critical")
                    .payload("{\"readingId\":\"r-" + i + "\"}")
                    .correlationId("corr-" + i)
                    .traceParent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                    .occurredOn(Instant.now().minusSeconds(totalMessages - i))
                    .status(OutboxStatus.PENDING)
                    .attempts(0)
                    .build();
            outboxRepository.save(msg);
        }

        int concurrency = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CyclicBarrier barrier = new CyclicBarrier(concurrency);

        Callable<List<OutboxMessage>> claimTask = () -> {
            barrier.await();
            return outboxService.fetchAndClaimBatch(3, batchSize);
        };

        Future<List<OutboxMessage>> future1 = executor.submit(claimTask);
        Future<List<OutboxMessage>> future2 = executor.submit(claimTask);

        List<OutboxMessage> batch1 = future1.get(10, TimeUnit.SECONDS);
        List<OutboxMessage> batch2 = future2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertNotNull(batch1);
        assertNotNull(batch2);

        assertTrue(!batch1.isEmpty() || !batch2.isEmpty(), "At least one concurrent batch must claim messages");

        Set<String> ids1 = batch1.stream().map(OutboxMessage::getId).collect(Collectors.toSet());
        Set<String> ids2 = batch2.stream().map(OutboxMessage::getId).collect(Collectors.toSet());

        Set<String> intersection = new HashSet<>(ids1);
        intersection.retainAll(ids2);
        assertTrue(intersection.isEmpty(), "Concurrent instances must never claim overlapping messages: " + intersection);

        List<OutboxMessage> remainingBatch = outboxService.fetchAndClaimBatch(3, batchSize);
        Set<String> remainingIds = remainingBatch.stream().map(OutboxMessage::getId).collect(Collectors.toSet());

        Set<String> allClaimedIds = new HashSet<>(ids1);
        allClaimedIds.addAll(ids2);
        allClaimedIds.addAll(remainingIds);

        assertEquals(totalMessages, allClaimedIds.size(), "Total unique claimed messages must match total messages inserted");

        List<OutboxMessage> allClaimed = outboxRepository.findAll();
        for (OutboxMessage msg : allClaimed) {
            assertEquals(OutboxStatus.PROCESSING, msg.getStatus());
            assertNotNull(msg.getNextAttemptAt());
            assertTrue(msg.getNextAttemptAt().isAfter(Instant.now()));
        }
    }

    @Test
    void shouldNotReclaimMessagesUnderActiveLease() {
        for (int i = 0; i < 5; i++) {
            OutboxMessage msg = OutboxMessage.builder()
                    .eventType("PestDetected")
                    .routingKey("pest.detected.warning")
                    .payload("{\"pestId\":\"p-" + i + "\"}")
                    .occurredOn(Instant.now().minusSeconds(5 - i))
                    .status(OutboxStatus.PENDING)
                    .attempts(0)
                    .build();
            outboxRepository.save(msg);
        }

        List<OutboxMessage> firstClaim = outboxService.fetchAndClaimBatch(3, 5);
        assertEquals(5, firstClaim.size());

        List<OutboxMessage> secondClaim = outboxService.fetchAndClaimBatch(3, 5);
        assertTrue(secondClaim.isEmpty(), "Messages under active lease must not be claimed by another relay query");
    }

    @Test
    void shouldReclaimMessagesAfterLeaseExpiration() {
        OutboxMessage msg = OutboxMessage.builder()
                .eventType("MachineryMaintenanceScheduled")
                .routingKey("machinery.maintenance.scheduled")
                .payload("{\"machineryId\":\"m-1\"}")
                .occurredOn(Instant.now().minusSeconds(120))
                .status(OutboxStatus.PROCESSING)
                .nextAttemptAt(Instant.now().minusSeconds(10))
                .attempts(1)
                .build();
        outboxRepository.save(msg);

        List<OutboxMessage> reclaimed = outboxService.fetchAndClaimBatch(3, 10);
        assertEquals(1, reclaimed.size());
        assertEquals(msg.getId(), reclaimed.getFirst().getId());
        assertEquals(OutboxStatus.PROCESSING, reclaimed.getFirst().getStatus());
        assertTrue(reclaimed.getFirst().getNextAttemptAt().isAfter(Instant.now()));
    }
}
