package com.moneyflowbackend.receipt.service;

public record ReceiptMerchantCandidate(
        String value,
        String rawText,
        String normalizedValue,
        Source source,
        int score,
        Confidence confidence,
        boolean selected,
        boolean rejected,
        String rejectedReason,
        String evidence) {

    public enum Source {
        AZURE_STRUCTURED_MERCHANT,
        TEXT_HEADER,
        TEXT_KNOWN_MERCHANT,
        TEXT_UNLABELED,
        REJECTED_CONTEXT
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    ReceiptMerchantCandidate markSelected() {
        return new ReceiptMerchantCandidate(value, rawText, normalizedValue, source, score, confidence, true, rejected, rejectedReason, evidence);
    }
}
