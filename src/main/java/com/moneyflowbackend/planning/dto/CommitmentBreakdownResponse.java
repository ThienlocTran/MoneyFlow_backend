package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;

public record CommitmentBreakdownResponse(
        BigDecimal knownUpcomingObligations,
        long variableUnknownCount) {
}
