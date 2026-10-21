package com.farmflow.entity;

import com.farmflow.exceptions.BusinessException;
import com.farmflow.exceptions.InvalidMachineryStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "machinery")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Machinery extends AuditableEntity {

    @Column(nullable = false)
    private String farmId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String type;

    private Double operatingHours;

    @Builder.Default
    private String status = "OPERATIONAL";

    private String nextMaintenanceDate;

    private Double fuelLevelPercent;

    public void sendToMaintenance(String reason, String expectedDate) {
        if ("MAINTENANCE".equalsIgnoreCase(this.status)) {
            throw new InvalidMachineryStateException("Machinery is already under maintenance.");
        }
        this.status = "MAINTENANCE";
        if (expectedDate != null && !expectedDate.isBlank()) {
            this.nextMaintenanceDate = expectedDate;
        }
    }

    public void returnFromMaintenance(Double newOperatingHours) {
        if (!"MAINTENANCE".equalsIgnoreCase(this.status)) {
            throw new InvalidMachineryStateException("Machinery is not under maintenance.");
        }
        if (newOperatingHours != null) {
            updateOperatingHours(newOperatingHours);
        }
        this.status = "OPERATIONAL";
    }

    public void updateOperatingHours(Double newHours) {
        if (newHours == null || newHours < 0.0) {
            throw new BusinessException("Operating hours cannot be negative.");
        }
        if (this.operatingHours != null && newHours < this.operatingHours) {
            throw new BusinessException("Operating hours cannot decrease.");
        }
        this.operatingHours = newHours;
    }

    public void updateFuelLevel(Double newFuelPercent) {
        if (newFuelPercent == null || newFuelPercent < 0.0 || newFuelPercent > 100.0) {
            throw new BusinessException("Fuel level must be between 0.0 and 100.0 percent.");
        }
        this.fuelLevelPercent = newFuelPercent;
    }

    public void assignFarm(String newFarmId) {
        if (newFarmId == null || newFarmId.isBlank()) {
            throw new BusinessException("Farm ID cannot be blank.");
        }
        if ("MAINTENANCE".equalsIgnoreCase(this.status)) {
            throw new InvalidMachineryStateException("Cannot reassign machinery currently under maintenance.");
        }
        this.farmId = newFarmId;
    }
}