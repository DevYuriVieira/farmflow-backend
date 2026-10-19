package com.farmflow.service;

import com.farmflow.entity.TelemetryReading;
import com.farmflow.enums.TelemetryType;
import com.farmflow.dto.TelemetryDtos.CreateTelemetryRequest;
import com.farmflow.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;

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

        return telemetryRepository.save(reading);
    }
}


