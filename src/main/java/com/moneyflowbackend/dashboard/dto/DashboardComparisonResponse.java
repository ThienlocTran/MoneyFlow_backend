package com.moneyflowbackend.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardComparisonResponse {
    private BigDecimal currentAmount;
    private BigDecimal previousAmount;
    private double percentageDifference;
    private String status; // NEW, INCREASE, DECREASE, UNCHANGED, NO_DATA
    private java.time.LocalDate previousStartDate;
    private java.time.LocalDate previousEndDate;
    private BigDecimal previousIncome;
    private BigDecimal previousExpense;
    private BigDecimal previousNetCashFlow;
    private BigDecimal incomeDelta;
    private BigDecimal expenseDelta;
    private BigDecimal netDelta;
    private Double incomePercent;
    private Double expensePercent;
    private Double netPercent;
    private boolean incomeNew;
    private boolean expenseNew;
    private boolean netNew;
    private String incomeLabel;
    private String expenseLabel;
    private String netLabel;
}
