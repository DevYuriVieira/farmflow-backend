package com.farmflow.repository;

import com.farmflow.domain.PestScoutingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PestRecordRepository extends JpaRepository<PestScoutingRecord, String> {
    List<PestScoutingRecord> findByFarmIdOrderByDetectedAtDesc(String farmId);
    List<PestScoutingRecord> findByPlantationIdOrderByDetectedAtDesc(String plantationId);
}