package com.farmflow.repository;

import com.farmflow.entity.Plantation;
import com.farmflow.enums.PlantationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlantationRepository extends JpaRepository<Plantation, String>, JpaSpecificationExecutor<Plantation> {
    List<Plantation> findByFarmId(String farmId);
    List<Plantation> findByFarmIdAndStatus(String farmId, PlantationStatus status);
}