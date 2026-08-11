package com.moneyflowbackend.planning.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.planning.dto.*;
import com.moneyflowbackend.planning.model.*;
import com.moneyflowbackend.planning.repository.PlannedObligationRepository;
import com.moneyflowbackend.planning.repository.ReserveAllocationRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PlanningOverviewService {
    private static final int DEFAULT_HORIZON_DAYS = 30;
    private static final int MAX_HORIZON_DAYS = 365;
    private static final BigDecimal CRITICAL_OVERDUE_AMOUNT = new BigDecimal("1000000");
    private static final BigDecimal LOW_SPENDABLE_THRESHOLD = new BigDecimal("500000");

    private final WorkspaceService workspaceService;
    private final PlanningProjectionService projectionService;
    private final PlannedObligationRepository obligationRepository;
    private final ReserveAllocationRepository reserveRepository;
    private final Clock clock;

    public PlanningOverviewService(WorkspaceService workspaceService,
                                   PlanningProjectionService projectionService,
                                   PlannedObligationRepository obligationRepository,
                                   ReserveAllocationRepository reserveRepository,
                                   Clock clock) {
        this.workspaceService = workspaceService;
        this.projectionService = projectionService;
        this.obligationRepository = obligationRepository;
        this.reserveRepository = reserveRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlanningOverviewResponse overview(UUID workspaceId, LocalDate asOfDate, Integer horizonDays, boolean includeInactive, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        LocalDate asOf = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        int horizon = horizon(horizonDays);
        PlanningProjectionSnapshot projection = projectionService.calculateProjection(workspaceId, asOf, horizon);
        List<PlannedObligation> obligations = obligationRepository.findOverviewObligations(workspaceId, asOf.plusDays(horizon));
        List<ReserveAllocation> reserves = reserveRepository.findOverviewReserves(workspaceId);
        PlanningObligationSummaryResponse obligationSummary = obligationSummary(obligations, asOf, projection.currency());
        PlanningReserveSummaryResponse reserveSummary = includeInactive
                ? reserveSummary(reserves, projection.currency())
                : activeOnlyReserveSummary(reserves, projection.currency());
        List<PlanningObligationOverviewItem> upcoming = obligations.stream()
                .filter(o -> o.getStatus() == PlannedObligationStatus.PLANNED)
                .filter(o -> !o.getDueDate().isBefore(asOf))
                .filter(o -> !o.getDueDate().isAfter(asOf.plusDays(horizon)))
                .map(o -> obligationItem(o, asOf))
                .toList();
        List<PlanningObligationOverviewItem> overdue = obligations.stream()
                .filter(o -> o.getStatus() == PlannedObligationStatus.PLANNED)
                .filter(o -> o.getDueDate().isBefore(asOf))
                .map(o -> obligationItem(o, asOf))
                .toList();
        List<PlanningReserveOverviewItem> activeReserves = reserves.stream()
                .filter(r -> r.getStatus() == ReserveAllocationStatus.ACTIVE)
                .map(this::reserveItem)
                .toList();
        List<PlanningActionItemResponse> actionItems = actionItems(projection, obligationSummary, activeReserves, asOf);
        return new PlanningOverviewResponse(
                workspaceId,
                projection.asOfDate(),
                projection.horizonDays(),
                projection.generatedAt(),
                projection.currency(),
                projection,
                obligationSummary,
                reserveSummary,
                upcoming,
                overdue,
                activeReserves,
                actionItems,
                projection.warnings());
    }

    @Transactional(readOnly = true)
    public PlanningProjectionSnapshot projection(UUID workspaceId, LocalDate asOfDate, Integer horizonDays, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        return projectionService.calculateProjection(workspaceId, asOfDate == null ? LocalDate.now(clock) : asOfDate, horizon(horizonDays));
    }

    @Transactional(readOnly = true)
    public PlanningObligationSummaryResponse obligationSummary(UUID workspaceId, LocalDate asOfDate, Integer horizonDays, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        LocalDate asOf = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        PlanningProjectionSnapshot projection = projectionService.calculateProjection(workspaceId, asOf, horizon(horizonDays));
        return obligationSummary(obligationRepository.findOverviewObligations(workspaceId, asOf.plusDays(projection.horizonDays())), asOf, projection.currency());
    }

    @Transactional(readOnly = true)
    public PlanningReserveSummaryResponse reserveSummary(UUID workspaceId, boolean includeInactive, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        List<ReserveAllocation> reserves = reserveRepository.findOverviewReserves(workspaceId);
        String currency = reserves.isEmpty() ? "VND" : reserves.get(0).getCurrency();
        return includeInactive ? reserveSummary(reserves, currency) : activeOnlyReserveSummary(reserves, currency);
    }

    private int horizon(Integer horizonDays) {
        int value = horizonDays == null ? DEFAULT_HORIZON_DAYS : horizonDays;
        if (value < 1 || value > MAX_HORIZON_DAYS) {
            throw new BusinessException("INVALID_PLANNING_HORIZON_DAYS", "horizonDays must be between 1 and 365", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private PlanningObligationSummaryResponse obligationSummary(List<PlannedObligation> obligations, LocalDate asOf, String currency) {
        BigDecimal upcomingAmount = BigDecimal.ZERO;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        long upcomingCount = 0;
        long overdueCount = 0;
        long dueSoonCount = 0;
        long paidCount = 0;
        long cancelledCount = 0;
        for (PlannedObligation obligation : obligations) {
            if (obligation.getStatus() == PlannedObligationStatus.PAID) paidCount++;
            if (obligation.getStatus() == PlannedObligationStatus.CANCELLED) cancelledCount++;
            if (obligation.getStatus() != PlannedObligationStatus.PLANNED) continue;
            if (obligation.getDueDate().isBefore(asOf)) {
                overdueAmount = overdueAmount.add(obligation.getAmount());
                overdueCount++;
            } else {
                upcomingAmount = upcomingAmount.add(obligation.getAmount());
                upcomingCount++;
                if (!obligation.getDueDate().isAfter(asOf.plusDays(7))) dueSoonCount++;
            }
        }
        return new PlanningObligationSummaryResponse(upcomingAmount, overdueAmount, upcomingCount, overdueCount, dueSoonCount, paidCount, cancelledCount, currency);
    }

    private PlanningReserveSummaryResponse reserveSummary(List<ReserveAllocation> reserves, String currency) {
        BigDecimal active = BigDecimal.ZERO;
        BigDecimal released = BigDecimal.ZERO;
        BigDecimal cancelled = BigDecimal.ZERO;
        long activeCount = 0;
        long releasedCount = 0;
        long cancelledCount = 0;
        for (ReserveAllocation reserve : reserves) {
            if (reserve.getStatus() == ReserveAllocationStatus.ACTIVE) {
                active = active.add(reserve.getAmount());
                activeCount++;
            } else if (reserve.getStatus() == ReserveAllocationStatus.RELEASED) {
                released = released.add(reserve.getAmount());
                releasedCount++;
            } else if (reserve.getStatus() == ReserveAllocationStatus.CANCELLED) {
                cancelled = cancelled.add(reserve.getAmount());
                cancelledCount++;
            }
        }
        return new PlanningReserveSummaryResponse(active, activeCount, released, releasedCount, cancelled, cancelledCount, currency);
    }

    private PlanningReserveSummaryResponse activeOnlyReserveSummary(List<ReserveAllocation> reserves, String currency) {
        PlanningReserveSummaryResponse summary = reserveSummary(reserves, currency);
        return new PlanningReserveSummaryResponse(summary.activeAmount(), summary.activeCount(), BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, currency);
    }

    private PlanningObligationOverviewItem obligationItem(PlannedObligation obligation, LocalDate asOf) {
        var category = obligation.getCategory();
        Wallet wallet = obligation.getWallet();
        long days = ChronoUnit.DAYS.between(asOf, obligation.getDueDate());
        return new PlanningObligationOverviewItem(
                obligation.getId(),
                obligation.getName(),
                obligation.getAmount(),
                obligation.getCurrency(),
                obligation.getDueDate(),
                Math.max(days, 0),
                Math.max(-days, 0),
                obligation.getPriority(),
                obligation.getStatus(),
                computedState(obligation, asOf),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                wallet == null ? null : wallet.getId(),
                wallet == null ? null : wallet.getName());
    }

    private PlanningReserveOverviewItem reserveItem(ReserveAllocation reserve) {
        Wallet wallet = reserve.getWallet();
        var category = reserve.getCategory();
        var jar = reserve.getJar();
        return new PlanningReserveOverviewItem(
                reserve.getId(),
                reserve.getName(),
                reserve.getAmount(),
                reserve.getCurrency(),
                reserve.getPurposeType(),
                reserve.getTargetDate(),
                wallet == null ? null : wallet.getId(),
                wallet == null ? null : wallet.getName(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                jar == null ? null : jar.getId(),
                jar == null ? null : jar.getName());
    }

    private PlannedObligationComputedState computedState(PlannedObligation obligation, LocalDate asOf) {
        if (obligation.getStatus() == PlannedObligationStatus.PAID) return PlannedObligationComputedState.PAID;
        if (obligation.getStatus() == PlannedObligationStatus.CANCELLED) return PlannedObligationComputedState.CANCELLED;
        if (obligation.getDueDate().isBefore(asOf)) return PlannedObligationComputedState.OVERDUE;
        if (!obligation.getDueDate().isAfter(asOf.plusDays(7))) return PlannedObligationComputedState.DUE_SOON;
        return PlannedObligationComputedState.UPCOMING;
    }

    private List<PlanningActionItemResponse> actionItems(PlanningProjectionSnapshot projection,
                                                         PlanningObligationSummaryResponse obligations,
                                                         List<PlanningReserveOverviewItem> reserves,
                                                         LocalDate asOf) {
        List<PlanningActionItemResponse> items = new ArrayList<>();
        String currency = projection.currency();
        if (obligations.overdueCount() > 0) {
            items.add(item("planning:overdue:" + asOf, "PLANNING_OBLIGATION_OVERDUE",
                    obligations.overdueAmount().compareTo(CRITICAL_OVERDUE_AMOUNT) >= 0 ? "CRITICAL" : "WARNING",
                    "Overdue obligations", "You have overdue planned obligations.", obligations.overdueAmount(), currency, "Review", "/planning/obligations", null));
        }
        if (obligations.dueSoonCount() > 0) {
            items.add(item("planning:due-soon:" + asOf, "PLANNING_OBLIGATION_DUE_SOON", "WARNING",
                    "Obligations due soon", "Planned obligations are due in the next 7 days.", obligations.upcomingAmount(), currency, "Review", "/planning/obligations", null));
        }
        if (projection.projectedShortfall().signum() > 0) {
            items.add(item("planning:shortfall:" + asOf, "PROJECTED_SHORTFALL", "CRITICAL",
                    "Projected shortfall", "Projected actually spendable is below zero.", projection.projectedShortfall(), currency, "Review", "/planning", null));
        } else if (projection.actuallySpendable().compareTo(BigDecimal.ZERO) >= 0 && projection.actuallySpendable().compareTo(LOW_SPENDABLE_THRESHOLD) < 0) {
            items.add(item("planning:low-spendable:" + asOf, "LOW_ACTUALLY_SPENDABLE", "WARNING",
                    "Low actually spendable", "Actually spendable is low.", projection.actuallySpendable(), currency, "Review", "/planning", null));
        }
        if (!reserves.isEmpty()) {
            BigDecimal total = reserves.stream().map(PlanningReserveOverviewItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            items.add(item("planning:active-reserves:" + asOf, "ACTIVE_RESERVE_SUMMARY", "INFO",
                    "Active reserves", "Active reserves are reducing actually spendable.", total, currency, "Review", "/planning/reserves", null));
        }
        if (projection.warnings().stream().anyMatch(w -> "PARTIAL_PLANNING_DATA".equals(w.code()))) {
            items.add(item("planning:partial-data:" + asOf, "PARTIAL_PLANNING_DATA", "INFO",
                    "Partial planning data", "Some planning data is missing, so the projection may be incomplete.", null, currency, "Review", "/planning", null));
        }
        return items;
    }

    private PlanningActionItemResponse item(String key, String type, String severity, String title, String message,
                                            BigDecimal amount, String currency, String actionLabel, String route, UUID entityId) {
        return new PlanningActionItemResponse(key, type, severity, title, message, amount, currency, actionLabel, route, entityId);
    }
}
