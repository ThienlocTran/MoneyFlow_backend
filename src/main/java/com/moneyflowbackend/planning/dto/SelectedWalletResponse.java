package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SelectedWalletResponse(
        UUID id,
        String name,
        boolean includeInTotal,
        BigDecimal currentBalance) {
}
