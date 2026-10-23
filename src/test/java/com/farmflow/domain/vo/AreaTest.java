package com.farmflow.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AreaTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.0001, 1.0, 50.5, 1250.75, 999999.9})
    @DisplayName("Deve instanciar Area com hectares válidos")
    void shouldCreateAreaWithValidHectares(double hectares) {
        Area area = Area.ofHectares(hectares);
        assertNotNull(area);
        assertTrue(area.hectares() > 0.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.001, -1.0, -100.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
    @DisplayName("Deve rejeitar hectares nulos, negativos, zero ou infinitos")
    void shouldRejectInvalidHectares(double invalidHectares) {
        assertThrows(IllegalArgumentException.class, () -> new Area(invalidHectares));
    }

    @Test
    @DisplayName("Deve rejeitar valor nulo")
    void shouldRejectNullHectares() {
        assertThrows(IllegalArgumentException.class, () -> new Area(null));
    }

    @Test
    @DisplayName("Deve converter corretamente hectares para metros quadrados")
    void shouldConvertToSquareMetersCorrectly() {
        Area area = Area.ofHectares(2.5);
        assertEquals(25_000.0, area.toSquareMeters(), 0.001);
    }

    @Test
    @DisplayName("Deve somar áreas preservando imutabilidade")
    void shouldAddAreasCorrectly() {
        Area a1 = Area.ofHectares(10.0);
        Area a2 = Area.ofHectares(15.5);

        Area sum = a1.add(a2);

        assertEquals(25.5, sum.hectares(), 0.0001);
        assertEquals(10.0, a1.hectares());
        assertEquals(15.5, a2.hectares());
    }

    @Test
    @DisplayName("Deve comparar áreas com precisão")
    void shouldCompareAreasCorrectly() {
        Area smaller = Area.ofHectares(5.0);
        Area larger = Area.ofHectares(12.0);

        assertTrue(larger.isGreaterThan(smaller));
        assertFalse(smaller.isGreaterThan(larger));
        assertTrue(smaller.compareTo(larger) < 0);
        assertTrue(larger.compareTo(smaller) > 0);
        assertEquals(0, smaller.compareTo(Area.ofHectares(5.0)));
    }
}
