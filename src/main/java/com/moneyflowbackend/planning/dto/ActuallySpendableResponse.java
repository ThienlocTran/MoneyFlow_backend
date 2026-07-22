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
        List<SelectedWalletResponse> selectedWallets,
        BigDecimal availableLedger,
        ReserveBreakdownResponse reserveBreakdown,
        CommitmentBreakdownResponse commitmentBreakdown,
        AdvisoryCommitmentsResponse advisoryCommitments,
        BigDecimal actuallySpendable,
        boolean incomplete,
        List<String> warnings,
        List<String> assumptions) {
}
