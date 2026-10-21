package com.farmflow.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
public class OpenTelemetryConfig {

    @Bean
    public ObservationPredicate healthAndActuatorSpanFilter() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverContext) {
                String uri = serverContext.getCarrier().getRequestURI();
                return uri == null || (!uri.contains("/health") && !uri.contains("/actuator"));
            }
            return true;
        };
    }
}