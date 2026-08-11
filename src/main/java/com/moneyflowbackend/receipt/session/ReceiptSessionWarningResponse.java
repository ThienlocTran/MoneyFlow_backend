package com.moneyflowbackend.receipt.session;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReceiptSessionWarningResponse {
    private String code;
    private String message;
}
