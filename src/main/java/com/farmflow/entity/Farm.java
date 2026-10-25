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

    public void updateDetails(
            String name,
            String location,
            Double totalAreaHectares,
            String primaryCrop,
            String ownerName,
            String contactPhone,
            String status
    ) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (location != null && !location.isBlank()) {
            this.location = location;
        }
        if (totalAreaHectares != null) {
            if (totalAreaHectares <= 0.0) {
                throw new com.farmflow.exceptions.BusinessException("Total area must be strictly positive.");
            }
            this.totalAreaHectares = totalAreaHectares;
        }
        if (primaryCrop != null && !primaryCrop.isBlank()) {
            this.primaryCrop = primaryCrop;
        }
        if (ownerName != null && !ownerName.isBlank()) {
            this.ownerName = ownerName;
        }
        if (contactPhone != null) {
            this.contactPhone = contactPhone;
        }
        if (status != null && !status.isBlank()) {
            this.status = status;
        }
    }

    public com.farmflow.domain.vo.Area getTotalArea() {
        return this.totalAreaHectares != null ? com.farmflow.domain.vo.Area.ofHectares(this.totalAreaHectares) : null;
    }
}