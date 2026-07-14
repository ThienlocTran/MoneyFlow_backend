package com.moneyflowbackend.jar.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class JarMonthlySummaryResponse {
    private String month;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyExpense;
    private BigDecimal jarsTotalTargetPercent;
    private long jarsMappedCategoryCount;
    private long unmappedActiveExpenseCategoryCount;
    private long inactiveUnmappedCategoryCount;
    private String overallStatus;
    private List<Item> jars;

    @Data
    @Builder
    public static class Item {
        private UUID jarId;
        private String jarCode;
        private String jarName;
        private BigDecimal targetPercent;
        private BigDecimal targetAmount;
        private BigDecimal actualAmount;
        private BigDecimal actualPercentOfIncome;
        private long transactionCount;
        private long categoryCount;
        private BigDecimal remainingAmount;
        private BigDecimal overAmount;
        private String status;
        private String message;
    }
}
