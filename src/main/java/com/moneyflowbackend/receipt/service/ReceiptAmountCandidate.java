package com.moneyflowbackend.receipt.service;

import java.math.BigDecimal;

public record ReceiptAmountCandidate(
        BigDecimal value,
        String rawText,
        String normalizedValue,
        String lineText,
        String nearbyLabel,
        Source source,
        int score,
        boolean selected,
        boolean excluded,
        String excludedReason,
        Confidence confidence,
        String evidence) {

    public enum Source {
        AZURE_STRUCTURED_TOTAL,
        TEXT_LABEL_HIGH,
        TEXT_LABEL_MEDIUM,
        TEXT_UNLABELED,
        EXCLUDED_CONTEXT
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    ReceiptAmountCandidate markSelected() {
        return new ReceiptAmountCandidate(value, rawText, normalizedValue, lineText, nearbyLabel, source, score,
                true, excluded, excludedReason, confidence, evidence);
    }
}
