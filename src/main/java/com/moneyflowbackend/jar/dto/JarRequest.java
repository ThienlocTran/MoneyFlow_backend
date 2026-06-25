package com.moneyflowbackend.jar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class JarRequest {
    @NotBlank(message = "Jar name is required")
    @Size(max = 100, message = "Jar name must be at most 100 characters")
    private String name;

    @Size(max = 20, message = "Jar code must be at most 20 characters")
    private String code;

    private BigDecimal allocationPercent;
    private Integer displayOrder;
}
