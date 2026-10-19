package com.farmflow.domain;

import com.farmflow.domain.enums.InventoryCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String farmId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryCategory category;

    @Column(nullable = false)
    private Double quantity;

    @Column(name = "unit_measure", nullable = false)
    private String unit; // kg, L, sacas, un

    private Double minimumThreshold;

    private Double unitPrice;

    private String location;

    @Builder.Default
    private Instant updatedAt = Instant.now();
}