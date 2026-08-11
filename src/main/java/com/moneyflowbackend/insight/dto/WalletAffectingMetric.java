package com.moneyflowbackend.insight.dto;

import java.math.BigDecimal;

public record WalletAffectingMetric(
        BigDecimal walletAffectingIncome,
        BigDecimal walletAffectingExpense,
        BigDecimal nonWalletIncome,
        BigDecimal transferIn,
        BigDecimal transferOut,
        BigDecimal snapshotAdjustments
) {
}
