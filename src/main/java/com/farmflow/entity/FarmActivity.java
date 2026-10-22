package com.farmflow.entity;

import com.farmflow.enums.ActivityPriority;
import com.farmflow.enums.ActivityType;
import com.farmflow.exceptions.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmActivity extends AuditableEntity {

    @Column(nullable = false)
    private String plantationId;

    @Column(nullable = false)
    private String farmId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private ActivityType type = ActivityType.OTHER;

    @Column(nullable = false)
    private String date;

    @Column(nullable = false)
    private Boolean completed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ActivityPriority priority = ActivityPriority.MEDIUM;

    @Column(length = 1000)
    private String notes;

    private Double costEstimate;

    private String completedAt;

    @Builder
    public FarmActivity(
            String id,
            String plantationId,
            String farmId,
            String title,
            ActivityType type,
            String date,
            Boolean completed,
            ActivityPriority priority,
            String notes,
            Double costEstimate,
            String completedAt
    ) {
        if (farmId == null || farmId.isBlank()) {
            throw new BusinessException("Farm ID cannot be blank.");
        }
        if (plantationId == null || plantationId.isBlank()) {
            throw new BusinessException("Plantation ID cannot be blank.");
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException("Title cannot be blank.");
        }
        if (costEstimate != null && costEstimate < 0.0) {
            throw new BusinessException("Cost estimate cannot be negative.");
        }
        if (id != null) {
            setId(id);
        }
        this.plantationId = plantationId;
        this.farmId = farmId;
        this.title = title;
        this.type = type != null ? type : ActivityType.OTHER;
        this.date = (date != null && !date.isBlank()) ? date : LocalDate.now().toString();
        this.completed = completed != null ? completed : false;
        this.priority = priority != null ? priority : ActivityPriority.MEDIUM;
        this.notes = notes;
        this.costEstimate = costEstimate;
        this.completedAt = completedAt;
    }

    public static class FarmActivityBuilder {
        public FarmActivityBuilder type(String type) {
            this.type = ActivityType.fromString(type);
            return this;
        }

        public FarmActivityBuilder type(ActivityType type) {
            this.type = type;
            return this;
        }

        public FarmActivityBuilder priority(String priority) {
            this.priority = ActivityPriority.fromString(priority);
            return this;
        }

        public FarmActivityBuilder priority(ActivityPriority priority) {
            this.priority = priority;
            return this;
        }
    }

    public String getType() {
        return this.type != null ? this.type.name() : ActivityType.OTHER.name();
    }

    @JsonIgnore
    public ActivityType getActivityType() {
        return this.type;
    }

    public String getPriority() {
        return this.priority != null ? this.priority.name() : ActivityPriority.MEDIUM.name();
    }

    @JsonIgnore
    public ActivityPriority getActivityPriority() {
        return this.priority;
    }

    public void complete() {
        if (Boolean.TRUE.equals(this.completed)) {
            return;
        }
        this.completed = true;
        this.completedAt = Instant.now().toString();
    }

    public void reopen() {
        this.completed = false;
        this.completedAt = null;
    }

    public void updateDetails(
            String title,
            ActivityType type,
            String date,
            ActivityPriority priority,
            String notes,
            Double costEstimate
    ) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (type != null) {
            this.type = type;
        }
        if (date != null && !date.isBlank()) {
            this.date = date;
        }
        if (priority != null) {
            this.priority = priority;
        }
        if (notes != null) {
            this.notes = notes;
        }
        if (costEstimate != null) {
            if (costEstimate < 0.0) {
                throw new BusinessException("Cost estimate cannot be negative.");
            }
            this.costEstimate = costEstimate;
        }
    }

    public void updateDetails(
            String title,
            String type,
            String date,
            String priority,
            String notes,
            Double costEstimate
    ) {
        ActivityType activityType = (type != null && !type.isBlank()) ? ActivityType.fromString(type) : null;
        ActivityPriority activityPriority = (priority != null && !priority.isBlank()) ? ActivityPriority.fromString(priority) : null;
        updateDetails(title, activityType, date, activityPriority, notes, costEstimate);
    }
}