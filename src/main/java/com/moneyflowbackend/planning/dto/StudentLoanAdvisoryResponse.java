package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StudentLoanAdvisoryResponse(
        UUID loanId,
        String name,
        BigDecimal minimumMonthlyPayment,
        BigDecimal plannedExtraMonthlyPayment,
        BigDecimal advisoryTotal) {
}
