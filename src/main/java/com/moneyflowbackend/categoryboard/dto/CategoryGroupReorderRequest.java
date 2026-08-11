package com.moneyflowbackend.categoryboard.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CategoryGroupReorderRequest(
        UUID jarId,
        @NotEmpty(message = "categoryIds are required")
        List<UUID> categoryIds,
        Boolean includeStats) {
}
