package com.farmflow.service;

import com.farmflow.enums.TelemetryType;
import com.farmflow.dto.DashboardDtos.DashboardStatsResponse;
import com.farmflow.entity.Farm;
import com.farmflow.entity.TelemetryReading;
import com.farmflow.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final FarmRepository farmRepository;
    private final PlantationRepository plantationRepository;
    private final ActivityRepository activityRepository;
    private final MachineryRepository machineryRepository;
    private final PestRecordRepository pestRecordRepository;
    private final TelemetryRepository telemetryRepository;

    public DashboardStatsResponse getStats(String farmId) {
        long totalFarms;
        double totalArea;

        if (farmId != null && !farmId.isBlank()) {
            totalFarms = 1;
            totalArea = farmRepository.findById(farmId)
                .map(Farm::getTotalAreaHectares)
                .orElse(0.0);
        } else {
            List<Farm> allFarms = farmRepository.findAll();
            totalFarms = allFarms.size();
            totalArea = allFarms.stream()
                .mapToDouble(f -> f.getTotalAreaHectares() != null ? f.getTotalAreaHectares() : 0.0)
                .sum();
        }

        long activePlantations = plantationRepository.count();
        long pendingActivities = activityRepository.findAll().stream()
            .filter(a -> a.getCompleted() == null || !a.getCompleted())
            .count();
        long operationalMachinery = machineryRepository.findByFarmIdAndStatus(farmId != null ? farmId : "", "OPERATIONAL").size();
        if (farmId == null || farmId.isBlank()) {
            operationalMachinery = machineryRepository.findAll().stream()
                .filter(m -> "OPERATIONAL".equalsIgnoreCase(m.getStatus()))
                .count();
        }

        long pestCount = pestRecordRepository.count();

        List<TelemetryReading> silos = telemetryRepository.findAll().stream()
            .filter(t -> t.getType() == TelemetryType.SILO_LEVEL)
            .toList();

        Double avgSilo = silos.isEmpty() ? null : silos.stream()
            .mapToDouble(TelemetryReading::getValue)
            .average()
            .orElse(0.0);

        return new DashboardStatsResponse(
            totalFarms,
            totalArea,
            activePlantations,
            pendingActivities,
            operationalMachinery,
            pestCount,
            avgSilo
        );
    }
}