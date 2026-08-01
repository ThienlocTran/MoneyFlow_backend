package com.moneyflowbackend.receipt.ocr;

public enum ReceiptOcrStatus {
    DISABLED,
    SKIPPED,
    SUCCEEDED,
    EXTRACTED,
    FAILED,
    TEXT_EMPTY,
    TIMEOUT,
    UNSUPPORTED
}
