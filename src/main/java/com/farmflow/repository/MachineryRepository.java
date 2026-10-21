package com.farmflow.repository;

import com.farmflow.entity.Machinery;
import com.farmflow.enums.MachineryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MachineryRepository extends JpaRepository<Machinery, String> {
    List<Machinery> findByFarmId(String farmId);
    List<Machinery> findByFarmIdAndStatus(String farmId, MachineryStatus status);

    default List<Machinery> findByFarmIdAndStatus(String farmId, String status) {
        if (status == null || status.isBlank()) {
            return findByFarmId(farmId);
        }
        return findByFarmIdAndStatus(farmId, MachineryStatus.fromString(status));
    }
}
