package com.farmflow.repository;

import com.farmflow.domain.FarmActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<FarmActivity, String> {
    List<FarmActivity> findByPlantationId(String plantationId);
    List<FarmActivity> findByFarmId(String farmId);
    List<FarmActivity> findByPlantationIdAndCompleted(String plantationId, Boolean completed);
}
