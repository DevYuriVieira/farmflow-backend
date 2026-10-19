package com.farmflow.dto.telemetry;

import com.farmflow.domain.enums.TelemetryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TelemetryDtos {

    public record CreateTelemetryRequest(
        @NotBlank(message = "ID da fazenda é obrigatório")
        String farmId,

        String plantationId,

        @NotBlank(message = "ID do sensor é obrigatório")
        String sensorId,

        @NotNull(message = "Tipo de telemetria é obrigatório")
        TelemetryType type,

        @NotNull(message = "Valor da leitura é obrigatório")
        Double value,

        @NotBlank(message = "Unidade de medida é obrigatória")
        String unit,

        String batteryLevel
    ) {}
}