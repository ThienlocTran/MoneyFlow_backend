package com.moneyflowbackend.workspace.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.workspace.dto.WorkspaceResponse;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository, WorkspaceMemberRepository workspaceMemberRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    public List<WorkspaceResponse> getUserWorkspaces(UUID userId) {
        return workspaceRepository.findAllByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public WorkspaceResponse getWorkspaceDetails(UUID workspaceId, UUID userId) {
        verifyMembership(workspaceId, userId);
        Workspace ws = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Không tìm thấy workspace"));
        return mapToResponse(ws);
    }

    public void verifyMembership(UUID workspaceId, UUID userId) {
        boolean isMember = workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE");
        if (!isMember) {
            throw new BusinessException("FORBIDDEN", "Bạn không có quyền truy cập workspace này");
        }
    }

    private WorkspaceResponse mapToResponse(Workspace ws) {
        return WorkspaceResponse.builder()
                .id(ws.getId())
                .name(ws.getName())
                .workspaceType(ws.getWorkspaceType().name())
                .currency(ws.getCurrency())
                .timezone(ws.getTimezone())
                .quickAmountUnit(ws.getQuickAmountUnit())
                .build();
    }
}