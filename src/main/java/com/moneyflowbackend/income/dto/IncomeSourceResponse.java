package com.moneyflowbackend.income.dto;

import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.model.IncomeSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomeSourceResponse {
    private UUID id;
    private UUID workspaceId;
    private String name;
    private IncomeSourceType type;
    private String description;
    private IncomeSourceStatus status;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
