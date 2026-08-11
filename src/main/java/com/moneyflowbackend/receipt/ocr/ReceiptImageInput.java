package com.moneyflowbackend.receipt.ocr;

public record ReceiptImageInput(
        int index,
        String filename,
        String contentType,
        long sizeBytes,
        byte[] bytes,
        String sourceUrl) {
    public ReceiptImageInput(int index, String filename, String contentType, long sizeBytes, byte[] bytes) {
        this(index, filename, contentType, sizeBytes, bytes, null);
    }
}
