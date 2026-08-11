package com.moneyflowbackend.insight.dto;

public record SpendableDataQualityWarning(
        SpendableWarningCode code,
        String message,
        InsightSeverity severity,
        Long affectedCount
) {
}
