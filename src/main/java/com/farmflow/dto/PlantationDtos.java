package com.farmflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PlantationDtos {

    public record CreatePlantationRequest(
        @NotBlank(message = "ID da fazenda é obrigatório")
        String farmId,

        @NotBlank(message = "Nome do talhão é obrigatório")
        String name,

        @NotBlank(message = "Cultura é obrigatória")
        String crop,

        String variety,

        @NotNull(message = "Área é obrigatória")
        @Positive(message = "Área deve ser positiva")
        Double areaHectares,

        String status,

        @NotBlank(message = "Data de plantio é obrigatória")
        String plantingDate,

        @NotBlank(message = "Previsão de colheita é obrigatória")
        String harvestEstimateDate,

        Double expectedYieldTons,

        String notes
    ) {}

    public record UpdatePlantationRequest(
        String name,
        String crop,
        String variety,
        Double areaHectares,
        String status,
        String plantingDate,
        String harvestEstimateDate,
        Double expectedYieldTons,
        String notes
    ) {}
}
