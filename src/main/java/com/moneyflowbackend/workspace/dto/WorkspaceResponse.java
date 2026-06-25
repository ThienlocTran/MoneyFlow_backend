package com.moneyflowbackend.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceResponse {
    private UUID id;
    private String name;
    private String workspaceType;
    private String currency;
    private String timezone;
    private String quickAmountUnit;
}