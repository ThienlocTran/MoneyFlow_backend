package com.moneyflowbackend.activity.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ActivitySafeDetails {
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "transactionType",
            "transactionStatus",
            "walletId",
            "destinationWalletId",
            "categoryId",
            "obligationTemplateId",
            "closingDate",
            "adjustmentDirection"
    );

    private ActivitySafeDetails() {
    }

    public static Map<String, Object> whitelist(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (ALLOWED_KEYS.contains(key) && value != null) {
                safe.put(key, value);
            }
        });
        return Map.copyOf(safe);
    }
}
