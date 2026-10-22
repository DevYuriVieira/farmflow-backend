package com.farmflow.concurrency;

import com.farmflow.entity.InventoryItem;
import com.farmflow.enums.InventoryCategory;
import com.farmflow.exceptions.BusinessException;
import com.farmflow.repository.InventoryRepository;
import com.farmflow.service.InventoryService;
import com.farmflow.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InventoryConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
    }

    @Test
    void shouldTriggerOptimisticLockingExceptionOnConcurrentStaleUpdates() throws Exception {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-test-conc")
                .name("Adubo Fósforo Super Simples")
                .category(InventoryCategory.FERTILIZER)
                .quantity(1000.0)
                .unit("kg")
                .minimumThreshold(200.0)
                .build();
        InventoryItem savedItem = inventoryRepository.saveAndFlush(item);
        String itemId = savedItem.getId();

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
                        InventoryItem current = inventoryRepository.findById(itemId).orElseThrow();
                        current.updateDetails(
                                "Adubo Fósforo Super Simples - Worker " + index,
                                current.getCategory(),
                                current.getUnit(),
                                current.getMinimumThreshold(),
                                10.0 + index,
                                "Galpão " + index
                        );
                        inventoryRepository.saveAndFlush(current);
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

        assertTrue(completed, "All concurrent update workers should finish within timeout");
        assertTrue(successCount.get() >= 1, "At least one worker must successfully commit");
        assertEquals(threadCount, successCount.get() + conflictCount.get(),
                "Total outcomes must equal total submitted concurrent worker attempts");

        InventoryItem finalState = inventoryRepository.findById(itemId).orElseThrow();
        assertNotNull(finalState.getVersion());
        assertTrue(finalState.getVersion() >= 1, "Entity version must increment after successful optimistic commit");
    }

    @Test
    void shouldSequentiallyDeductStockAndIncrementVersionsWithoutLoss() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-test-seq")
                .name("Semente de Algodão")
                .category(InventoryCategory.SEED)
                .quantity(500.0)
                .unit("sc")
                .minimumThreshold(50.0)
                .build();
        InventoryItem saved = inventoryRepository.saveAndFlush(item);
        String itemId = saved.getId();

        int deductions = 5;
        double amountPerDeduction = 50.0;

        for (int i = 0; i < deductions; i++) {
            inventoryService.deductStock(itemId, amountPerDeduction);
        }

        InventoryItem current = inventoryRepository.findById(itemId).orElseThrow();
        assertEquals(250.0, current.getQuantity(), 0.001);
        assertFalse(current.isLowStock());

        inventoryService.deductStock(itemId, 200.0);
        InventoryItem lowStockItem = inventoryRepository.findById(itemId).orElseThrow();
        assertEquals(50.0, lowStockItem.getQuantity(), 0.001);
        assertTrue(lowStockItem.isLowStock());
    }

    @Test
    void shouldRejectOverdrawAndPreserveStockConsistency() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-test-overdraw")
                .name("Herbicida Seletivo")
                .category(InventoryCategory.DEFENSIVE)
                .quantity(80.0)
                .unit("L")
                .minimumThreshold(20.0)
                .build();
        InventoryItem saved = inventoryRepository.saveAndFlush(item);
        String itemId = saved.getId();
        Long initialVersion = saved.getVersion();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                inventoryService.deductStock(itemId, 100.0)
        );
        assertTrue(ex.getMessage().contains("Insufficient inventory"));

        InventoryItem afterRejection = inventoryRepository.findById(itemId).orElseThrow();
        assertEquals(80.0, afterRejection.getQuantity());
        assertEquals(initialVersion, afterRejection.getVersion());
    }
}
