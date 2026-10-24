package com.farmflow.event.cloudevents;

import java.util.Objects;

public record InputUsedItem(
        String itemId,
        Double quantity,
        String unit
) {
    public InputUsedItem {
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(unit, "unit must not be null");

        if (itemId.isBlank()) throw new IllegalArgumentException("itemId must not be blank");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be strictly positive");
        if (unit.isBlank()) throw new IllegalArgumentException("unit must not be blank");
    }
}
