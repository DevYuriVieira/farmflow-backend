package com.farmflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class MachineryDtos {

    public record CreateMachineryRequest(
        @NotBlank(message = "ID da fazenda é obrigatório")
        String farmId,

        @NotBlank(message = "Nome é obrigatório")
        String name,

        @NotBlank(message = "Modelo é obrigatório")
        String model,

        @NotBlank(message = "Tipo é obrigatório")
        String type,

        @PositiveOrZero(message = "Horas de operação não podem ser negativas")
        Double operatingHours,
        String status,
        String nextMaintenanceDate,
        @PositiveOrZero(message = "Nível de combustível não pode ser negativo")
        @Max(value = 100, message = "Nível de combustível não pode ser superior a 100%")
        Double fuelLevelPercent
    ) {}

    public record UpdateMachineryRequest(
        String name,
        String model,
        String type,
        @PositiveOrZero(message = "Horas de operação não podem ser negativas")
        Double operatingHours,
        String status,
        String nextMaintenanceDate,
        @PositiveOrZero(message = "Nível de combustível não pode ser negativo")
        @Max(value = 100, message = "Nível de combustível não pode ser superior a 100%")
        Double fuelLevelPercent
    ) {}

    public record SendToMaintenanceRequest(
        String reason,
        String expectedDate
    ) {}

    public record ReturnFromMaintenanceRequest(
        @NotNull(message = "Horas de operação são obrigatórias")
        @PositiveOrZero(message = "Horas de operação não podem ser negativas")
        Double newOperatingHours
    ) {}

    public record UpdateOperatingHoursRequest(
        @NotNull(message = "Horas de operação são obrigatórias")
        @PositiveOrZero(message = "Horas de operação não podem ser negativas")
        Double operatingHours
    ) {}
}


