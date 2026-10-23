package com.farmflow.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Area(Double hectares) implements Serializable, Comparable<Area> {

    public static final double HECTARE_TO_SQUARE_METERS = 10_000.0;

    public Area {
        if (hectares == null) {
            throw new IllegalArgumentException("Área em hectares não pode ser nula");
        }
        if (Double.isNaN(hectares) || Double.isInfinite(hectares)) {
            throw new IllegalArgumentException("Área em hectares deve ser um número finito válido: " + hectares);
        }
        if (hectares <= 0.0) {
            throw new IllegalArgumentException("Área em hectares deve ser estritamente positiva (> 0): " + hectares);
        }
        hectares = BigDecimal.valueOf(hectares)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static Area ofHectares(double hectares) {
        return new Area(hectares);
    }

    public double toSquareMeters() {
        return BigDecimal.valueOf(hectares)
                .multiply(BigDecimal.valueOf(HECTARE_TO_SQUARE_METERS))
                .doubleValue();
    }

    public Area add(Area other) {
        Objects.requireNonNull(other, "Área a somar não pode ser nula");
        return new Area(this.hectares + other.hectares);
    }

    public boolean isGreaterThan(Area other) {
        Objects.requireNonNull(other, "Área para comparação não pode ser nula");
        return this.hectares > other.hectares;
    }

    @Override
    public int compareTo(Area o) {
        return Double.compare(this.hectares, o.hectares);
    }

    @Override
    public String toString() {
        return hectares + " ha";
    }
}
