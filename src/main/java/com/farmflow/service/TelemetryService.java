package com.farmflow.service;

import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.entity.TelemetryReading;
import com.farmflow.enums.TelemetryType;
import com.farmflow.dto.TelemetryDtos.CreateTelemetryRequest;
import com.farmflow.event.TelemetryAlertEvent;
import com.farmflow.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;
    private final FarmSecurityEvaluator farmSecurity;

    public List<TelemetryReading> getReadingsByFarm(String farmId, TelemetryType type) {
        farmSecurity.validateTenantAccess(farmId);
        if (type != null) {
            return telemetryRepository.findByFarmIdAndTypeOrderByTimestampDesc(farmId, type);
        }
        return telemetryRepository.findByFarmIdOrderByTimestampDesc(farmId);
    }

    public List<TelemetryReading> getReadingsBySensor(String sensorId) {
        List<TelemetryReading> readings = telemetryRepository.findBySensorIdOrderByTimestampDesc(sensorId);
        if (!readings.isEmpty()) {
            farmSecurity.validateTenantAccess(readings.get(0).getFarmId());
        }
        return readings;
    }

    @Transactional
    public TelemetryReading recordReading(CreateTelemetryRequest request) {
        farmSecurity.validateTenantAccess(request.farmId());
        TelemetryReading reading = TelemetryReading.builder()
            .farmId(request.farmId())
            .plantationId(request.plantationId())
            .sensorId(request.sensorId())
            .type(request.type())
            .value(request.value())
            .unit(request.unit())
            .batteryLevel(request.batteryLevel())
            .build();

        TelemetryReading saved = telemetryRepository.save(reading);
        if (saved.getType() == TelemetryType.SOIL_MOISTURE && saved.getValue() < 20.0) {
            TelemetryAlertEvent alert = new TelemetryAlertEvent(saved, "Low soil moisture: " + saved.getValue() + "%", "CRITICAL");
            outboxService.recordEvent("TelemetryAlert", alert);
            eventPublisher.publishEvent(alert);
        } else if (saved.getType() == TelemetryType.TEMPERATURE && saved.getValue() > 38.0) {
            TelemetryAlertEvent alert = new TelemetryAlertEvent(saved, "High temperature: " + saved.getValue() + " C", "HIGH");
            outboxService.recordEvent("TelemetryAlert", alert);
            eventPublisher.publishEvent(alert);
        } else if (saved.getType() == TelemetryType.SILO_LEVEL && saved.getValue() < 15.0) {
            TelemetryAlertEvent alert = new TelemetryAlertEvent(saved, "Low silo storage: " + saved.getValue() + "%", "MEDIUM");
            outboxService.recordEvent("TelemetryAlert", alert);
            eventPublisher.publishEvent(alert);
        }
        return saved;
    }
}