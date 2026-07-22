package com.moneyflowbackend.sinkingfund.dto;

import com.moneyflowbackend.sinkingfund.model.SinkingFundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SinkingFundSummaryResponse {
    private UUID id;
    private UUID workspaceId;
    private String name;
    private SinkingFundStatus status;
    private BigDecimal reservedAmount;
    private BigDecimal targetAmount;
    private BigDecimal remainingAmount;
    private BigDecimal progressPercent;
    private LocalDate targetDate;
    private SinkingFundAllocationResponse latestAllocation;
    private BigDecimal activeWorkspaceReservedTotal;
}
