package com.farmflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "farms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Farm extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private Double totalAreaHectares;

    @Column(nullable = false)
    private String primaryCrop;

    @Column(nullable = false)
    private String ownerName;

    private String contactPhone;

    @Builder.Default
    private String status = "active";
}