package com.moneyflowbackend.suggestion.dto;

/**
 * Where a suggestion came from. Deterministic rules only, no model inference.
 */
public enum SuggestionSource {
    EXACT_NAME,
    KEYWORD,
    ALIAS,
    HISTORY,
    LAST_USED
}
