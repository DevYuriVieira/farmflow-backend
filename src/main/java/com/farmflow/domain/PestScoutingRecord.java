package com.farmflow.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "pest_scouting_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PestScoutingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String farmId;

    @Column(nullable = false)
    private String plantationId;

    @Column(nullable = false)
    private String pestName;

    @Column(nullable = false)
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    private Double confidenceScore;

    private String photoUrl;

    private Double latitude;

    private Double longitude;

    @Column(length = 1000)
    private String recommendedAction;

    @Builder.Default
    private Instant detectedAt = Instant.now();
}