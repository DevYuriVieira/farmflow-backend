package com.farmflow.controller;

import com.farmflow.entity.FarmActivity;
import com.farmflow.dto.ActivityDtos.*;
import com.farmflow.service.ActivityService;
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
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/paged")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or (#farmId != null and @farmSecurity.hasFarmAccess(#farmId)) or (#farmId == null and isAuthenticated())")
    public ResponseEntity<PageResponse<FarmActivity>> getActivitiesPaged(
        @RequestParam(required = false) String farmId,
        @RequestParam(required = false) String plantationId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Boolean completed,
        @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(activityService.getActivitiesPaged(farmId, plantationId, type, completed, pageable));
    }

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or (#farmId != null and @farmSecurity.hasFarmAccess(#farmId)) or (#farmId == null and isAuthenticated())")
    public ResponseEntity<List<FarmActivity>> getActivities(
        @RequestParam(required = false) String plantationId,
        @RequestParam(required = false) String farmId,
        @RequestParam(required = false) Boolean completed
    ) {
        return ResponseEntity.ok(activityService.getActivities(plantationId, farmId, completed));
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<FarmActivity> getActivityById(@PathVariable String id) {
        return ResponseEntity.ok(activityService.getActivityById(id));
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN') or @farmSecurity.hasFarmAccess(#request.farmId())")
    public ResponseEntity<FarmActivity> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.createActivity(request));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<FarmActivity> updateActivity(
        @PathVariable String id,
        @RequestBody UpdateActivityRequest request
    ) {
        return ResponseEntity.ok(activityService.updateActivity(id, request));
    }

    @PatchMapping("/{id}/status")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<FarmActivity> toggleActivityStatus(
        @PathVariable String id,
        @RequestBody ToggleStatusRequest request
    ) {
        return ResponseEntity.ok(activityService.toggleStatus(id, request.completed()));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteActivity(@PathVariable String id) {
        activityService.deleteActivity(id);
        return ResponseEntity.noContent().build();
    }
}


