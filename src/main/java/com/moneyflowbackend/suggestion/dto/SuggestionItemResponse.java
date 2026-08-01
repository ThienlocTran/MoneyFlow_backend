package com.moneyflowbackend.suggestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionItemResponse {
    private UUID id;
    private String name;
    private SuggestionTargetType type;
    private double confidence;
    private String reason;
    private SuggestionSource source;
    /**
     * True when the suggestion is weak. The caller must still let the user decide;
     * MoneyFlow never commits a low-confidence guess silently.
     */
    private boolean lowConfidence;
}
