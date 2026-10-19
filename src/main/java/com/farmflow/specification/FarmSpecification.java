package com.farmflow.specification;

import com.farmflow.entity.Farm;
import org.springframework.data.jpa.domain.Specification;

public class FarmSpecification {

    public static Specification<Farm> hasStatus(String status) {
        return (root, query, cb) -> status == null || status.isBlank() ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Farm> nameContains(String name) {
        return (root, query, cb) -> name == null || name.isBlank() ? null : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Farm> locationContains(String location) {
        return (root, query, cb) -> location == null || location.isBlank() ? null : cb.like(cb.lower(root.get("location")), "%" + location.toLowerCase() + "%");
    }
}
