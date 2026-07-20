package com.moneyflowbackend.obligation.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OccurrenceGenerationResult(
        LocalDate fromDate,
        LocalDate toDate,
        int generatedCount,
        int existingCount,
        List<UUID> generatedOccurrenceIds) {
}
