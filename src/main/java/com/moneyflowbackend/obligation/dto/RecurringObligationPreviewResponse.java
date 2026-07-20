package com.moneyflowbackend.obligation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringObligationPreviewResponse {
    private List<LocalDate> dueDates;
    private List<LocalDate> reminderDates;
    private boolean hasMore;
}
