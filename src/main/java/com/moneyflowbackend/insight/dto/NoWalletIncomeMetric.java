package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record NoWalletIncomeMetric(
        BigDecimal totalAmount,
        long count,
        List<UUID> sampleTransactionIds
) {
}
