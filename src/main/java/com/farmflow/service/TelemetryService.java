package com.farmflow.service;

import com.farmflow.entity.TelemetryReading;
import com.farmflow.enums.TelemetryType;
import com.farmflow.dto.TelemetryDtos.CreateTelemetryRequest;
import com.farmflow.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import com.farmflow.event.TelemetryAlertEvent;
import org.springframework.context.ApplicationEventPublisher;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<TelemetryReading> getReadingsByFarm(String farmId, TelemetryType type) {
        if (type != null) {
            return telemetryRepository.findByFarmIdAndTypeOrderByTimestampDesc(farmId, type);
        }
        return telemetryRepository.findByFarmIdOrderByTimestampDesc(farmId);
    }

    public List<TelemetryReading> getReadingsBySensor(String sensorId) {
        return telemetryRepository.findBySensorIdOrderByTimestampDesc(sensorId);
    }

    public TelemetryReading recordReading(CreateTelemetryRequest request) {
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
            eventPublisher.publishEvent(new TelemetryAlertEvent(saved, "Low soil moisture: " + saved.getValue() + "%", "CRITICAL"));
        } else if (saved.getType() == TelemetryType.TEMPERATURE && saved.getValue() > 38.0) {
            eventPublisher.publishEvent(new TelemetryAlertEvent(saved, "High temperature: " + saved.getValue() + " C", "HIGH"));
        } else if (saved.getType() == TelemetryType.SILO_LEVEL && saved.getValue() < 15.0) {
            eventPublisher.publishEvent(new TelemetryAlertEvent(saved, "Low silo storage: " + saved.getValue() + "%", "MEDIUM"));
        }
        return saved;
    }
}


