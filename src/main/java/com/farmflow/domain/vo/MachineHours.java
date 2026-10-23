package com.farmflow.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record MachineHours(Double value) implements Serializable, Comparable<MachineHours> {

    public MachineHours {
        if (value == null) {
            throw new IllegalArgumentException("Horímetro não pode ser nulo");
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Horímetro deve ser um número finito válido: " + value);
        }
        if (value < 0.0) {
            throw new IllegalArgumentException("Horímetro não pode ser negativo (< 0): " + value);
        }

        value = BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static MachineHours of(double value) {
        return new MachineHours(value);
    }

    public static MachineHours zero() {
        return new MachineHours(0.0);
    }

    public MachineHours addOperatingHours(double deltaHours) {
        if (deltaHours < 0.0) {
            throw new IllegalArgumentException("Horas de operação adicionais não podem ser negativas: " + deltaHours);
        }
        return new MachineHours(this.value + deltaHours);
    }

    public void validateMonotonicAdvanceFrom(MachineHours previous) {
        if (previous != null && this.value < previous.value) {
            throw new IllegalStateException(
                    String.format("Violação de monotonicidade no horímetro: leitura atual (%.2f h) não pode ser menor que a anterior (%.2f h)",
                            this.value, previous.value)
            );
        }
    }

    public boolean isGreaterThanOrEqualTo(MachineHours other) {
        Objects.requireNonNull(other, "Horímetro a comparar não pode ser nulo");
        return this.value >= other.value;
    }

    @Override
    public int compareTo(MachineHours o) {
        return Double.compare(this.value, o.value);
    }

    @Override
    public String toString() {
        return String.format("%.2f h", value);
    }
}
