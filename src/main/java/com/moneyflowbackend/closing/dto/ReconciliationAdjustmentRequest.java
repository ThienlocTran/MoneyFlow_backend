package com.moneyflowbackend.closing.dto;

import com.moneyflowbackend.transaction.model.AdjustmentDirection;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReconciliationAdjustmentRequest {
    private AdjustmentDirection direction;
    private BigDecimal amount;
    private Boolean confirmed;
    private String note;
}
