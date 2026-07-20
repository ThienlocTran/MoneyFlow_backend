package com.moneyflowbackend.obligation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SnoozeOccurrenceRequest {
    @NotNull(message = "snoozedUntil is required")
    private LocalDate snoozedUntil;
}
