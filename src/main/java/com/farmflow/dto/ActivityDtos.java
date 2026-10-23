package com.farmflow.dto;

import jakarta.validation.constraints.NotBlank;

public class ActivityDtos {

    public record CreateActivityRequest(
        @NotBlank(message = "ID do talhão é obrigatório")
        String plantationId,

        @NotBlank(message = "ID da fazenda é obrigatório")
        String farmId,

        @NotBlank(message = "Título é obrigatório")
        String title,

        @NotBlank(message = "Tipo de atividade é obrigatório")
        String type,

        @NotBlank(message = "Data é obrigatória")
        String date,

        String priority,

        String notes,

        @jakarta.validation.constraints.PositiveOrZero(message = "Estimativa de custo não pode ser negativa")
        Double costEstimate
    ) {}

    public record UpdateActivityRequest(
        String title,
        String type,
        String date,
        String priority,
        String notes,
        @jakarta.validation.constraints.PositiveOrZero(message = "Estimativa de custo não pode ser negativa")
        Double costEstimate,
        Boolean completed
    ) {}

    public record ToggleStatusRequest(
        boolean completed
    ) {}
}


