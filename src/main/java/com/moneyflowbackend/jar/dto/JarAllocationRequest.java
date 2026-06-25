package com.moneyflowbackend.jar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class JarAllocationRequest {
    @NotEmpty(message = "Allocation items are required")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "jarId is required")
        private UUID jarId;
        @NotNull(message = "allocationPercent is required")
        private BigDecimal allocationPercent;
    }
}
