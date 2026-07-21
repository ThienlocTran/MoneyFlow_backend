package com.moneyflowbackend.obligation.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecurrenceCalculator {
    public List<LocalDate> calculate(
            LocalDate startDate,
            LocalDate endDate,
            ObligationFrequency frequency,
            int intervalCount,
            LocalDate fromDate,
            LocalDate toDate) {
        validate(startDate, frequency, intervalCount, fromDate, toDate);
        LocalDate lastDate = endDate == null || endDate.isAfter(toDate) ? toDate : endDate;
        if (lastDate.isBefore(startDate)) {
            return List.of();
        }

        long index = firstIndex(startDate, frequency, intervalCount, fromDate);
        List<LocalDate> dueDates = new ArrayList<>();
        while (true) {
            LocalDate dueDate = dueDate(startDate, frequency, intervalCount, index);
            if (dueDate.isAfter(lastDate)) {
                return dueDates;
            }
            if (!dueDate.isBefore(fromDate)) {
                dueDates.add(dueDate);
            }
            index++;
        }
    }

    private void validate(
            LocalDate startDate,
            ObligationFrequency frequency,
            int intervalCount,
            LocalDate fromDate,
            LocalDate toDate) {
        if (startDate == null || fromDate == null || toDate == null) {
            throw new BusinessException("VALIDATION_ERROR", "Recurrence dates are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("INVALID_DATE_RANGE", "fromDate must be before or equal to toDate");
        }
        if (intervalCount < 1) {
            throw new BusinessException("INVALID_RECURRENCE_INTERVAL", "intervalCount must be at least 1");
        }
        if (frequency == null) {
            throw new BusinessException("INVALID_RECURRENCE_FREQUENCY", "frequency is required");
        }
    }

    private long firstIndex(LocalDate startDate, ObligationFrequency frequency, int intervalCount, LocalDate fromDate) {
        if (!fromDate.isAfter(startDate)) {
            return 0;
        }
        long elapsed = switch (frequency) {
            case WEEKLY -> ChronoUnit.WEEKS.between(startDate, fromDate);
            case MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(fromDate));
            case YEARLY -> ChronoUnit.YEARS.between(startDate, fromDate);
        };
        long index = Math.max(0, elapsed / intervalCount);
        while (dueDate(startDate, frequency, intervalCount, index).isBefore(fromDate)) {
            index++;
        }
        return index;
    }

    private LocalDate dueDate(LocalDate startDate, ObligationFrequency frequency, int intervalCount, long index) {
        long steps = Math.multiplyExact(index, intervalCount);
        return switch (frequency) {
            case WEEKLY -> startDate.plusWeeks(steps);
            case MONTHLY -> monthlyDueDate(startDate, steps);
            case YEARLY -> startDate.plusYears(steps);
        };
    }

    private LocalDate monthlyDueDate(LocalDate startDate, long months) {
        YearMonth month = YearMonth.from(startDate).plusMonths(months);
        return month.atDay(Math.min(startDate.getDayOfMonth(), month.lengthOfMonth()));
    }
}
