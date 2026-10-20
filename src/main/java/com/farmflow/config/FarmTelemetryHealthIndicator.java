package com.farmflow.config;

import com.farmflow.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FarmTelemetryHealthIndicator implements HealthIndicator {

    private final TelemetryRepository telemetryRepository;

    @Override
    public Health health() {
        try {
            long count = telemetryRepository.count();
            return Health.up()
                    .withDetail("totalTelemetryReadings", count)
                    .withDetail("telemetryStatus", "ACTIVE")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
