package com.moneyflowbackend.income.dto;

import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.model.IncomeSourceType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class IncomeSourceSummaryResponse {
    private UUID incomeSourceId;
    private String name;
    private IncomeSourceType type;
    private IncomeSourceStatus status;
    private LocalDate from;
    private LocalDate toExclusive;
    private BigDecimal grossIncome;
    private BigDecimal directExpenses;
    private BigDecimal netIncome;
    private long incomeTransactionCount;
    private long expenseTransactionCount;
    private String currency;
}
