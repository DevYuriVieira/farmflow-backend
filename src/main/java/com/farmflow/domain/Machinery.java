package com.farmflow.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "machinery")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Machinery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String farmId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String type; // Trator, Colheitadeira, Pulverizador, Plantadeira

    private Double operatingHours;

    @Builder.Default
    private String status = "OPERATIONAL"; // OPERATIONAL, MAINTENANCE, IDLE

    private String nextMaintenanceDate;

    private Double fuelLevelPercent;

    @Builder.Default
    private Instant updatedAt = Instant.now();
}