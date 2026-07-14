package com.moneyflowbackend.workspace.controller;

import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.workspace.dto.UserSearchResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceInvitationRequest;
import com.moneyflowbackend.workspace.dto.WorkspaceInvitationResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceMemberResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceRequest;
import com.moneyflowbackend.workspace.dto.WorkspaceResponse;
import com.moneyflowbackend.workspace.dto.WorkspaceRoleRequest;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

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
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách không gian tài chính thành công", workspaceService.getUserWorkspaces(currentUserId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceResponse>> create(@RequestBody WorkspaceRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo sổ chung thành công", workspaceService.createWorkspace(req, currentUserId())));
    }

    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> get(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết không gian tài chính thành công", workspaceService.getWorkspaceDetails(workspaceId, currentUserId())));
    }

    @PutMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> update(@PathVariable UUID workspaceId, @RequestBody WorkspaceRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật không gian tài chính thành công", workspaceService.updateWorkspace(workspaceId, req, currentUserId())));
    }

    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<ApiResponse<List<WorkspaceMemberResponse>>> members(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách thành viên thành công", workspaceService.listMembers(workspaceId, currentUserId())));
    }

    @PutMapping("/{workspaceId}/members/{memberId}/role")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> updateMemberRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @RequestBody WorkspaceRoleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật vai trò thành công", workspaceService.updateMemberRole(workspaceId, memberId, req, currentUserId())));
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable UUID workspaceId, @PathVariable UUID memberId) {
        workspaceService.removeMember(workspaceId, memberId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Xóa thành viên thành công"));
    }

    @PostMapping("/{workspaceId}/invitations")
    public ResponseEntity<ApiResponse<WorkspaceInvitationResponse>> invite(
            @PathVariable UUID workspaceId,
            @RequestBody WorkspaceInvitationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Gửi lời mời thành công", workspaceService.invite(workspaceId, req, currentUserId())));
    }

    @GetMapping("/{workspaceId}/invitations")
    public ResponseEntity<ApiResponse<List<WorkspaceInvitationResponse>>> workspaceInvitations(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy lời mời đang chờ thành công", workspaceService.workspacePendingInvitations(workspaceId, currentUserId())));
    }

    @PostMapping("/{workspaceId}/invitations/{invitationId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelInvitation(@PathVariable UUID workspaceId, @PathVariable UUID invitationId) {
        workspaceService.cancelInvitation(workspaceId, invitationId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Hủy lời mời thành công"));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}

@RestController
class WorkspaceInvitationController {

    private final WorkspaceService workspaceService;

    WorkspaceInvitationController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/api/users/search")
    public ResponseEntity<ApiResponse<List<UserSearchResponse>>> searchUsers(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.ok("Tìm người dùng thành công", workspaceService.searchUsers(username)));
    }

    @GetMapping("/api/invitations/pending")
    public ResponseEntity<ApiResponse<List<WorkspaceInvitationResponse>>> pendingInvitations() {
        return ResponseEntity.ok(ApiResponse.ok("Lấy lời mời đang chờ thành công", workspaceService.pendingInvitations(currentUserId())));
    }

    @PostMapping("/api/invitations/{invitationId}/accept")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> accept(@PathVariable UUID invitationId) {
        return ResponseEntity.ok(ApiResponse.ok("Chấp nhận lời mời thành công", workspaceService.acceptInvitation(invitationId, currentUserId())));
    }

    @PostMapping("/api/invitations/{invitationId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable UUID invitationId) {
        workspaceService.rejectInvitation(invitationId, currentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Từ chối lời mời thành công"));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }
}
