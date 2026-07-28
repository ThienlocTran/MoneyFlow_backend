package com.moneyflowbackend.planning.dto;

import com.moneyflowbackend.planning.model.PlanningHorizon;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ActuallySpendableResponse(
        UUID workspaceId,
        Instant calculatedAt,
        PlanningHorizon horizon,
        LocalDate from,
        LocalDate to,
        LocalDate periodStart,
        LocalDate periodEnd,
        String periodLabel,
        String currencyCode,
        List<SelectedWalletResponse> selectedWallets,
        BigDecimal availableLedger,
        BigDecimal availableWalletBalance,
        ReserveBreakdownResponse reserveBreakdown,
        BigDecimal reservedTotal,
        CommitmentBreakdownResponse commitmentBreakdown,
        BigDecimal recurringObligationsTotal,
        AdvisoryCommitmentsResponse advisoryCommitments,
        BigDecimal payableDebtsTotal,
        BigDecimal actuallySpendable,
        PlanningBreakdownResponse breakdown,
        PlanningFormulaResponse formula,
        PlanningDataFreshnessResponse dataFreshness,
        List<PlanningNoticeResponse> warningDetails,
        List<PlanningNoticeResponse> exclusions,
        boolean incomplete,
        List<String> warnings,
        List<String> assumptions) {
}
