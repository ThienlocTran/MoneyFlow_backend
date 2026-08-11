package com.moneyflowbackend.receipt.session;

import lombok.Data;

@Data
public class ReceiptSessionCreateRequest {
    private ReceiptSessionSource source = ReceiptSessionSource.UPLOAD;
    private String note;
}
