package com.moneyflowbackend.receipt.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ReceiptReviewParseRequest {
    private String rawText;
    private ReceiptReviewSource source = ReceiptReviewSource.MANUAL_TEXT;
    private String imageReference;
    private OffsetDateTime occurredAtHint;
    private UUID walletId;
}
