package com.farmflow.dto.scouting;

import jakarta.validation.constraints.NotBlank;

public class ScoutingDtos {

    public record CreatePestRecordRequest(
        @NotBlank(message = "ID da fazenda é obrigatório")
        String farmId,

        @NotBlank(message = "ID do talhão é obrigatório")
        String plantationId,

        @NotBlank(message = "Nome da praga/doença é obrigatório")
        String pestName,

        @NotBlank(message = "Severidade é obrigatória")
        String severity,

        Double confidenceScore,
        String photoUrl,
        Double latitude,
        Double longitude,
        String recommendedAction
    ) {}
}