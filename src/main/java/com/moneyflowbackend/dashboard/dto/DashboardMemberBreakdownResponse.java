package com.moneyflowbackend.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardMemberBreakdownResponse {
    private UUID userId;
    private String username;
    private String displayName;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal net;
    private long transactionCount;
}
