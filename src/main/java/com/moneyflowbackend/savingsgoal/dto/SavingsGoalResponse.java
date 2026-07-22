package com.moneyflowbackend.savingsgoal.dto;

import com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsGoalResponse {
    private UUID id;
    private UUID workspaceId;
    private String name;
    private String description;
    private BigDecimal targetAmount;
    private LocalDate targetDate;
    private SavingsGoalStatus status;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
