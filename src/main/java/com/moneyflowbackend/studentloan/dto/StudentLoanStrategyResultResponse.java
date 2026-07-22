package com.moneyflowbackend.studentloan.dto;

import com.moneyflowbackend.studentloan.model.StudentLoanPayoffStrategy;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class StudentLoanStrategyResultResponse {
    private StudentLoanPayoffStrategy strategy;
    private int monthCount;
    private LocalDate estimatedPayoffDate;
    private BigDecimal projectedInterest;
    private BigDecimal projectedTotalPaid;
    private boolean nonAmortizing;
    private String nonAmortizingReason;
    private List<StudentLoanStrategyLoanResponse> strategyOrder;
    private List<String> assumptions;
}
