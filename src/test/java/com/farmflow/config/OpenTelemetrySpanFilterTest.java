package com.farmflow.config;

import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenTelemetrySpanFilterTest {

    private final OpenTelemetryConfig config = new OpenTelemetryConfig();

    @Test
    void shouldPermitNonServerObservations() {
        ObservationPredicate predicate = config.healthAndActuatorSpanFilter();
        boolean allowed = predicate.test("custom.event", null);
        assertTrue(allowed);
    }
}