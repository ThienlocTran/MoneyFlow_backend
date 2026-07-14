package com.moneyflowbackend.jar.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class JarMonthlyDetailResponse {
    private String month;
    private UUID workspaceId;
    private UUID jarId;
    private String jarCode;
    private String jarName;
    private BigDecimal targetPercent;
    private BigDecimal monthlyIncome;
    private BigDecimal targetAmount;
    private BigDecimal actualAmount;
    private BigDecimal remainingAmount;
    private BigDecimal overAmount;
    private BigDecimal actualPercentOfIncome;
    private String status;
    private String message;
    private String formulaText;
    private Formula formula;
    private IncomeBreakdown incomeBreakdown;
    private List<CategoryBreakdown> categoryBreakdown;
    private List<RecentTransaction> recentTransactions;
    private List<RecentTransaction> recentExpenseTransactions;
    private Explanation explanation;

    @Data
    @Builder
    public static class Formula {
        private String label;
        private String calculationText;
    }

    @Data
    @Builder
    public static class IncomeBreakdown {
        private long transactionCount;
        private BigDecimal totalAmount;
        private List<RecentTransaction> recentTransactions;
    }

    @Data
    @Builder
    public static class CategoryBreakdown {
        private UUID categoryId;
        private String categoryName;
        private long transactionCount;
        private BigDecimal totalAmount;
        private BigDecimal percentOfJarActual;
        private BigDecimal percentOfMonthlyIncome;
    }

    @Data
    @Builder
    public static class RecentTransaction {
        private UUID id;
        private LocalDate date;
        private String categoryName;
        private String description;
        private BigDecimal amount;
        private String walletName;
    }

    @Data
    @Builder
    public static class Explanation {
        private String whyThisStatus;
        private String whatChangesBudget;
        private String nextAction;
    }
}
