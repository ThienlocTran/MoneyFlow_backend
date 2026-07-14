package com.moneyflowbackend.workspace.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WorkspaceMemberResponse {
    private UUID id;
    private UUID userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String role;
    private String status;
    private Instant joinedAt;
}
