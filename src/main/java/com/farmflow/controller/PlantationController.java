package com.farmflow.controller;

import com.farmflow.entity.Plantation;
import com.farmflow.dto.PlantationDtos.*;
import com.farmflow.service.PlantationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.farmflow.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping("/plantations")
@RequiredArgsConstructor
public class PlantationController {

    private final PlantationService plantationService;

    @GetMapping("/paged")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or (#farmId != null and @farmSecurity.hasFarmAccess(#farmId)) or (#farmId == null and isAuthenticated())")
    public ResponseEntity<PageResponse<Plantation>> getPlantationsPaged(
        @RequestParam(required = false) String farmId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String crop,
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(plantationService.getPlantationsPaged(farmId, status, crop, pageable));
    }

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or (#farmId != null and @farmSecurity.hasFarmAccess(#farmId)) or (#farmId == null and isAuthenticated())")
    public ResponseEntity<List<Plantation>> getPlantations(
        @RequestParam(required = false) String farmId,
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(plantationService.getPlantations(farmId, status));
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Plantation> getPlantationById(@PathVariable String id) {
        return ResponseEntity.ok(plantationService.getPlantationById(id));
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or @farmSecurity.hasFarmAccess(#request.farmId())")
    public ResponseEntity<Plantation> createPlantation(@Valid @RequestBody CreatePlantationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plantationService.createPlantation(request));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Plantation> updatePlantation(
        @PathVariable String id,
        @RequestBody UpdatePlantationRequest request
    ) {
        return ResponseEntity.ok(plantationService.updatePlantation(id, request));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deletePlantation(@PathVariable String id) {
        plantationService.deletePlantation(id);
        return ResponseEntity.noContent().build();
    }
}


