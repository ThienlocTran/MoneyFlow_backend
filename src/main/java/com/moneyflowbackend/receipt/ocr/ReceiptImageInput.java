package com.moneyflowbackend.receipt.ocr;

public record ReceiptImageInput(
        int index,
        String filename,
        String contentType,
        long sizeBytes) {
}
