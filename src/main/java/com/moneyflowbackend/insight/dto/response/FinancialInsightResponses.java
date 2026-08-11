package com.moneyflowbackend.insight.dto.response;

import com.moneyflowbackend.insight.dto.ActionItemType;
import com.moneyflowbackend.insight.dto.InsightConfidence;
import com.moneyflowbackend.insight.dto.InsightEvidence;
import com.moneyflowbackend.insight.dto.InsightSeverity;
import com.moneyflowbackend.insight.dto.InsightType;
import com.moneyflowbackend.insight.dto.SpendableBreakdownItem;
import com.moneyflowbackend.insight.dto.SpendableDataQualityWarning;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinancialInsightResponses {
    private FinancialInsightResponses() {
    }

    public record PeriodResponse(LocalDate from, LocalDate to, String label) {
    }

    public record FinancialInsightOverviewResponse(
            UUID workspaceId,
            PeriodResponse period,
            Instant generatedAt,
            String currency,
            FinancialTotalsResponse totals,
            List<InsightCardResponse> topInsightCards,
            ActuallySpendableResponse actuallySpendable,
            List<FinancialActionItemResponse> actionItems,
            List<SpendableDataQualityWarning> dataQualityWarnings
    ) {
    }

    public record FinancialMetricResponse(
            UUID workspaceId,
            PeriodResponse period,
            String currency,
            FinancialTotalsResponse totals,
            List<MetricBreakdownResponse> incomeBySource,
            List<MetricBreakdownResponse> expenseByCategory,
            List<MetricBreakdownResponse> expenseByJar,
            NoWalletIncomeResponse noWalletIncome,
            MetricBreakdownResponse uncategorizedExpense
    ) {
    }

    public record FinancialTotalsResponse(
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal netCashflow,
            long transactionCount,
            long incomeTransactionCount,
            long expenseTransactionCount
    ) {
    }

    public record MetricBreakdownResponse(
            UUID id,
            String name,
            BigDecimal amount,
            long count,
            BigDecimal percentage
    ) {
    }

    public record NoWalletIncomeResponse(
            BigDecimal totalAmount,
            long count,
            List<UUID> sampleTransactionIds,
            String message
    ) {
    }

    public record InsightCardListResponse(
            UUID workspaceId,
            PeriodResponse period,
            List<InsightCardResponse> cards
    ) {
    }

    public record InsightCardResponse(
            String deterministicKey,
            InsightType type,
            InsightSeverity severity,
            String title,
            String message,
            BigDecimal amount,
            String currency,
            LocalDate periodFrom,
            LocalDate periodTo,
            List<InsightEvidence> evidence,
            String actionLabel,
            String targetRoute,
            InsightConfidence confidence
    ) {
    }

    public record ActuallySpendableResponse(
            UUID workspaceId,
            LocalDate asOfDate,
            int horizonDays,
            String currency,
            BigDecimal availableLedgerBalance,
            BigDecimal activeReserveAmount,
            BigDecimal upcomingRequiredOutflowAmount,
            BigDecimal overdueRequiredOutflowAmount,
            BigDecimal actuallySpendable,
            BigDecimal expectedIncomingAmount,
            List<SpendableBreakdownItem> sourceBreakdowns,
            List<SpendableDataQualityWarning> warnings,
            Instant generatedAt
    ) {
    }

    public record FinancialActionItemListResponse(
            UUID workspaceId,
            PeriodResponse period,
            LocalDate asOfDate,
            List<FinancialActionItemResponse> actionItems,
            List<SpendableDataQualityWarning> dataQualityWarnings,
            Instant generatedAt
    ) {
    }

    public record FinancialActionItemResponse(
            String deterministicKey,
            ActionItemType type,
            InsightSeverity severity,
            String title,
            String message,
            long count,
            BigDecimal amount,
            String currency,
            String actionLabel,
            String targetRoute,
            List<InsightEvidence> evidence,
            Instant generatedAt
    ) {
    }
}
