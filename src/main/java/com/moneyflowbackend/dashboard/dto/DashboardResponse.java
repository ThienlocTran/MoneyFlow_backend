package com.moneyflowbackend.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {
    private DashboardPeriodResponse period;
    private BigDecimal walletTotal;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal netCashFlow;
    private long transactionCount;
    private DashboardComparisonResponse comparison;
    private List<DashboardCategoryResponse> expenseByCategory;
    private List<DashboardJarResponse> expenseByJar;
    private List<DashboardCategoryResponse> incomeByCategory;
    private List<DashboardRecentTransactionResponse> recentTransactions;
}
