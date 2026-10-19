package com.farmflow.controller;

import com.farmflow.domain.PestScoutingRecord;
import com.farmflow.dto.scouting.ScoutingDtos.CreatePestRecordRequest;
import com.farmflow.service.ScoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scouting")
@RequiredArgsConstructor
public class ScoutingController {

    private final ScoutingService scoutingService;

    @GetMapping("/farm/{farmId}")
    public ResponseEntity<List<PestScoutingRecord>> getRecordsByFarm(@PathVariable String farmId) {
        return ResponseEntity.ok(scoutingService.getRecordsByFarm(farmId));
    }

    @GetMapping("/plantation/{plantationId}")
    public ResponseEntity<List<PestScoutingRecord>> getRecordsByPlantation(@PathVariable String plantationId) {
        return ResponseEntity.ok(scoutingService.getRecordsByPlantation(plantationId));
    }

    @PostMapping
    public ResponseEntity<PestScoutingRecord> recordPest(@Valid @RequestBody CreatePestRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scoutingService.recordPest(request));
    }
}