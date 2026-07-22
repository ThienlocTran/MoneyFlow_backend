package com.moneyflowbackend.planning.dto;

import com.moneyflowbackend.planning.model.PlanningHorizon;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanningPreferenceResponse(
        UUID workspaceId,
        PlanningHorizon defaultHorizon,
        LocalDate customFrom,
        LocalDate customTo,
        boolean useIncludedWallets,
        List<UUID> selectedWalletIds,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
