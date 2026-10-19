package com.farmflow.dto.machinery;

import jakarta.validation.constraints.NotBlank;

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

        Double operatingHours,
        String status,
        String nextMaintenanceDate,
        Double fuelLevelPercent
    ) {}

    public record UpdateMachineryRequest(
        String name,
        String model,
        String type,
        Double operatingHours,
        String status,
        String nextMaintenanceDate,
        Double fuelLevelPercent
    ) {}
}


