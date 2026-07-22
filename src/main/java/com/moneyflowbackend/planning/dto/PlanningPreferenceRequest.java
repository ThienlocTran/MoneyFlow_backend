package com.moneyflowbackend.planning.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanningPreferenceRequest(
        String defaultHorizon,
        LocalDate customFrom,
        LocalDate customTo,
        Boolean useIncludedWallets,
        List<UUID> selectedWalletIds,
        Long version) {
}
