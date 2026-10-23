package com.farmflow.domain.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record Coordinates(Double latitude, Double longitude) implements Serializable {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public Coordinates {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Latitude e longitude não podem ser nulas");
        }
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude inválida (deve estar entre -90.0 e +90.0): " + latitude);
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude inválida (deve estar entre -180.0 e +180.0): " + longitude);
        }

        latitude = BigDecimal.valueOf(latitude).setScale(6, RoundingMode.HALF_UP).doubleValue();
        longitude = BigDecimal.valueOf(longitude).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    public static Coordinates of(double latitude, double longitude) {
        return new Coordinates(latitude, longitude);
    }

    public double distanceToKm(Coordinates other) {
        if (other == null) {
            throw new IllegalArgumentException("Coordenada de destino não pode ser nula");
        }

        double latDistance = Math.toRadians(other.latitude - this.latitude);
        double lonDistance = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return BigDecimal.valueOf(EARTH_RADIUS_KM * c)
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Override
    public String toString() {
        return String.format("(lat: %.6f, lon: %.6f)", latitude, longitude);
    }
}
