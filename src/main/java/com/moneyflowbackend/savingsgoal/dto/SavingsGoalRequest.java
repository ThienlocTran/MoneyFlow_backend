package com.moneyflowbackend.savingsgoal.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SavingsGoalRequest {
    @Size(max = 160, message = "Savings goal name must not exceed 160 characters")
    private String name;

    @Size(max = 500, message = "Savings goal description must not exceed 500 characters")
    private String description;

    private BigDecimal targetAmount;
    private LocalDate targetDate;
}
