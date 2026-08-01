package com.moneyflowbackend.voice.command;

public enum VoiceCommandMode {
    TRANSACTION_REVIEW,
    MULTI_DRAFT_REVIEW,
    INCOME_FACT_REVIEW,
    WALLET_SNAPSHOT_REVIEW,
    READ_ONLY_QUERY,
    UNSUPPORTED,
    NEEDS_CLARIFICATION
}
