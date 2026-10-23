package com.farmflow.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class QuantityTest {

    @Test
    @DisplayName("Deve instanciar Quantity válida")
    void shouldCreateValidQuantity() {
        Quantity q = Quantity.of(150.0, "kg");
        assertEquals(150.0, q.amount());
        assertEquals("kg", q.unit());
        assertFalse(q.isZero());
    }

    @Test
    @DisplayName("Deve permitir quantidade zero")
    void shouldAllowZeroQuantity() {
        Quantity q = Quantity.zero("litros");
        assertEquals(0.0, q.amount());
        assertTrue(q.isZero());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.001, -10.0, Double.NaN, Double.NEGATIVE_INFINITY})
    @DisplayName("Deve rejeitar quantidades negativas ou não-numéricas")
    void shouldRejectNegativeOrInvalidAmount(double invalidAmount) {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(invalidAmount, "kg"));
    }

    @Test
    @DisplayName("Deve rejeitar unidade nula ou vazia")
    void shouldRejectNullOrBlankUnit() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(10.0, null));
        assertThrows(IllegalArgumentException.class, () -> new Quantity(10.0, "   "));
    }

    @Test
    @DisplayName("Deve somar e subtrair quantidades da mesma unidade")
    void shouldAddAndSubtractSameUnitQuantities() {
        Quantity q1 = Quantity.of(100.0, "kg");
        Quantity q2 = Quantity.of(40.0, "kg");

        Quantity sum = q1.add(q2);
        assertEquals(140.0, sum.amount());
        assertEquals("kg", sum.unit());

        Quantity diff = q1.subtract(q2);
        assertEquals(60.0, diff.amount());
        assertEquals("kg", diff.unit());
    }

    @Test
    @DisplayName("Deve rejeitar subtração que resulte em valor negativo")
    void shouldRejectSubtractionResultingInNegative() {
        Quantity q1 = Quantity.of(20.0, "litros");
        Quantity q2 = Quantity.of(50.0, "litros");

        assertThrows(IllegalArgumentException.class, () -> q1.subtract(q2));
    }

    @Test
    @DisplayName("Deve rejeitar operações com unidades diferentes")
    void shouldRejectOperationsWithDifferentUnits() {
        Quantity q1 = Quantity.of(100.0, "kg");
        Quantity q2 = Quantity.of(100.0, "litros");

        assertThrows(IllegalArgumentException.class, () -> q1.add(q2));
        assertThrows(IllegalArgumentException.class, () -> q1.subtract(q2));
        assertThrows(IllegalArgumentException.class, () -> q1.compareTo(q2));
    }
}
