package com.moneyflowbackend.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardCategoryResponse {
    private UUID categoryId;
    private String categoryName;
    private UUID jarId;
    private String jarName;
    private String categoryType;
    private BigDecimal amount;
    private BigDecimal totalAmount;
    private double percentage;
    private long transactionCount;
}
