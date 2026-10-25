package com.farmflow.service;

import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.dto.InventoryDtos.CreateInventoryRequest;
import com.farmflow.dto.InventoryDtos.UpdateInventoryRequest;
import com.farmflow.entity.InventoryItem;
import com.farmflow.enums.InventoryCategory;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.event.cloudevents.InputReservedData;
import com.farmflow.exceptions.BusinessException;
import com.farmflow.exceptions.ResourceNotFoundException;
import com.farmflow.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InventoryServiceTest {

    private InventoryRepository inventoryRepository;
    private FarmSecurityEvaluator farmSecurity;
    private OutboxService outboxService;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        farmSecurity = mock(FarmSecurityEvaluator.class);
        outboxService = mock(OutboxService.class);

        inventoryService = new InventoryService(inventoryRepository, farmSecurity, outboxService);
    }

    @Test
    void shouldCreateItemWithTenantValidation() {
        CreateInventoryRequest request = new CreateInventoryRequest(
                "farm-1", "Adubo NPK", InventoryCategory.FERTILIZER,
                100.0, "kg", 20.0, 50.0, "Galpão 1"
        );
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Adubo NPK")
                .category(InventoryCategory.FERTILIZER)
                .quantity(100.0)
                .unit("kg")
                .build();
        when(inventoryRepository.save(any(InventoryItem.class))).thenReturn(item);

        InventoryItem created = inventoryService.createItem(request);

        assertNotNull(created);
        assertEquals("farm-1", created.getFarmId());
        verify(farmSecurity, times(1)).validateTenantAccess("farm-1");
        verify(inventoryRepository, times(1)).save(any(InventoryItem.class));
    }

    @Test
    void shouldThrowNotFoundWhenItemDoesNotExist() {
        when(inventoryRepository.findById("inv-missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> inventoryService.getItemById("inv-missing"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReserveStockAndEmitCanonicalCloudEvent() {
        InventoryItem item = InventoryItem.builder()
                .id("item-1")
                .farmId("farm-1")
                .name("Glifosato")
                .category(InventoryCategory.DEFENSIVE)
                .quantity(200.0)
                .unit("litros")
                .build();

        when(inventoryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(inventoryRepository.deductAtomically(eq("item-1"), eq(50.0), any(Instant.class))).thenReturn(1);

        InventoryItem result = inventoryService.reserveStock("item-1", "farm-1", "act-42", 50.0);

        assertNotNull(result);
        verify(farmSecurity, atLeastOnce()).validateTenantAccess("farm-1");
        verify(inventoryRepository, times(1)).deductAtomically(eq("item-1"), eq(50.0), any(Instant.class));

        ArgumentCaptor<CloudEvent<InputReservedData>> captor = ArgumentCaptor.forClass(CloudEvent.class);
        verify(outboxService, times(1)).recordCloudEvent(captor.capture());

        CloudEvent<InputReservedData> cloudEvent = captor.getValue();
        assertEquals("com.farmflow.supply.input.reserved.v1", cloudEvent.type());
        assertEquals("farm-1", cloudEvent.tenantId());
        assertEquals("act-42", cloudEvent.data().activityId());
        assertEquals("item-1", cloudEvent.data().itemId());
        assertEquals(50.0, cloudEvent.data().quantity());
        assertEquals("litros", cloudEvent.data().unit());
    }

    @Test
    void shouldRejectReserveStockWhenQuantityNonPositiveOrTenantMismatch() {
        assertThrows(BusinessException.class, () ->
                inventoryService.reserveStock("item-1", "farm-1", "act-1", 0.0)
        );
        assertThrows(BusinessException.class, () ->
                inventoryService.reserveStock("item-1", "farm-1", "act-1", -10.0)
        );

        InventoryItem itemOtherFarm = InventoryItem.builder()
                .id("item-2")
                .farmId("farm-other")
                .name("Sementes")
                .category(InventoryCategory.SEED)
                .quantity(100.0)
                .unit("sc")
                .build();
        when(inventoryRepository.findById("item-2")).thenReturn(Optional.of(itemOtherFarm));

        assertThrows(BusinessException.class, () ->
                inventoryService.reserveStock("item-2", "farm-1", "act-1", 10.0)
        );
    }

    @Test
    void shouldThrowExceptionWhenInventoryInsufficientToReserve() {
        InventoryItem item = InventoryItem.builder()
                .id("item-1")
                .farmId("farm-1")
                .name("Glifosato")
                .category(InventoryCategory.DEFENSIVE)
                .quantity(10.0)
                .unit("litros")
                .build();

        when(inventoryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(inventoryRepository.deductAtomically(eq("item-1"), eq(50.0), any(Instant.class))).thenReturn(0);

        assertThrows(BusinessException.class, () ->
                inventoryService.reserveStock("item-1", "farm-1", "act-1", 50.0)
        );
        verify(outboxService, never()).recordCloudEvent(any());
    }

    @Test
    void shouldDeductStockSuccessfully() {
        InventoryItem item = InventoryItem.builder()
                .id("item-1")
                .farmId("farm-1")
                .name("Sementes Milho")
                .category(InventoryCategory.SEED)
                .quantity(50.0)
                .unit("sc")
                .build();
        when(inventoryRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(inventoryRepository.deductAtomically(eq("item-1"), eq(10.0), any(Instant.class))).thenReturn(1);

        InventoryItem deducted = inventoryService.deductStock("item-1", 10.0);

        assertNotNull(deducted);
        verify(inventoryRepository, times(1)).deductAtomically(eq("item-1"), eq(10.0), any(Instant.class));
    }

    @Test
    void shouldReplenishStockSuccessfully() {
        InventoryItem item = InventoryItem.builder()
                .id("item-1")
                .farmId("farm-1")
                .name("Sementes Milho")
                .category(InventoryCategory.SEED)
                .quantity(50.0)
                .unit("sc")
                .build();
        when(inventoryRepository.findById("item-1")).thenReturn(Optional.of(item));

        InventoryItem replenished = inventoryService.replenishStock("item-1", 20.0);

        assertNotNull(replenished);
        verify(inventoryRepository, times(1)).replenishAtomically(eq("item-1"), eq(20.0), any(Instant.class));
    }

    @Test
    void shouldDeleteItemSuccessfully() {
        InventoryItem item = InventoryItem.builder()
                .id("item-del")
                .farmId("farm-1")
                .name("To Delete")
                .category(InventoryCategory.OTHER)
                .quantity(0.0)
                .unit("un")
                .build();
        when(inventoryRepository.findById("item-del")).thenReturn(Optional.of(item));

        inventoryService.deleteItem("item-del");

        verify(inventoryRepository, times(1)).delete(item);
    }
}
