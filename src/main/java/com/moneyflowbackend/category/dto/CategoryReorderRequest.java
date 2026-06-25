package com.moneyflowbackend.category.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CategoryReorderRequest {
    @NotEmpty(message = "Reorder items are required")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "categoryId is required")
        private UUID categoryId;
        @NotNull(message = "displayOrder is required")
        private Integer displayOrder;
    }
}
