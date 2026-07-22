package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;

public record ReserveBreakdownResponse(
        BigDecimal sinkingFunds,
        BigDecimal savingsGoals,
        BigDecimal emergencyFund,
        BigDecimal total) {
}
