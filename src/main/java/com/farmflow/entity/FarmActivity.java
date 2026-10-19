package com.farmflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmActivity extends AuditableEntity {

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