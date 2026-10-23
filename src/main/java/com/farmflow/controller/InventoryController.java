package com.farmflow.controller;

import com.farmflow.entity.InventoryItem;
import com.farmflow.enums.InventoryCategory;
import com.farmflow.dto.InventoryDtos.*;
import com.farmflow.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<InventoryItem>> getAllItems() {
        return ResponseEntity.ok(inventoryService.getAllItems());
    }

    @GetMapping("/farm/{farmId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or @farmSecurity.hasFarmAccess(#farmId)")
    public ResponseEntity<List<InventoryItem>> getItemsByFarm(
        @PathVariable String farmId,
        @RequestParam(required = false) InventoryCategory category
    ) {
        return ResponseEntity.ok(inventoryService.getItemsByFarm(farmId, category));
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<InventoryItem> getItemById(@PathVariable String id) {
        return ResponseEntity.ok(inventoryService.getItemById(id));
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or @farmSecurity.hasFarmAccess(#request.farmId())")
    public ResponseEntity<InventoryItem> createItem(@Valid @RequestBody CreateInventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createItem(request));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<InventoryItem> updateItem(
        @PathVariable String id,
        @Valid @RequestBody UpdateInventoryRequest request
    ) {
        return ResponseEntity.ok(inventoryService.updateItem(id, request));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteItem(@PathVariable String id) {
        inventoryService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}


