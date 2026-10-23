package com.farmflow.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class MachineHoursTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.5, 120.0, 5000.75})
    @DisplayName("Deve instanciar MachineHours válidas")
    void shouldCreateValidMachineHours(double hours) {
        MachineHours mh = MachineHours.of(hours);
        assertEquals(hours, mh.value(), 0.01);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, -10.0, Double.NaN, Double.NEGATIVE_INFINITY})
    @DisplayName("Deve rejeitar horas negativas ou inválidas")
    void shouldRejectNegativeOrInvalidHours(double invalid) {
        assertThrows(IllegalArgumentException.class, () -> new MachineHours(invalid));
    }

    @Test
    @DisplayName("Deve somar horas de operação com incremento não-negativo")
    void shouldAddOperatingHoursCorrectly() {
        MachineHours initial = MachineHours.of(100.0);
        MachineHours updated = initial.addOperatingHours(12.5);

        assertEquals(112.5, updated.value(), 0.01);
        assertEquals(100.0, initial.value(), 0.01);
    }

    @Test
    @DisplayName("Deve rejeitar acréscimo de horas negativas")
    void shouldRejectNegativeOperatingHoursIncrement() {
        MachineHours mh = MachineHours.of(50.0);
        assertThrows(IllegalArgumentException.class, () -> mh.addOperatingHours(-5.0));
    }

    @Test
    @DisplayName("Deve aceitar avanço estritamente monotônico do horímetro")
    void shouldAcceptMonotonicAdvance() {
        MachineHours previous = MachineHours.of(100.0);
        MachineHours current = MachineHours.of(105.0);

        assertDoesNotThrow(() -> current.validateMonotonicAdvanceFrom(previous));
    }

    @Test
    @DisplayName("Deve permitir mesmo valor de horímetro (operação parada)")
    void shouldAllowSameHoursReading() {
        MachineHours reading1 = MachineHours.of(150.0);
        MachineHours reading2 = MachineHours.of(150.0);

        assertDoesNotThrow(() -> reading2.validateMonotonicAdvanceFrom(reading1));
    }

    @Test
    @DisplayName("Deve falhar com IllegalStateException quando nova leitura violar monotonicidade (for menor)")
    void shouldThrowWhenMonotonicityIsViolated() {
        MachineHours previous = MachineHours.of(200.0);
        MachineHours retroceded = MachineHours.of(195.0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> retroceded.validateMonotonicAdvanceFrom(previous));
        assertTrue(ex.getMessage().contains("Violação de monotonicidade"));
    }
}
