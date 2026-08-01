package com.moneyflowbackend.receipt.ocr;

public enum ReceiptOcrStatus {
    DISABLED,
    SKIPPED,
    EXTRACTED,
    FAILED,
    TEXT_EMPTY,
    UNSUPPORTED
}
