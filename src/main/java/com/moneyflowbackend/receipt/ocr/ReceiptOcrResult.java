package com.moneyflowbackend.receipt.ocr;

import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiptOcrResult(
        ReceiptOcrProviderType provider,
        ReceiptOcrStatus status,
        String text,
        String merchantName,
        LocalDate receiptDate,
        BigDecimal totalAmount,
        String currency,
        List<Page> pages,
        List<ReceiptReviewParseResponse.Warning> warnings) {

    public ReceiptOcrResult(
            ReceiptOcrProviderType provider,
            ReceiptOcrStatus status,
            String text,
            List<ReceiptReviewParseResponse.Warning> warnings) {
        this(provider, status, text, null, null, null, null, List.of(), warnings);
    }

    public ReceiptOcrResult(
            ReceiptOcrProviderType provider,
            ReceiptOcrStatus status,
            String text,
            List<Page> pages,
            List<ReceiptReviewParseResponse.Warning> warnings) {
        this(provider, status, text, null, null, null, null, pages, warnings);
    }

    public record Page(int index, String text, Double confidence) {
    }
}
