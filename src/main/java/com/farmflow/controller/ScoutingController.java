package com.farmflow.controller;

import com.farmflow.entity.PestScoutingRecord;
import com.farmflow.dto.ScoutingDtos.CreatePestRecordRequest;
import com.farmflow.service.ScoutingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scouting")
@RequiredArgsConstructor
public class ScoutingController {

    private final ScoutingService scoutingService;

    @GetMapping("/farm/{farmId}")
    @PreAuthorize("hasRole('ADMIN') or @farmSecurity.hasFarmAccess(#farmId)")
    public ResponseEntity<List<PestScoutingRecord>> getRecordsByFarm(@PathVariable String farmId) {
        return ResponseEntity.ok(scoutingService.getRecordsByFarm(farmId));
    }

    @GetMapping("/plantation/{plantationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR')")
    public ResponseEntity<List<PestScoutingRecord>> getRecordsByPlantation(@PathVariable String plantationId) {
        return ResponseEntity.ok(scoutingService.getRecordsByPlantation(plantationId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR')")
    public ResponseEntity<PestScoutingRecord> recordPest(@Valid @RequestBody CreatePestRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scoutingService.recordPest(request));
    }
}


