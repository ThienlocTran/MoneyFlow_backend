package com.moneyflowbackend.emergencyfund.dto;

import com.moneyflowbackend.emergencyfund.model.EmergencyFundBasisMode;
import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlanStatus;
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
public class EmergencyFundPlanResponse {
    private UUID id;
    private UUID workspaceId;
    private Integer targetMonths;
    private EmergencyFundBasisMode basisMode;
    private BigDecimal manualMonthlyExpense;
    private EmergencyFundPlanStatus planStatus;
    private BigDecimal reservedAmount;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
