package com.moneyflowbackend.sinkingfund.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SinkingFundRequest {
    @Size(max = 160, message = "Sinking fund name must not exceed 160 characters")
    private String name;

    @Size(max = 500, message = "Sinking fund description must not exceed 500 characters")
    private String description;

    private BigDecimal targetAmount;
    private LocalDate targetDate;
    private String status;
}
