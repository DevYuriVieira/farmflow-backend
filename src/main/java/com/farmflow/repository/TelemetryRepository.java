package com.farmflow.repository;

import com.farmflow.entity.TelemetryReading;
import com.farmflow.enums.TelemetryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemetryRepository extends JpaRepository<TelemetryReading, String> {
    List<TelemetryReading> findByFarmIdOrderByTimestampDesc(String farmId);
    List<TelemetryReading> findByFarmIdAndTypeOrderByTimestampDesc(String farmId, TelemetryType type);
    List<TelemetryReading> findBySensorIdOrderByTimestampDesc(String sensorId);
}


