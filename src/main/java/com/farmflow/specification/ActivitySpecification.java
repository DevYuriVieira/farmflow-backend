package com.farmflow.specification;

import com.farmflow.entity.FarmActivity;
import org.springframework.data.jpa.domain.Specification;

public class ActivitySpecification {

    public static Specification<FarmActivity> hasFarmId(String farmId) {
        return (root, query, cb) -> farmId == null || farmId.isBlank() ? null : cb.equal(root.get("farmId"), farmId);
    }

    public static Specification<FarmActivity> hasPlantationId(String plantationId) {
        return (root, query, cb) -> plantationId == null || plantationId.isBlank() ? null : cb.equal(root.get("plantationId"), plantationId);
    }

    public static Specification<FarmActivity> isCompleted(Boolean completed) {
        return (root, query, cb) -> completed == null ? null : cb.equal(root.get("completed"), completed);
    }

    public static Specification<FarmActivity> hasType(String type) {
        return (root, query, cb) -> type == null || type.isBlank() ? null : cb.equal(cb.lower(root.get("type")), type.toLowerCase());
    }
}
