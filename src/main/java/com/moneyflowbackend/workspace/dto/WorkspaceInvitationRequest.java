package com.moneyflowbackend.workspace.dto;

import lombok.Data;

@Data
public class WorkspaceInvitationRequest {
    private String username;
    private String role;
}
