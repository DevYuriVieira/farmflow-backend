package com.farmflow.controller;

import com.farmflow.domain.FarmActivity;
import com.farmflow.dto.ActivityDtos.*;
import com.farmflow.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public ResponseEntity<List<FarmActivity>> getActivities(
        @RequestParam(required = false) String plantationId,
        @RequestParam(required = false) String farmId,
        @RequestParam(required = false) Boolean completed
    ) {
        return ResponseEntity.ok(activityService.getActivities(plantationId, farmId, completed));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FarmActivity> getActivityById(@PathVariable String id) {
        return ResponseEntity.ok(activityService.getActivityById(id));
    }

    @PostMapping
    public ResponseEntity<FarmActivity> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activityService.createActivity(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FarmActivity> updateActivity(
        @PathVariable String id,
        @RequestBody UpdateActivityRequest request
    ) {
        return ResponseEntity.ok(activityService.updateActivity(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<FarmActivity> toggleActivityStatus(
        @PathVariable String id,
        @RequestBody ToggleStatusRequest request
    ) {
        return ResponseEntity.ok(activityService.toggleStatus(id, request.completed()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivity(@PathVariable String id) {
        activityService.deleteActivity(id);
        return ResponseEntity.noContent().build();
    }
}
