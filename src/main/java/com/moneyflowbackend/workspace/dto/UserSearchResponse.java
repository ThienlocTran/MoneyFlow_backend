package com.moneyflowbackend.workspace.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UserSearchResponse {
    private UUID id;
    private String username;
    private String displayName;
    private String avatarUrl;
}
