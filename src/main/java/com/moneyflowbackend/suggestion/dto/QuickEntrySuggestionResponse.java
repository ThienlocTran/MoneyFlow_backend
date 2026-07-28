package com.moneyflowbackend.suggestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickEntrySuggestionResponse {
    @Builder.Default
    private List<SuggestionItemResponse> categorySuggestions = new ArrayList<>();
    @Builder.Default
    private List<SuggestionItemResponse> walletSuggestions = new ArrayList<>();
    @Builder.Default
    private List<SuggestionItemResponse> incomeSourceSuggestions = new ArrayList<>();
    @Builder.Default
    private List<Warning> warnings = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Warning {
        private String code;
        private String message;
        private String severity;
    }
}
