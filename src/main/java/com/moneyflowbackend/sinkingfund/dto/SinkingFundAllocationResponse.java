package com.moneyflowbackend.sinkingfund.dto;

import com.moneyflowbackend.sinkingfund.model.SinkingFundAllocationType;
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
public class SinkingFundAllocationResponse {
    private UUID id;
    private UUID workspaceId;
    private UUID sinkingFundId;
    private SinkingFundAllocationType type;
    private BigDecimal amountDelta;
    private BigDecimal reservedAmount;
    private String note;
    private UUID actorUserId;
    private Instant occurredAt;
    private Instant createdAt;
}
