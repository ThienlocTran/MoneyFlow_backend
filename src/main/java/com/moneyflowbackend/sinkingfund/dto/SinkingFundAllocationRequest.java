package com.moneyflowbackend.sinkingfund.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SinkingFundAllocationRequest {
    private String type;
    private BigDecimal amount;

    @Size(max = 500, message = "Allocation note must not exceed 500 characters")
    private String note;
}
