package com.farmflow.entity;

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
}