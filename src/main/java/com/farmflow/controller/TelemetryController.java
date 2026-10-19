package com.farmflow.controller;

import com.farmflow.entity.TelemetryReading;
import com.farmflow.enums.TelemetryType;
import com.farmflow.dto.TelemetryDtos.CreateTelemetryRequest;
import com.farmflow.service.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @GetMapping("/farm/{farmId}")
    public ResponseEntity<List<TelemetryReading>> getFarmReadings(
        @PathVariable String farmId,
        @RequestParam(required = false) TelemetryType type
    ) {
        return ResponseEntity.ok(telemetryService.getReadingsByFarm(farmId, type));
    }

    @GetMapping("/sensor/{sensorId}")
    public ResponseEntity<List<TelemetryReading>> getSensorReadings(@PathVariable String sensorId) {
        return ResponseEntity.ok(telemetryService.getReadingsBySensor(sensorId));
    }

    @PostMapping
    public ResponseEntity<TelemetryReading> recordReading(@Valid @RequestBody CreateTelemetryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(telemetryService.recordReading(request));
    }
}


