package com.farmflow.dto;

public class DashboardDtos {

    public record DashboardStatsResponse(
        long totalFarms,
        double totalAreaHectares,
        long activePlantations,
        long pendingActivities,
        long operationalMachinery,
        long detectedPestsCount,
        Double averageSiloLevelPercent
    ) {}
}