package com.moneyflowbackend.quickentry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuickEntryOptionsResponse {
    private UUID defaultWalletId;
    private List<WalletOption> wallets;
    private List<CategoryOption> quickCategories;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WalletOption {
        private UUID id;
        private String name;
        private String type;
        private boolean isDefault;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryOption {
        private UUID id;
        private String name;
        private String type;
        private String icon;
        private UUID jarId;
        private String jarName;
    }

}
