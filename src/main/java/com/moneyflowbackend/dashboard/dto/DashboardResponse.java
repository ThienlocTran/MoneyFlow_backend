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
    private DashboardSummaryBlockResponse summary;
    private BigDecimal walletTotal;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal netCashFlow;
    private long transactionCount;
    private DashboardComparisonResponse comparison;
    private List<DashboardCategoryResponse> categoryBreakdown;
    private List<DashboardJarResponse> jarBreakdown;
    private List<DashboardCategoryResponse> expenseByCategory;
    private List<DashboardJarResponse> expenseByJar;
    private List<DashboardCategoryResponse> incomeByCategory;
    private List<DashboardCategoryChangeResponse> topIncreases;
    private List<DashboardCategoryChangeResponse> topDecreases;
    private List<DashboardWalletBalanceResponse> walletBalances;
    private List<DashboardRecentTransactionResponse> recentTransactions;
}
