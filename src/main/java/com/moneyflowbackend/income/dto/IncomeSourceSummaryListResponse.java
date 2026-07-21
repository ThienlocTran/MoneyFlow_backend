package com.moneyflowbackend.income.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class IncomeSourceSummaryListResponse {
    private LocalDate from;
    private LocalDate toExclusive;
    private List<IncomeSourceSummaryItemResponse> items;
}
