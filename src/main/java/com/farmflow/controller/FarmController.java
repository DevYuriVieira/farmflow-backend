package com.farmflow.controller;

import com.farmflow.entity.Farm;
import com.farmflow.dto.FarmDtos.*;
import com.farmflow.service.FarmService;
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
@RequestMapping("/farms")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;

    @GetMapping("/paged")
    public ResponseEntity<PageResponse<Farm>> getFarmsPaged(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String location,
        @RequestParam(required = false) String status,
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(farmService.getFarmsPaged(name, location, status, pageable));
    }

    @GetMapping
    public ResponseEntity<List<Farm>> getAllFarms() {
        return ResponseEntity.ok(farmService.getAllFarms());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Farm> getFarmById(@PathVariable String id) {
        return ResponseEntity.ok(farmService.getFarmById(id));
    }

    @PostMapping
    public ResponseEntity<Farm> createFarm(@Valid @RequestBody CreateFarmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(farmService.createFarm(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Farm> updateFarm(
        @PathVariable String id,
        @RequestBody UpdateFarmRequest request
    ) {
        return ResponseEntity.ok(farmService.updateFarm(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFarm(@PathVariable String id) {
        farmService.deleteFarm(id);
        return ResponseEntity.noContent().build();
    }
}


