package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanningActionItemResponse(String deterministicKey, String type, String severity, String title,
                                         String message, BigDecimal amount, String currency, String actionLabel,
                                         String targetRoute, UUID targetEntityId) {
}
