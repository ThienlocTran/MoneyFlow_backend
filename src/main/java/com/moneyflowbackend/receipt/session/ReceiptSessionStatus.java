package com.moneyflowbackend.receipt.session;

public enum ReceiptSessionStatus {
    CREATED,
    IMAGE_UPLOADED,
    DRAFTED,
    NEEDS_REVIEW,
    PARTIALLY_CONFIRMED,
    CONFIRMED,
    FAILED,
    DELETED
}
