package com.farmflow.repository;

import com.farmflow.entity.FarmActivity;
import com.farmflow.enums.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<FarmActivity, String>, JpaSpecificationExecutor<FarmActivity> {
    List<FarmActivity> findByPlantationId(String plantationId);
    List<FarmActivity> findByFarmId(String farmId);
    List<FarmActivity> findByPlantationIdAndCompleted(String plantationId, Boolean completed);
    List<FarmActivity> findByType(ActivityType type);
}