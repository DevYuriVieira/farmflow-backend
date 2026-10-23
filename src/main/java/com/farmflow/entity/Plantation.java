package com.farmflow.entity;

import com.farmflow.enums.PlantationStatus;
import com.farmflow.exceptions.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plantations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Plantation extends AuditableEntity {

    @Column(nullable = false)
    private String farmId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String crop;

    private String variety;

    @Column(nullable = false)
    private Double areaHectares;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PlantationStatus status = PlantationStatus.PLANTED;

    @Column(nullable = false)
    private String plantingDate;

    @Column(nullable = false)
    private String harvestEstimateDate;

    private Double expectedYieldTons;

    @Column(length = 1000)
    private String notes;

    @Builder
    public Plantation(
            String id,
            String farmId,
            String name,
            String crop,
            String variety,
            Double areaHectares,
            PlantationStatus status,
            String plantingDate,
            String harvestEstimateDate,
            Double expectedYieldTons,
            String notes
    ) {
        if (farmId == null || farmId.isBlank()) {
            throw new BusinessException("Farm ID cannot be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("Name cannot be blank.");
        }
        if (crop == null || crop.isBlank()) {
            throw new BusinessException("Crop cannot be blank.");
        }
        if (areaHectares == null || areaHectares <= 0.0) {
            throw new BusinessException("Area in hectares must be greater than zero.");
        }
        if (expectedYieldTons != null && expectedYieldTons < 0.0) {
            throw new BusinessException("Expected yield cannot be negative.");
        }
        if (id != null) {
            setId(id);
        }
        this.farmId = farmId;
        this.name = name;
        this.crop = crop;
        this.variety = variety;
        this.areaHectares = areaHectares;
        this.status = status != null ? status : PlantationStatus.PLANTED;
        this.plantingDate = (plantingDate != null && !plantingDate.isBlank()) ? plantingDate : "";
        this.harvestEstimateDate = (harvestEstimateDate != null && !harvestEstimateDate.isBlank()) ? harvestEstimateDate : "";
        this.expectedYieldTons = expectedYieldTons;
        this.notes = notes;
    }

    public static class PlantationBuilder {
        public PlantationBuilder status(String status) {
            this.status = (status != null && !status.isBlank()) ? PlantationStatus.fromString(status) : PlantationStatus.PLANTED;
            return this;
        }

        public PlantationBuilder status(PlantationStatus status) {
            this.status = status != null ? status : PlantationStatus.PLANTED;
            return this;
        }
    }

    public String getStatus() {
        return this.status != null ? this.status.name().toLowerCase() : "planted";
    }

    public PlantationStatus getPlantationStatus() {
        return this.status;
    }

    public void transitionStatus(PlantationStatus newStatus) {
        if (newStatus == null) {
            throw new BusinessException("Target plantation status cannot be null.");
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new BusinessException("Cannot transition plantation status from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
    }

    public void transitionStatus(String newStatus) {
        if (newStatus == null || newStatus.isBlank()) {
            throw new BusinessException("Target plantation status cannot be null or blank.");
        }
        transitionStatus(PlantationStatus.fromString(newStatus));
    }

    public void updateDetails(
            String name,
            String crop,
            String variety,
            Double areaHectares,
            String plantingDate,
            String harvestEstimateDate,
            Double expectedYieldTons,
            String notes
    ) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (crop != null && !crop.isBlank()) {
            this.crop = crop;
        }
        if (variety != null) {
            this.variety = variety;
        }
        if (areaHectares != null) {
            if (areaHectares <= 0.0) {
                throw new BusinessException("Area in hectares must be greater than zero.");
            }
            this.areaHectares = areaHectares;
        }
        if (plantingDate != null) {
            this.plantingDate = plantingDate;
        }
        if (harvestEstimateDate != null) {
            this.harvestEstimateDate = harvestEstimateDate;
        }
        if (expectedYieldTons != null) {
            if (expectedYieldTons < 0.0) {
                throw new BusinessException("Expected yield cannot be negative.");
            }
            this.expectedYieldTons = expectedYieldTons;
        }
        if (notes != null) {
            this.notes = notes;
        }
    }
}