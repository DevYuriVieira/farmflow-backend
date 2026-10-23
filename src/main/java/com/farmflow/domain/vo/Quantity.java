package com.farmflow.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Quantity(Double amount, String unit) implements Serializable, Comparable<Quantity> {

    public Quantity {
        if (amount == null) {
            throw new IllegalArgumentException("Quantidade não pode ser nula");
        }
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("Quantidade deve ser um número finito válido: " + amount);
        }
        if (amount < 0.0) {
            throw new IllegalArgumentException("Quantidade não pode ser negativa (< 0): " + amount);
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Unidade de medida não pode ser nula ou vazia");
        }

        unit = unit.trim().toLowerCase();
        amount = BigDecimal.valueOf(amount)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static Quantity of(double amount, String unit) {
        return new Quantity(amount, unit);
    }

    public static Quantity zero(String unit) {
        return new Quantity(0.0, unit);
    }

    public Quantity add(Quantity other) {
        ensureSameUnit(other);
        return new Quantity(this.amount + other.amount, this.unit);
    }

    public Quantity subtract(Quantity other) {
        ensureSameUnit(other);
        double result = this.amount - other.amount;
        if (result < 0.0) {
            throw new IllegalArgumentException(
                    "Subtração resultaria em quantidade negativa: " + this.amount + " - " + other.amount
            );
        }
        return new Quantity(result, this.unit);
    }

    public boolean isZero() {
        return this.amount == 0.0;
    }

    public boolean isGreaterThan(Quantity other) {
        ensureSameUnit(other);
        return this.amount > other.amount;
    }

    public boolean isGreaterThanOrEqualTo(Quantity other) {
        ensureSameUnit(other);
        return this.amount >= other.amount;
    }

    private void ensureSameUnit(Quantity other) {
        Objects.requireNonNull(other, "Quantidade a comparar/operar não pode ser nula");
        if (!this.unit.equalsIgnoreCase(other.unit)) {
            throw new IllegalArgumentException(
                    "Incompatibilidade de unidades: " + this.unit + " vs " + other.unit
            );
        }
    }

    @Override
    public int compareTo(Quantity o) {
        ensureSameUnit(o);
        return Double.compare(this.amount, o.amount);
    }

    @Override
    public String toString() {
        return amount + " " + unit;
    }
}
