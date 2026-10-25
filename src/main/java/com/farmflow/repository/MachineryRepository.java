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

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Machinery m
        SET m.operatingHours = :newHours, m.version = m.version + 1, m.updatedAt = :now
        WHERE m.id = :id AND (m.operatingHours IS NULL OR m.operatingHours <= :newHours)
        """)
    int updateOperatingHoursMonotonically(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("newHours") Double newHours,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now
    );
}

