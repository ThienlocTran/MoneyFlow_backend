package com.moneyflowbackend.debt.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DebtRequest {
    @NotBlank
    @Size(max = 120)
    private String personName;

    @NotBlank
    private String type;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal principalAmount;

    @NotNull
    private LocalDate openedDate;

    private LocalDate dueDate;

    @Size(max = 2000)
    private String note;
}
