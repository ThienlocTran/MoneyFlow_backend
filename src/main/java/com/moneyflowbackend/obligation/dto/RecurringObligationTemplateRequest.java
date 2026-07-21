package com.moneyflowbackend.obligation.dto;

import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurringObligationTemplateRequest {
    private String name;
    private ObligationDirection direction;
    private ObligationAmountMode amountMode;
    private BigDecimal defaultAmount;
    private ObligationFrequency frequency;
    private Integer intervalCount;
    @NotNull
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer reminderDaysBefore;
    private UUID defaultWalletId;
    private UUID defaultCategoryId;
    private String note;
}
