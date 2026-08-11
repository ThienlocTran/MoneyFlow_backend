package com.moneyflowbackend.receipt.session;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ReceiptSessionConfirmDraftRequest {
    private BigDecimal amount;
    private String currency;
    private LocalDate transactionDate;
    private UUID walletId;
    private UUID categoryId;
    private String note;
    private String merchantName;
}
