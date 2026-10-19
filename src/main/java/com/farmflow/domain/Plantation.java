package com.farmflow.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "plantations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plantation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

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
    private String status = "planted"; // planning, planted, growing, harvesting, completed, failed

    @Column(nullable = false)
    private String plantingDate;

    @Column(nullable = false)
    private String harvestEstimateDate;

    private Double expectedYieldTons;

    @Column(length = 1000)
    private String notes;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
