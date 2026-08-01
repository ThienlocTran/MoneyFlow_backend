package com.moneyflowbackend.quickentry.dto;

public enum VoiceLedgerEffect {
    AFFECTS_WALLET_NOW,
    DOES_NOT_AFFECT_WALLET,
    NEEDS_WALLET_REVIEW,
    READ_ONLY,
    MANUAL_UNSUPPORTED
}
