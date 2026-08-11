package com.moneyflowbackend.receipt.session;

public enum ReceiptImageStorageStatus {
    NOT_REQUESTED,
    STORED,
    STORAGE_NOT_CONFIGURED,
    STORAGE_FAILED,
    UNSUPPORTED_FORMAT,
    FILE_TOO_LARGE
}
