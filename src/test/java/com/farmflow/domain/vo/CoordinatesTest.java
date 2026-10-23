package com.farmflow.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class CoordinatesTest {

    @ParameterizedTest
    @CsvSource({
            "-15.793889, -47.882778",
            "0.0, 0.0",
            "90.0, 180.0",
            "-90.0, -180.0",
            "45.123456, -93.654321"
    })
    @DisplayName("Deve instanciar Coordinates dentro dos limites válidos")
    void shouldCreateCoordinatesWithinValidBounds(double lat, double lon) {
        Coordinates coords = Coordinates.of(lat, lon);
        assertEquals(lat, coords.latitude(), 0.0001);
        assertEquals(lon, coords.longitude(), 0.0001);
    }

    @ParameterizedTest
    @CsvSource({
            "90.0001, 0.0",
            "-90.0001, 0.0",
            "0.0, 180.0001",
            "0.0, -180.0001"
    })
    @DisplayName("Deve rejeitar coordenadas fora dos limites geográficos")
    void shouldRejectCoordinatesOutOfBounds(double invalidLat, double invalidLon) {
        assertThrows(IllegalArgumentException.class, () -> Coordinates.of(invalidLat, invalidLon));
    }

    @Test
    @DisplayName("Deve rejeitar valores nulos")
    void shouldRejectNullCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(null, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(0.0, null));
    }

    @Test
    @DisplayName("Deve calcular distância geodésica em km via fórmula de Haversine")
    void shouldCalculateDistanceHaversineCorrectly() {
        // Ponto A: Brasília (-15.793889, -47.882778)
        Coordinates brasilia = Coordinates.of(-15.793889, -47.882778);
        // Ponto B: Goiânia (-16.686891, -49.264794)
        Coordinates goiania = Coordinates.of(-16.686891, -49.264794);

        double distance = brasilia.distanceToKm(goiania);

        // Distância real em linha reta ~170 a 180 km
        assertTrue(distance > 160.0 && distance < 190.0, "Distância esperada ~175 km, calculada: " + distance);
    }

    @Test
    @DisplayName("Distância para o mesmo ponto deve ser zero")
    void distanceToSamePointShouldBeZero() {
        Coordinates point = Coordinates.of(-20.5, -45.5);
        assertEquals(0.0, point.distanceToKm(point), 0.001);
    }
}
