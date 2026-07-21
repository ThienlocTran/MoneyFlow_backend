package com.moneyflowbackend.activity.domain;

public enum ActivitySource {
    TRANSACTION_AUDIT(50),
    TRANSACTION(40),
    DAILY_CLOSING(30),
    WALLET_SNAPSHOT(20),
    OBLIGATION_OCCURRENCE(10);

    private final int rank;

    ActivitySource(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
