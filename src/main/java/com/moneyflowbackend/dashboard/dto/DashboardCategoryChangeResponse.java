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
public class DashboardCategoryChangeResponse {
    private UUID categoryId;
    private String categoryName;
    private UUID jarId;
    private String jarName;
    private BigDecimal currentAmount;
    private BigDecimal previousAmount;
    private BigDecimal delta;
    private Double percent;
    private boolean newCategory;
}
