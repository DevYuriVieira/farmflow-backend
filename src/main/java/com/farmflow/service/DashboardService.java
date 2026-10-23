package com.farmflow.service;

import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.dto.DashboardDtos.DashboardStatsResponse;
import com.farmflow.entity.Farm;
import com.farmflow.entity.TelemetryReading;
import com.farmflow.enums.TelemetryType;
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
    private final FarmSecurityEvaluator farmSecurity;

    public DashboardStatsResponse getStats(String farmId) {
        String targetFarmId = farmId;
        if (!farmSecurity.isAdmin()) {
            if (targetFarmId == null || targetFarmId.isBlank()) {
                targetFarmId = farmSecurity.getCurrentUserFarmId();
            } else {
                farmSecurity.validateTenantAccess(targetFarmId);
            }
        }

        long totalFarms;
        double totalArea;

        if (targetFarmId != null && !targetFarmId.isBlank()) {
            totalFarms = 1;
            totalArea = farmRepository.findById(targetFarmId)
                .map(Farm::getTotalAreaHectares)
                .orElse(0.0);

            long activePlantations = plantationRepository.findByFarmId(targetFarmId).size();
            long pendingActivities = activityRepository.findByFarmId(targetFarmId).stream()
                .filter(a -> a.getCompleted() == null || !a.getCompleted())
                .count();
            long operationalMachinery = machineryRepository.findByFarmIdAndStatus(targetFarmId, "OPERATIONAL").size();
            long pestCount = pestRecordRepository.findByFarmIdOrderByDetectedAtDesc(targetFarmId).size();
            List<TelemetryReading> silos = telemetryRepository.findByFarmIdAndTypeOrderByTimestampDesc(targetFarmId, TelemetryType.SILO_LEVEL);
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
        } else {
            List<Farm> allFarms = farmRepository.findAll();
            totalFarms = allFarms.size();
            totalArea = allFarms.stream()
                .mapToDouble(f -> f.getTotalAreaHectares() != null ? f.getTotalAreaHectares() : 0.0)
                .sum();
            long activePlantations = plantationRepository.count();
            long pendingActivities = activityRepository.findAll().stream()
                .filter(a -> a.getCompleted() == null || !a.getCompleted())
                .count();
            long operationalMachinery = machineryRepository.findAll().stream()
                .filter(m -> "OPERATIONAL".equalsIgnoreCase(m.getStatus()))
                .count();
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
}