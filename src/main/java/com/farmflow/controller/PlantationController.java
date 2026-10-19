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

@RestController
@RequestMapping("/plantations")
@RequiredArgsConstructor
public class PlantationController {

    private final PlantationService plantationService;

    @GetMapping
    public ResponseEntity<List<Plantation>> getPlantations(
        @RequestParam(required = false) String farmId,
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(plantationService.getPlantations(farmId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Plantation> getPlantationById(@PathVariable String id) {
        return ResponseEntity.ok(plantationService.getPlantationById(id));
    }

    @PostMapping
    public ResponseEntity<Plantation> createPlantation(@Valid @RequestBody CreatePlantationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plantationService.createPlantation(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Plantation> updatePlantation(
        @PathVariable String id,
        @RequestBody UpdatePlantationRequest request
    ) {
        return ResponseEntity.ok(plantationService.updatePlantation(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlantation(@PathVariable String id) {
        plantationService.deletePlantation(id);
        return ResponseEntity.noContent().build();
    }
}


