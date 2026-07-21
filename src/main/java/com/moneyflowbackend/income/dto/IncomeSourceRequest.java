package com.moneyflowbackend.income.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IncomeSourceRequest {
    @Size(max = 160, message = "Income source name must not exceed 160 characters")
    private String name;

    private String type;

    @Size(max = 500, message = "Income source description must not exceed 500 characters")
    private String description;
}
