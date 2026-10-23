package com.farmflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class FarmDtos {

    public record CreateFarmRequest(
        @NotBlank(message = "Nome da fazenda é obrigatório")
        String name,

        @NotBlank(message = "Localização é obrigatória")
        String location,

        @NotNull(message = "Área total é obrigatória")
        @Positive(message = "Área total deve ser maior que zero")
        Double totalAreaHectares,

        @NotBlank(message = "Cultura principal é obrigatória")
        String primaryCrop,

        @NotBlank(message = "Nome do proprietário é obrigatório")
        String ownerName,

        String contactPhone
    ) {}

    public record UpdateFarmRequest(
        String name,
        String location,
        @Positive(message = "Área total deve ser maior que zero")
        Double totalAreaHectares,
        String primaryCrop,
        String ownerName,
        String contactPhone,
        String status
    ) {}
}


