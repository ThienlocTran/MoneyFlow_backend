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
    private UUID jarId;
    private String jarCode;
    private String jarName;
    private BigDecimal targetPercent;
    private BigDecimal monthlyIncome;
    private BigDecimal targetAmount;
    private BigDecimal actualAmount;
    private BigDecimal remainingAmount;
    private BigDecimal overAmount;
    private String status;
    private String formulaText;
    private List<CategoryBreakdown> categoryBreakdown;
    private List<RecentTransaction> recentTransactions;

    @Data
    @Builder
    public static class CategoryBreakdown {
        private UUID categoryId;
        private String categoryName;
        private long transactionCount;
        private BigDecimal totalAmount;
    }

    @Data
    @Builder
    public static class RecentTransaction {
        private LocalDate date;
        private String categoryName;
        private String description;
        private BigDecimal amount;
    }
}
