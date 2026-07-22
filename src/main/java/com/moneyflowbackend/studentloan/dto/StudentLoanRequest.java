package com.moneyflowbackend.studentloan.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class StudentLoanRequest {
    private String name;
    private String lender;
    private BigDecimal originalPrincipal;
    private BigDecimal currentPrincipal;
    private BigDecimal annualInterestRate;
    private BigDecimal minimumMonthlyPayment;
    private BigDecimal plannedExtraMonthlyPayment;
    private LocalDate startDate;
    private LocalDate targetPayoffDate;
}
