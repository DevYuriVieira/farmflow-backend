package com.farmflow.entity;

import com.farmflow.enums.InventoryCategory;
import com.farmflow.exceptions.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "inventory_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryItem extends AuditableEntity {

    @Column(nullable = false)
    private String farmId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryCategory category;

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "unit_measure", nullable = false)
    private String unit;

    private Double minimumThreshold;

    private Double unitPrice;

    private String location;

    @Builder
    public InventoryItem(
            String id,
            String farmId,
            String name,
            InventoryCategory category,
            Double quantity,
            String unit,
            Double minimumThreshold,
            Double unitPrice,
            String location
    ) {
        if (farmId == null || farmId.isBlank()) {
            throw new IllegalArgumentException("Farm ID cannot be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Inventory item name cannot be null or blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("Inventory category cannot be null");
        }
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Initial quantity cannot be null or negative");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Unit of measure cannot be null or blank");
        }
        if (minimumThreshold != null && minimumThreshold < 0) {
            throw new IllegalArgumentException("Minimum threshold cannot be negative");
        }
        if (unitPrice != null && unitPrice < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative");
        }

        if (id != null) {
            this.setId(id);
        }
        this.farmId = farmId;
        this.name = name.trim();
        this.category = category;
        this.quantity = quantity;
        this.unit = unit.trim();
        this.minimumThreshold = minimumThreshold;
        this.unitPrice = unitPrice;
        this.location = location != null ? location.trim() : null;
    }

    public void updateDetails(
            String name,
            InventoryCategory category,
            String unit,
            Double minimumThreshold,
            Double unitPrice,
            String location
    ) {
        if (name != null) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("Inventory item name cannot be blank");
            }
            this.name = name.trim();
        }
        if (category != null) {
            this.category = category;
        }
        if (unit != null) {
            if (unit.isBlank()) {
                throw new IllegalArgumentException("Unit of measure cannot be blank");
            }
            this.unit = unit.trim();
        }
        if (minimumThreshold != null) {
            if (minimumThreshold < 0) {
                throw new IllegalArgumentException("Minimum threshold cannot be negative");
            }
            this.minimumThreshold = minimumThreshold;
        }
        if (unitPrice != null) {
            if (unitPrice < 0) {
                throw new IllegalArgumentException("Unit price cannot be negative");
            }
            this.unitPrice = unitPrice;
        }
        if (location != null) {
            this.location = location.trim();
        }
        this.setUpdatedAt(Instant.now());
    }

    public void adjustStock(Double newQuantity) {
        if (newQuantity == null || newQuantity < 0) {
            throw new IllegalArgumentException("Adjusted stock quantity cannot be null or negative");
        }
        this.quantity = newQuantity;
        this.setUpdatedAt(Instant.now());
    }

    public void deductStock(Double amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Deduct amount must be greater than zero");
        }
        if (this.quantity < amount) {
            throw new BusinessException(String.format(
                    "Insufficient inventory for '%s'. Available: %.2f %s, requested deduction: %.2f %s",
                    this.name, this.quantity, this.unit, amount, this.unit
            ));
        }
        this.quantity -= amount;
        this.setUpdatedAt(Instant.now());
    }

    public void replenishStock(Double amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Replenish amount must be greater than zero");
        }
        this.quantity += amount;
        this.setUpdatedAt(Instant.now());
    }

    public boolean isLowStock() {
        return this.minimumThreshold != null && this.quantity <= this.minimumThreshold;
    }

    public com.farmflow.domain.vo.Quantity getStockQuantity() {
        return (this.quantity != null && this.unit != null)
                ? com.farmflow.domain.vo.Quantity.of(this.quantity, this.unit)
                : null;
    }
}