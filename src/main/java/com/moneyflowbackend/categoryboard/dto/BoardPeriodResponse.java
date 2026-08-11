package com.moneyflowbackend.categoryboard.dto;

import java.time.LocalDate;

public record BoardPeriodResponse(
        LocalDate from,
        LocalDate to,
        String label) {
}
