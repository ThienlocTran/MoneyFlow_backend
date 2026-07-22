package com.moneyflowbackend.emergencyfund.dto;

import com.moneyflowbackend.emergencyfund.model.EmergencyFundLedgerType;
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
public class EmergencyFundLedgerEntryResponse {
    private UUID id;
    private UUID workspaceId;
    private UUID emergencyFundPlanId;
    private EmergencyFundLedgerType entryType;
    private BigDecimal amountDelta;
    private String note;
    private UUID actorUserId;
    private Instant occurredAt;
    private Instant createdAt;
    private Long version;
}
