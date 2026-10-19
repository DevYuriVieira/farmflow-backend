package com.farmflow.entity;

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
    private String type;

    @Column(nullable = false)
    private String date;

    @Builder.Default
    private Boolean completed = false;

    @Builder.Default
    private String priority = "medium";

    @Column(length = 1000)
    private String notes;

    private Double costEstimate;

    private String completedAt;
}


