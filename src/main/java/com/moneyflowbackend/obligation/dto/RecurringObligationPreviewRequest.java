package com.moneyflowbackend.obligation.dto;

import com.moneyflowbackend.obligation.model.ObligationFrequency;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecurringObligationPreviewRequest {
    private ObligationFrequency frequency;
    private Integer intervalCount;
    @NotNull
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer reminderDaysBefore;
    private Integer count;
}
