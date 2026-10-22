package com.farmflow.domain;

import com.farmflow.entity.InventoryItem;
import com.farmflow.enums.InventoryCategory;
import com.farmflow.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryItemDomainTest {

    @Test
    void shouldCreateInventoryItemWithValidAttributes() {
        InventoryItem item = InventoryItem.builder()
                .id("inv-1")
                .farmId("farm-1")
                .name("Fertilizante Potássio KCL")
                .category(InventoryCategory.FERTILIZER)
                .quantity(5000.0)
                .unit("kg")
                .minimumThreshold(1000.0)
                .unitPrice(4.50)
                .location("Silo B")
                .build();

        assertEquals("inv-1", item.getId());
        assertEquals("farm-1", item.getFarmId());
        assertEquals("Fertilizante Potássio KCL", item.getName());
        assertEquals(InventoryCategory.FERTILIZER, item.getCategory());
        assertEquals(5000.0, item.getQuantity());
        assertEquals("kg", item.getUnit());
        assertEquals(1000.0, item.getMinimumThreshold());
        assertEquals(4.50, item.getUnitPrice());
        assertEquals("Silo B", item.getLocation());
        assertFalse(item.isLowStock());
    }

    @Test
    void shouldThrowExceptionWhenFarmIdIsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId(null)
                        .name("Ureia")
                        .category(InventoryCategory.FERTILIZER)
                        .quantity(100.0)
                        .unit("kg")
                        .build()
        );

        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId("   ")
                        .name("Ureia")
                        .category(InventoryCategory.FERTILIZER)
                        .quantity(100.0)
                        .unit("kg")
                        .build()
        );
    }

    @Test
    void shouldThrowExceptionWhenNameIsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId("farm-1")
                        .name(null)
                        .category(InventoryCategory.FERTILIZER)
                        .quantity(100.0)
                        .unit("kg")
                        .build()
        );

        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId("farm-1")
                        .name(" ")
                        .category(InventoryCategory.FERTILIZER)
                        .quantity(100.0)
                        .unit("kg")
                        .build()
        );
    }

    @Test
    void shouldThrowExceptionWhenCategoryIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId("farm-1")
                        .name("Insumo Geral")
                        .category(null)
                        .quantity(100.0)
                        .unit("kg")
                        .build()
        );
    }

    @Test
    void shouldThrowExceptionWhenQuantityIsNullOrNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId("farm-1")
                        .name("Insumo")
                        .category(InventoryCategory.OTHER)
                        .quantity(null)
                        .unit("un")
                        .build()
        );

        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId("farm-1")
                        .name("Insumo")
                        .category(InventoryCategory.OTHER)
                        .quantity(-5.0)
                        .unit("un")
                        .build()
        );
    }

    @Test
    void shouldThrowExceptionWhenUnitIsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId("farm-1")
                        .name("Insumo")
                        .category(InventoryCategory.OTHER)
                        .quantity(10.0)
                        .unit("")
                        .build()
        );
    }

    @Test
    void shouldThrowExceptionWhenThresholdOrPriceAreNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId("farm-1")
                        .name("Insumo")
                        .category(InventoryCategory.OTHER)
                        .quantity(10.0)
                        .unit("kg")
                        .minimumThreshold(-1.0)
                        .build()
        );

        assertThrows(IllegalArgumentException.class, () ->
                InventoryItem.builder()
                        .farmId("farm-1")
                        .name("Insumo")
                        .category(InventoryCategory.OTHER)
                        .quantity(10.0)
                        .unit("kg")
                        .unitPrice(-0.5)
                        .build()
        );
    }

    @Test
    void shouldUpdateDetailsCorrectly() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Glifosato 480")
                .category(InventoryCategory.DEFENSIVE)
                .quantity(200.0)
                .unit("L")
                .minimumThreshold(50.0)
                .unitPrice(45.0)
                .location("Armário Químico")
                .build();

        item.updateDetails(
                "Glifosato Concentrado 720",
                InventoryCategory.DEFENSIVE,
                "L",
                30.0,
                55.0,
                "Armário Químico - Prateleira 2"
        );

        assertEquals("Glifosato Concentrado 720", item.getName());
        assertEquals(30.0, item.getMinimumThreshold());
        assertEquals(55.0, item.getUnitPrice());
        assertEquals("Armário Químico - Prateleira 2", item.getLocation());
        assertNotNull(item.getUpdatedAt());
    }

    @Test
    void shouldThrowWhenUpdatingDetailsWithInvalidValues() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Item 1")
                .category(InventoryCategory.OTHER)
                .quantity(10.0)
                .unit("un")
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                item.updateDetails(" ", null, null, null, null, null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                item.updateDetails(null, null, " ", null, null, null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                item.updateDetails(null, null, null, -10.0, null, null)
        );

        assertThrows(IllegalArgumentException.class, () ->
                item.updateDetails(null, null, null, null, -1.0, null)
        );
    }

    @Test
    void shouldAdjustStockSuccessfully() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Semente Milho")
                .category(InventoryCategory.SEED)
                .quantity(50.0)
                .unit("sc")
                .build();

        item.adjustStock(85.0);
        assertEquals(85.0, item.getQuantity());

        item.adjustStock(0.0);
        assertEquals(0.0, item.getQuantity());

        assertThrows(IllegalArgumentException.class, () -> item.adjustStock(null));
        assertThrows(IllegalArgumentException.class, () -> item.adjustStock(-1.0));
    }

    @Test
    void shouldDeductStockWhenSufficientQuantityExists() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Óleo Diesel S10")
                .category(InventoryCategory.FUEL)
                .quantity(1000.0)
                .unit("L")
                .build();

        item.deductStock(350.0);
        assertEquals(650.0, item.getQuantity());

        item.deductStock(650.0);
        assertEquals(0.0, item.getQuantity());
    }

    @Test
    void shouldThrowBusinessExceptionWhenDeductingMoreThanAvailable() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Óleo Diesel S10")
                .category(InventoryCategory.FUEL)
                .quantity(100.0)
                .unit("L")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> item.deductStock(150.0));
        assertTrue(ex.getMessage().contains("Insufficient inventory"));
        assertEquals(100.0, item.getQuantity());
    }

    @Test
    void shouldThrowExceptionWhenDeductingZeroOrNegativeAmount() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Item")
                .category(InventoryCategory.OTHER)
                .quantity(100.0)
                .unit("un")
                .build();

        assertThrows(IllegalArgumentException.class, () -> item.deductStock(0.0));
        assertThrows(IllegalArgumentException.class, () -> item.deductStock(-10.0));
        assertThrows(IllegalArgumentException.class, () -> item.deductStock(null));
    }

    @Test
    void shouldReplenishStockSuccessfully() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Calcário Agrícola")
                .category(InventoryCategory.FERTILIZER)
                .quantity(20.0)
                .unit("ton")
                .build();

        item.replenishStock(30.0);
        assertEquals(50.0, item.getQuantity());

        assertThrows(IllegalArgumentException.class, () -> item.replenishStock(0.0));
        assertThrows(IllegalArgumentException.class, () -> item.replenishStock(-5.0));
        assertThrows(IllegalArgumentException.class, () -> item.replenishStock(null));
    }

    @Test
    void shouldDetectLowStockWhenThresholdIsReached() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Graxa Lubrificante")
                .category(InventoryCategory.OTHER)
                .quantity(10.0)
                .unit("balde")
                .minimumThreshold(5.0)
                .build();

        assertFalse(item.isLowStock());

        item.deductStock(5.0);
        assertTrue(item.isLowStock());

        item.deductStock(2.0);
        assertTrue(item.isLowStock());
    }

    @Test
    void shouldNotFlagLowStockWhenThresholdIsNull() {
        InventoryItem item = InventoryItem.builder()
                .farmId("farm-1")
                .name("Peça Avulsa")
                .category(InventoryCategory.OTHER)
                .quantity(0.0)
                .unit("un")
                .minimumThreshold(null)
                .build();

        assertFalse(item.isLowStock());
    }
}
