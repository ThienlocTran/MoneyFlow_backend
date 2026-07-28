package com.moneyflowbackend.suggestion.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuickEntrySuggestionRequest {
    private String text;
    private String intentType;
    private BigDecimal amount;
}
