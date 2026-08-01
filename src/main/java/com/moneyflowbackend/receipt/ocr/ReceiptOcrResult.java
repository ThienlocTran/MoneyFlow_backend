package com.moneyflowbackend.receipt.ocr;

import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;

import java.util.List;

public record ReceiptOcrResult(
        ReceiptOcrProviderType provider,
        ReceiptOcrStatus status,
        String text,
        List<ReceiptReviewParseResponse.Warning> warnings) {
}
