package com.farmflow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "outbox_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "routing_key")
    private String routingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "trace_parent")
    private String traceParent;

    @Column(name = "occurred_on", nullable = false)
    private Instant occurredOn;

    @Column(name = "processed_on")
    private Instant processedOn;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private com.farmflow.enums.OutboxStatus status = com.farmflow.enums.OutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}