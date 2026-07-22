package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdvisoryCommitmentsResponse(
        List<StudentLoanAdvisoryResponse> studentLoans,
        BigDecimal total,
        boolean includedInActuallySpendable) {
}
