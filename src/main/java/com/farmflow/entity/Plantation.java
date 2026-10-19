package com.farmflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "plantations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Builder.Default
    private String status = "planted";

    @Column(nullable = false)
    private String plantingDate;

    @Column(nullable = false)
    private String harvestEstimateDate;

    private Double expectedYieldTons;

    @Column(length = 1000)
    private String notes;
}