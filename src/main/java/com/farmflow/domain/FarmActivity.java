package com.farmflow.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String plantationId;

    @Column(nullable = false)
    private String farmId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String type; // planting, irrigation, spraying, fertilization, harvest, scouting, maintenance, other

    @Column(nullable = false)
    private String date;

    @Builder.Default
    private Boolean completed = false;

    @Builder.Default
    private String priority = "medium"; // low, medium, high, urgent

    @Column(length = 1000)
    private String notes;

    private Double costEstimate;

    private String completedAt;
}
