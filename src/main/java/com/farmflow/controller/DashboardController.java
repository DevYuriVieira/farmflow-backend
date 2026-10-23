package com.farmflow.controller;

import com.farmflow.dto.DashboardDtos.DashboardStatsResponse;
import com.farmflow.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DashboardStatsResponse> getStats(@RequestParam(required = false) String farmId) {
        return ResponseEntity.ok(dashboardService.getStats(farmId));
    }
}