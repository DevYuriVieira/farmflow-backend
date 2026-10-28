package com.farmflow.controller;

import com.farmflow.dto.AdminDtos.DlqReplayRequest;
import com.farmflow.dto.AdminDtos.DlqReplayResponse;
import com.farmflow.service.AdminDlqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/dlq")
@RequiredArgsConstructor
public class AdminDlqController {

    private final AdminDlqService adminDlqService;

    @PostMapping("/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DlqReplayResponse> replayDlqMessages(
            @RequestBody(required = false) DlqReplayRequest request
    ) {
        DlqReplayResponse response = adminDlqService.replayMessages(request);
        return ResponseEntity.ok(response);
    }
}
