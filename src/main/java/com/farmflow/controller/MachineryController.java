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
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Machinery>> getAllMachinery() {
        return ResponseEntity.ok(machineryService.getAllMachinery());
    }

    @GetMapping("/farm/{farmId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or @farmSecurity.hasFarmAccess(#farmId)")
    public ResponseEntity<List<Machinery>> getMachineryByFarm(
        @PathVariable String farmId,
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(machineryService.getMachineryByFarm(farmId, status));
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Machinery> getMachineryById(@PathVariable String id) {
        return ResponseEntity.ok(machineryService.getMachineryById(id));
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or @farmSecurity.hasFarmAccess(#request.farmId())")
    public ResponseEntity<Machinery> createMachinery(@Valid @RequestBody CreateMachineryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(machineryService.createMachinery(request));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Machinery> updateMachinery(
        @PathVariable String id,
        @RequestBody UpdateMachineryRequest request
    ) {
        return ResponseEntity.ok(machineryService.updateMachinery(id, request));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteMachinery(@PathVariable String id) {
        machineryService.deleteMachinery(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/maintenance")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Machinery> sendToMaintenance(
        @PathVariable String id,
        @RequestBody(required = false) SendToMaintenanceRequest request
    ) {
        return ResponseEntity.ok(machineryService.sendToMaintenance(id, request));
    }

    @PostMapping("/{id}/return-from-maintenance")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Machinery> returnFromMaintenance(
        @PathVariable String id,
        @RequestBody(required = false) ReturnFromMaintenanceRequest request
    ) {
        return ResponseEntity.ok(machineryService.returnFromMaintenance(id, request));
    }

    @PostMapping("/{id}/operating-hours")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Machinery> updateOperatingHours(
        @PathVariable String id,
        @RequestBody UpdateOperatingHoursRequest request
    ) {
        return ResponseEntity.ok(machineryService.updateOperatingHours(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Machinery> deactivate(@PathVariable String id) {
        return ResponseEntity.ok(machineryService.deactivate(id));
    }

    @PostMapping("/{id}/activate")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Machinery> activate(@PathVariable String id) {
        return ResponseEntity.ok(machineryService.activate(id));
    }
}


