package com.moneyflowbackend.receipt.service;

import java.util.List;
import java.util.UUID;

public record ReceiptCategorySuggestion(
        UUID categoryId,
        String categoryName,
        Confidence confidence,
        String evidence,
        List<CategoryCandidate> candidates,
        List<String> warnings) {

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    public record CategoryCandidate(
            UUID categoryId,
            String categoryName,
            String source,
            int score,
            Confidence confidence,
            boolean selected,
            boolean rejected,
            String rejectedReason,
            String evidence) {
    }
}
