package com.farmflow.dto;

import com.farmflow.enums.InventoryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class InventoryDtos {

    public record CreateInventoryRequest(
        @NotBlank(message = "ID da fazenda é obrigatório")
        String farmId,

        @NotBlank(message = "Nome do insumo é obrigatório")
        String name,

        @NotNull(message = "Categoria é obrigatória")
        InventoryCategory category,

        @NotNull(message = "Quantidade é obrigatória")
        @PositiveOrZero(message = "Quantidade não pode ser negativa")
        Double quantity,

        @NotBlank(message = "Unidade de medida é obrigatória")
        String unit,

        Double minimumThreshold,
        Double unitPrice,
        String location
    ) {}

    public record UpdateInventoryRequest(
        String name,
        InventoryCategory category,
        @PositiveOrZero(message = "Quantidade não pode ser negativa")
        Double quantity,
        String unit,
        @PositiveOrZero(message = "Limite mínimo não pode ser negativo")
        Double minimumThreshold,
        @PositiveOrZero(message = "Preço unitário não pode ser negativo")
        Double unitPrice,
        String location
    ) {}
}


