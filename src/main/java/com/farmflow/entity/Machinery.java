package com.farmflow.entity;

import com.farmflow.enums.MachineryStatus;
import com.farmflow.event.MachineryMaintenanceScheduledEvent;
import com.farmflow.event.MachineryOperatingHoursUpdatedEvent;
import com.farmflow.exceptions.BusinessException;
import com.farmflow.exceptions.InvalidMachineryStateException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "machinery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MachineryStatus status = MachineryStatus.OPERATIONAL;

    @Column(name = "next_maintenance_date")
    private LocalDate nextMaintenanceDate;

    @Column(name = "maintenance_reason", length = 255)
    private String maintenanceReason;

    private Double fuelLevelPercent;

    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    @Builder
    public Machinery(
            String id,
            String farmId,
            String name,
            String model,
            String type,
            Double operatingHours,
            MachineryStatus status,
            LocalDate nextMaintenanceDate,
            String maintenanceReason,
            Double fuelLevelPercent
    ) {
        if (farmId == null || farmId.isBlank()) {
            throw new BusinessException("Farm ID cannot be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("Name cannot be blank.");
        }
        if (model == null || model.isBlank()) {
            throw new BusinessException("Model cannot be blank.");
        }
        if (type == null || type.isBlank()) {
            throw new BusinessException("Type cannot be blank.");
        }
        if (operatingHours != null && operatingHours < 0.0) {
            throw new BusinessException("Operating hours cannot be negative.");
        }
        if (fuelLevelPercent != null && (fuelLevelPercent < 0.0 || fuelLevelPercent > 100.0)) {
            throw new BusinessException("Fuel level must be between 0.0 and 100.0 percent.");
        }
        if (id != null) {
            setId(id);
        }
        this.farmId = farmId;
        this.name = name;
        this.model = model;
        this.type = type;
        this.operatingHours = operatingHours != null ? operatingHours : 0.0;
        this.status = status != null ? status : MachineryStatus.OPERATIONAL;
        this.nextMaintenanceDate = nextMaintenanceDate;
        this.maintenanceReason = maintenanceReason;
        this.fuelLevelPercent = fuelLevelPercent;
    }

    public static class MachineryBuilder {
        public MachineryBuilder status(String status) {
            this.status = MachineryStatus.fromString(status);
            return this;
        }

        public MachineryBuilder status(MachineryStatus status) {
            this.status = status;
            return this;
        }

        public MachineryBuilder nextMaintenanceDate(String nextMaintenanceDate) {
            this.nextMaintenanceDate = (nextMaintenanceDate != null && !nextMaintenanceDate.isBlank())
                    ? LocalDate.parse(nextMaintenanceDate)
                    : null;
            return this;
        }

        public MachineryBuilder nextMaintenanceDate(LocalDate nextMaintenanceDate) {
            this.nextMaintenanceDate = nextMaintenanceDate;
            return this;
        }
    }

    public String getStatus() {
        return this.status != null ? this.status.name() : MachineryStatus.OPERATIONAL.name();
    }

    public MachineryStatus getMachineryStatus() {
        return this.status;
    }

    public String getNextMaintenanceDate() {
        return this.nextMaintenanceDate != null ? this.nextMaintenanceDate.toString() : null;
    }

    public LocalDate getNextMaintenanceDateValue() {
        return this.nextMaintenanceDate;
    }

    public List<Object> pullDomainEvents() {
        List<Object> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return Collections.unmodifiableList(events);
    }

    public void sendToMaintenance(String reason, String expectedDate) {
        LocalDate parsedDate = (expectedDate != null && !expectedDate.isBlank())
                ? LocalDate.parse(expectedDate)
                : null;
        sendToMaintenance(reason, parsedDate);
    }

    public void sendToMaintenance(String reason, LocalDate expectedDate) {
        if (this.status == MachineryStatus.MAINTENANCE) {
            throw new InvalidMachineryStateException("Machinery is already under maintenance.");
        }
        if (!this.status.canTransitionTo(MachineryStatus.MAINTENANCE)) {
            throw new InvalidMachineryStateException("Machinery cannot transition from " + this.status + " to MAINTENANCE.");
        }
        this.status = MachineryStatus.MAINTENANCE;
        this.maintenanceReason = (reason != null && !reason.isBlank()) ? reason : "Scheduled maintenance";
        if (expectedDate != null) {
            this.nextMaintenanceDate = expectedDate;
        }
        this.domainEvents.add(new MachineryMaintenanceScheduledEvent(this, this.maintenanceReason, getNextMaintenanceDate()));
    }

    public void returnFromMaintenance(Double newOperatingHours) {
        if (this.status != MachineryStatus.MAINTENANCE) {
            throw new InvalidMachineryStateException("Machinery is not under maintenance.");
        }
        if (!this.status.canTransitionTo(MachineryStatus.OPERATIONAL)) {
            throw new InvalidMachineryStateException("Machinery cannot transition from " + this.status + " to OPERATIONAL.");
        }
        if (newOperatingHours != null) {
            updateOperatingHours(newOperatingHours);
        }
        this.status = MachineryStatus.OPERATIONAL;
        this.maintenanceReason = null;
    }

    public void updateOperatingHours(Double newHours) {
        if (newHours == null || newHours < 0.0) {
            throw new BusinessException("Operating hours cannot be negative.");
        }
        if (this.operatingHours != null && newHours < this.operatingHours) {
            throw new BusinessException("Operating hours cannot decrease.");
        }
        Double previousHours = this.operatingHours;
        this.operatingHours = newHours;
        this.domainEvents.add(new MachineryOperatingHoursUpdatedEvent(this, previousHours, newHours));
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
        if (this.status == MachineryStatus.MAINTENANCE) {
            throw new InvalidMachineryStateException("Cannot reassign machinery currently under maintenance.");
        }
        this.farmId = newFarmId;
    }

    public void updateDetails(String name, String model, String type) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (model != null && !model.isBlank()) {
            this.model = model;
        }
        if (type != null && !type.isBlank()) {
            this.type = type;
        }
    }

    public void scheduleNextMaintenance(LocalDate expectedDate) {
        this.nextMaintenanceDate = expectedDate;
    }

    public void scheduleNextMaintenance(String expectedDate) {
        this.nextMaintenanceDate = (expectedDate != null && !expectedDate.isBlank())
                ? LocalDate.parse(expectedDate)
                : null;
    }

    public void deactivate() {
        if (!this.status.canTransitionTo(MachineryStatus.INACTIVE)) {
            throw new InvalidMachineryStateException("Machinery cannot transition from " + this.status + " to INACTIVE.");
        }
        this.status = MachineryStatus.INACTIVE;
    }

    public void activate() {
        if (!this.status.canTransitionTo(MachineryStatus.OPERATIONAL)) {
            throw new InvalidMachineryStateException("Machinery cannot transition from " + this.status + " to OPERATIONAL.");
        }
        this.status = MachineryStatus.OPERATIONAL;
    }
}