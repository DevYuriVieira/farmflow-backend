package com.farmflow.entity;

import com.farmflow.enums.TelemetryType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "telemetry_readings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemetryReading extends AuditableEntity {

    @Column(nullable = false)
    private String farmId;

    private String plantationId;

    @Column(nullable = false)
    private String sensorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_type", nullable = false)
    private TelemetryType type;

    @Column(name = "reading_value", nullable = false)
    private Double value;

    @Column(name = "unit_measure", nullable = false)
    private String unit;

    private String batteryLevel;

    @Builder.Default
    @Column(name = "recorded_at")
    private Instant timestamp = Instant.now();
}