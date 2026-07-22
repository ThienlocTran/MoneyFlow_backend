package com.moneyflowbackend.studentloan.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class StudentLoanStrategyRequest {
    private BigDecimal extraMonthlyBudget;
}
