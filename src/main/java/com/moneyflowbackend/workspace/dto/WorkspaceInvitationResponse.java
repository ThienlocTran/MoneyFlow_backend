package com.moneyflowbackend.workspace.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WorkspaceInvitationResponse {
    private UUID id;
    private UUID workspaceId;
    private String workspaceName;
    private UUID invitedUserId;
    private String invitedUsername;
    private String invitedDisplayName;
    private UUID invitedByUserId;
    private String invitedByUsername;
    private String role;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant respondedAt;
}
