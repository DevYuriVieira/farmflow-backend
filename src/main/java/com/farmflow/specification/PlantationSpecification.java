package com.farmflow.specification;

import com.farmflow.entity.Plantation;
import com.farmflow.enums.PlantationStatus;
import org.springframework.data.jpa.domain.Specification;

public class PlantationSpecification {

    public static Specification<Plantation> hasFarmId(String farmId) {
        return (root, query, cb) -> farmId == null || farmId.isBlank() ? null : cb.equal(root.get("farmId"), farmId);
    }

    public static Specification<Plantation> hasStatus(String status) {
        return (root, query, cb) -> status == null || status.isBlank() ? null : cb.equal(root.get("status"), PlantationStatus.fromString(status));
    }

    public static Specification<Plantation> hasCrop(String crop) {
        return (root, query, cb) -> crop == null || crop.isBlank() ? null : cb.equal(cb.lower(root.get("crop")), crop.toLowerCase());
    }
}
