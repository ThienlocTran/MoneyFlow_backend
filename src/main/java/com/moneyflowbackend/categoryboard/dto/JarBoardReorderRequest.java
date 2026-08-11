package com.moneyflowbackend.categoryboard.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record JarBoardReorderRequest(
        @NotEmpty(message = "jarIds are required")
        List<UUID> jarIds,
        Boolean includeStats) {
}
