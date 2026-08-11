package com.moneyflowbackend.receipt.service;

import java.time.LocalDate;

public record ReceiptDateCandidate(
        LocalDate value,
        String rawText,
        String lineText,
        Source source,
        int score,
        Confidence confidence,
        boolean selected,
        boolean rejected,
        String rejectedReason,
        String evidence) {

    public enum Source {
        AZURE_STRUCTURED_DATE,
        TEXT_HEADER_DATE,
        TEXT_DATE_PATTERN,
        REJECTED_CONTEXT
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    ReceiptDateCandidate markSelected() {
        return new ReceiptDateCandidate(value, rawText, lineText, source, score, confidence, true, rejected, rejectedReason, evidence);
    }
}
