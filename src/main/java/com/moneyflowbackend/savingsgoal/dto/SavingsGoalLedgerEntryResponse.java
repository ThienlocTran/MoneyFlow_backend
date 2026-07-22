package com.moneyflowbackend.savingsgoal.dto;

import com.moneyflowbackend.savingsgoal.model.SavingsGoalLedgerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingsGoalLedgerEntryResponse {
    private UUID id;
    private UUID workspaceId;
    private UUID savingsGoalId;
    private SavingsGoalLedgerType entryType;
    private BigDecimal amountDelta;
    private String note;
    private UUID actorUserId;
    private Instant occurredAt;
    private Instant createdAt;
    private Long version;
}
