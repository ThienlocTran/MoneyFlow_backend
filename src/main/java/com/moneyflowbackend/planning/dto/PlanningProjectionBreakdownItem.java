package com.moneyflowbackend.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PlanningProjectionBreakdownItem(String sourceType, UUID sourceId, String sourceName, BigDecimal amount,
                                              String currency, LocalDate dueDate, String priority,
                                              boolean includedInSpendableFormula, String note) {
}
