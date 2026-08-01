package com.moneyflowbackend.receipt.ocr;

import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;

import java.util.List;

public record ReceiptOcrResult(
        ReceiptOcrProviderType provider,
        ReceiptOcrStatus status,
        String text,
        List<Page> pages,
        List<ReceiptReviewParseResponse.Warning> warnings) {

    public ReceiptOcrResult(
            ReceiptOcrProviderType provider,
            ReceiptOcrStatus status,
            String text,
            List<ReceiptReviewParseResponse.Warning> warnings) {
        this(provider, status, text, List.of(), warnings);
    }

    public record Page(int index, String text, Double confidence) {
    }
}
