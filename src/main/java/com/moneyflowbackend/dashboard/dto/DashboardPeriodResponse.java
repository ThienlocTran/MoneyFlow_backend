package com.moneyflowbackend.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardPeriodResponse {
    private String month;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String comparisonMode;
}
