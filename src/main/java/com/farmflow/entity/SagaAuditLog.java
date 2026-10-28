package com.farmflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "saga_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SagaAuditLog extends AuditableEntity {

    @Column(nullable = false)
    private String farmId;

    @Column(nullable = false)
    private String activityId;

    @Column(nullable = false, length = 100)
    private String sagaType;

    @Column(nullable = false, length = 50)
    private String transitionFrom;

    @Column(nullable = false, length = 50)
    private String transitionTo;

    @Column(length = 1000)
    private String reason;

    @Column(length = 100)
    private String correlationId;

    @Column(nullable = false)
    private Instant executedAt;
}
