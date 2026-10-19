package com.farmflow.controller;

import com.farmflow.entity.Machinery;
import com.farmflow.dto.MachineryDtos.*;
import com.farmflow.service.MachineryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/machinery")
@RequiredArgsConstructor
public class MachineryController {

    private final MachineryService machineryService;

    @GetMapping("/farm/{farmId}")
    public ResponseEntity<List<Machinery>> getMachineryByFarm(
        @PathVariable String farmId,
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(machineryService.getMachineryByFarm(farmId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Machinery> getMachineryById(@PathVariable String id) {
        return ResponseEntity.ok(machineryService.getMachineryById(id));
    }

    @PostMapping
    public ResponseEntity<Machinery> createMachinery(@Valid @RequestBody CreateMachineryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(machineryService.createMachinery(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Machinery> updateMachinery(
        @PathVariable String id,
        @RequestBody UpdateMachineryRequest request
    ) {
        return ResponseEntity.ok(machineryService.updateMachinery(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMachinery(@PathVariable String id) {
        machineryService.deleteMachinery(id);
        return ResponseEntity.noContent().build();
    }
}


