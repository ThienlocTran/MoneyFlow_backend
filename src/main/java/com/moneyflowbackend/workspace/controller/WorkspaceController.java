package com.moneyflowbackend.workspace.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceResponse;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> list() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        List<WorkspaceResponse> res = workspaceService.getUserWorkspaces(userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách workspace thành công", res));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> get(@PathVariable UUID workspaceId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());
        WorkspaceResponse res = workspaceService.getWorkspaceDetails(workspaceId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết workspace thành công", res));
    }
}