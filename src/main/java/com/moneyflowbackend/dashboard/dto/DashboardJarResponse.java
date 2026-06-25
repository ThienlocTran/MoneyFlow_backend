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
public class DashboardJarResponse {
    private UUID jarId;
    private String jarCode;
    private String jarName;
    private BigDecimal allocationPercent;
    private BigDecimal amount;
    private BigDecimal totalAmount;
    private double percentage;
    private BigDecimal usagePercent;
}
