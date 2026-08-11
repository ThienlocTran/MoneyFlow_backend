package com.moneyflowbackend.planning.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.planning.dto.PlanningProjectionBreakdownItem;
import com.moneyflowbackend.planning.dto.PlanningProjectionSnapshot;
import com.moneyflowbackend.planning.dto.PlanningProjectionWarning;
import com.moneyflowbackend.planning.model.PlannedObligation;
import com.moneyflowbackend.planning.model.PlannedObligationPriority;
import com.moneyflowbackend.planning.model.PlannedObligationStatus;
import com.moneyflowbackend.planning.model.ReserveAllocation;
import com.moneyflowbackend.planning.repository.PlannedObligationRepository;
import com.moneyflowbackend.planning.repository.ReserveAllocationRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletBalanceService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlanningProjectionService {
    private static final int DEFAULT_HORIZON_DAYS = 30;
    private static final BigDecimal LOW_SPENDABLE_THRESHOLD = new BigDecimal("500000");

    private final WorkspaceRepository workspaceRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceService walletBalanceService;
    private final ReserveAllocationRepository reserveRepository;
    private final PlannedObligationRepository obligationRepository;
    private final Clock clock;

    public PlanningProjectionService(WorkspaceRepository workspaceRepository,
                                     WalletRepository walletRepository,
                                     WalletBalanceService walletBalanceService,
                                     ReserveAllocationRepository reserveRepository,
                                     PlannedObligationRepository obligationRepository,
                                     Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.walletRepository = walletRepository;
        this.walletBalanceService = walletBalanceService;
        this.reserveRepository = reserveRepository;
        this.obligationRepository = obligationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlanningProjectionSnapshot calculateProjection(UUID workspaceId, LocalDate asOfDate, int horizonDays) {
        LocalDate asOf = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        int horizon = horizonDays <= 0 ? DEFAULT_HORIZON_DAYS : horizonDays;
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        String currency = workspace.getCurrency() == null ? "VND" : workspace.getCurrency();
        List<PlanningProjectionWarning> warnings = new ArrayList<>();

        Ledger ledger = availableLedger(workspaceId, currency, warnings);
        Reserve reserve = reserves(workspaceId, currency, warnings);
        Obligation obligation = obligations(workspaceId, asOf, horizon, currency, warnings);

        BigDecimal actuallySpendable = ledger.amount()
                .subtract(reserve.amount())
                .subtract(obligation.upcoming())
                .subtract(obligation.overdue());
        BigDecimal projectedShortfall = actuallySpendable.signum() < 0 ? actuallySpendable.abs() : BigDecimal.ZERO;
        addSpendableWarnings(actuallySpendable, projectedShortfall, warnings);
        warnings.add(warning("EXPECTED_INCOME_DATA_UNAVAILABLE", "INFO", "Expected incoming source is not implemented in P13D.", null));

        return new PlanningProjectionSnapshot(
                workspaceId,
                asOf,
                horizon,
                currency,
                ledger.amount(),
                reserve.amount(),
                obligation.upcoming(),
                obligation.overdue(),
                BigDecimal.ZERO,
                actuallySpendable,
                projectedShortfall,
                reserve.breakdown(),
                obligation.breakdown(),
                List.of(),
                warnings,
                clock.instant());
    }

    @Transactional(readOnly = true)
    public PlanningProjectionSnapshot calculateDefaultProjection(UUID workspaceId) {
        return calculateProjection(workspaceId, LocalDate.now(clock), DEFAULT_HORIZON_DAYS);
    }

    private Ledger availableLedger(UUID workspaceId, String currency, List<PlanningProjectionWarning> warnings) {
        List<Wallet> wallets = walletRepository.findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(workspaceId);
        Map<UUID, BigDecimal> balances = walletBalanceService.calculateCurrentBalances(wallets);
        List<PlanningProjectionBreakdownItem> breakdown = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Wallet wallet : wallets) {
            BigDecimal balance = money(balances.get(wallet.getId()));
            total = total.add(balance);
            breakdown.add(new PlanningProjectionBreakdownItem(
                    "WALLET_BALANCE",
                    wallet.getId(),
                    wallet.getName(),
                    balance,
                    currency,
                    null,
                    null,
                    true,
                    "WalletBalanceService current balance"));
        }
        if (wallets.isEmpty()) {
            warnings.add(warning("WALLET_BALANCE_SOURCE_UNCLEAR", "WARN", "No active include-in-total wallets found.", null));
            warnings.add(warning("PARTIAL_PLANNING_DATA", "WARN", "Projection has no wallet ledger source.", "wallets=0"));
        }
        return new Ledger(total, breakdown);
    }

    private Reserve reserves(UUID workspaceId, String currency, List<PlanningProjectionWarning> warnings) {
        List<PlanningProjectionBreakdownItem> breakdown = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ReserveAllocation reserve : reserveRepository.findActiveForProjection(workspaceId)) {
            total = total.add(reserve.getAmount());
            breakdown.add(new PlanningProjectionBreakdownItem(
                    "RESERVE",
                    reserve.getId(),
                    reserve.getName(),
                    reserve.getAmount(),
                    reserve.getCurrency(),
                    reserve.getTargetDate(),
                    null,
                    true,
                    reserve.getPurposeType().name()));
            if (!currency.equals(reserve.getCurrency())) {
                warnings.add(warning("MIXED_CURRENCY_UNSUPPORTED", "WARN", "Reserve currency differs from workspace currency.", reserve.getId().toString()));
            }
        }
        return new Reserve(total, breakdown);
    }

    private Obligation obligations(UUID workspaceId, LocalDate asOf, int horizonDays, String currency,
                                   List<PlanningProjectionWarning> warnings) {
        LocalDate to = asOf.plusDays(horizonDays);
        List<PlanningProjectionBreakdownItem> breakdown = new ArrayList<>();
        BigDecimal upcoming = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        for (PlannedObligation obligation : obligationRepository.findProjectionObligations(workspaceId, to)) {
            if (obligation.getStatus() != PlannedObligationStatus.PLANNED || obligation.getDueDate().isAfter(to)) {
                continue;
            }
            if (obligation.getPriority() == PlannedObligationPriority.OPTIONAL) {
                continue;
            }
            boolean isOverdue = obligation.getDueDate().isBefore(asOf);
            if (isOverdue) {
                overdue = overdue.add(obligation.getAmount());
            } else {
                upcoming = upcoming.add(obligation.getAmount());
            }
            breakdown.add(new PlanningProjectionBreakdownItem(
                    isOverdue ? "OVERDUE_OBLIGATION" : "UPCOMING_OBLIGATION",
                    obligation.getId(),
                    obligation.getName(),
                    obligation.getAmount(),
                    obligation.getCurrency(),
                    obligation.getDueDate(),
                    obligation.getPriority().name(),
                    true,
                    null));
            if (!currency.equals(obligation.getCurrency())) {
                warnings.add(warning("MIXED_CURRENCY_UNSUPPORTED", "WARN", "Obligation currency differs from workspace currency.", obligation.getId().toString()));
            }
        }
        return new Obligation(upcoming, overdue, breakdown);
    }

    private void addSpendableWarnings(BigDecimal actuallySpendable, BigDecimal projectedShortfall,
                                      List<PlanningProjectionWarning> warnings) {
        if (actuallySpendable.signum() < 0) {
            warnings.add(warning("NEGATIVE_ACTUALLY_SPENDABLE", "WARN", "Actually spendable is negative.", null));
        } else if (actuallySpendable.compareTo(LOW_SPENDABLE_THRESHOLD) < 0) {
            warnings.add(warning("LOW_ACTUALLY_SPENDABLE", "INFO", "Actually spendable is below 500000 VND.", null));
        }
        if (projectedShortfall.signum() > 0) {
            warnings.add(warning("PROJECTED_SHORTFALL", "WARN", "Projection shows a shortfall.", projectedShortfall.toPlainString()));
        }
    }

    private PlanningProjectionWarning warning(String code, String severity, String message, String evidence) {
        return new PlanningProjectionWarning(code, severity, message, evidence);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Ledger(BigDecimal amount, List<PlanningProjectionBreakdownItem> breakdown) {
    }

    private record Reserve(BigDecimal amount, List<PlanningProjectionBreakdownItem> breakdown) {
    }

    private record Obligation(BigDecimal upcoming, BigDecimal overdue, List<PlanningProjectionBreakdownItem> breakdown) {
    }
}
