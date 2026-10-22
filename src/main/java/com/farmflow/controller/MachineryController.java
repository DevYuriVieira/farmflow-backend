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

    @GetMapping
    public ResponseEntity<List<Machinery>> getAllMachinery() {
        return ResponseEntity.ok(machineryService.getAllMachinery());
    }

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

    @PostMapping("/{id}/maintenance")
    public ResponseEntity<Machinery> sendToMaintenance(
        @PathVariable String id,
        @RequestBody(required = false) SendToMaintenanceRequest request
    ) {
        return ResponseEntity.ok(machineryService.sendToMaintenance(id, request));
    }

    @PostMapping("/{id}/return-from-maintenance")
    public ResponseEntity<Machinery> returnFromMaintenance(
        @PathVariable String id,
        @RequestBody(required = false) ReturnFromMaintenanceRequest request
    ) {
        return ResponseEntity.ok(machineryService.returnFromMaintenance(id, request));
    }

    @PostMapping("/{id}/operating-hours")
    public ResponseEntity<Machinery> updateOperatingHours(
        @PathVariable String id,
        @RequestBody UpdateOperatingHoursRequest request
    ) {
        return ResponseEntity.ok(machineryService.updateOperatingHours(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Machinery> deactivate(@PathVariable String id) {
        return ResponseEntity.ok(machineryService.deactivate(id));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Machinery> activate(@PathVariable String id) {
        return ResponseEntity.ok(machineryService.activate(id));
    }
}


