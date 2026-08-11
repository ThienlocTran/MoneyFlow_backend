package com.moneyflowbackend.receipt.session;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReceiptSessionDetailResponse {
    private UUID id;
    private UUID workspaceId;
    private ReceiptSessionStatus status;
    private ReceiptImageStorageStatus imageStorageStatus;
    private String imageContentType;
    private String imageOriginalFilename;
    private Long imageSizeBytes;
    private String imageUrl;
    private ReceiptSessionOcrStatus ocrStatus;
    private String ocrProvider;
    private String rawOcrText;
    private String normalizedOcrText;
    private String merchantName;
    private LocalDate receiptDate;
    private BigDecimal totalAmount;
    private String currency;
    private List<ReceiptSessionWarningResponse> warnings;
    private List<String> nextActions;
    private Instant createdAt;
    private Instant updatedAt;
}
