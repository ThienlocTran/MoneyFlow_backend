package com.moneyflowbackend.insight.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundLedgerEntryRepository;
import com.moneyflowbackend.insight.dto.ActuallySpendableSnapshot;
import com.moneyflowbackend.insight.dto.InsightConfidence;
import com.moneyflowbackend.insight.dto.InsightSeverity;
import com.moneyflowbackend.insight.dto.NoWalletIncomeMetric;
import com.moneyflowbackend.insight.dto.SpendableBreakdownItem;
import com.moneyflowbackend.insight.dto.SpendableDataQualityWarning;
import com.moneyflowbackend.insight.dto.SpendableSourceType;
import com.moneyflowbackend.insight.dto.SpendableWarningCode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.savingsgoal.repository.SavingsGoalLedgerEntryRepository;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundAllocationRepository;
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
public class ActuallySpendableService {
    private static final int DEFAULT_HORIZON_DAYS = 30;

    private final WorkspaceRepository workspaceRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceService walletBalanceService;
    private final SinkingFundAllocationRepository sinkingFundAllocationRepository;
    private final SavingsGoalLedgerEntryRepository savingsGoalLedgerEntryRepository;
    private final EmergencyFundLedgerEntryRepository emergencyFundLedgerEntryRepository;
    private final ObligationOccurrenceRepository obligationOccurrenceRepository;
    private final FinancialMetricQueryService metricQueryService;
    private final Clock clock;

    public ActuallySpendableService(
            WorkspaceRepository workspaceRepository,
            WalletRepository walletRepository,
            WalletBalanceService walletBalanceService,
            SinkingFundAllocationRepository sinkingFundAllocationRepository,
            SavingsGoalLedgerEntryRepository savingsGoalLedgerEntryRepository,
            EmergencyFundLedgerEntryRepository emergencyFundLedgerEntryRepository,
            ObligationOccurrenceRepository obligationOccurrenceRepository,
            FinancialMetricQueryService metricQueryService,
            Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.walletRepository = walletRepository;
        this.walletBalanceService = walletBalanceService;
        this.sinkingFundAllocationRepository = sinkingFundAllocationRepository;
        this.savingsGoalLedgerEntryRepository = savingsGoalLedgerEntryRepository;
        this.emergencyFundLedgerEntryRepository = emergencyFundLedgerEntryRepository;
        this.obligationOccurrenceRepository = obligationOccurrenceRepository;
        this.metricQueryService = metricQueryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ActuallySpendableSnapshot calculate(UUID workspaceId, LocalDate asOfDate, int horizonDays) {
        LocalDate asOf = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        int horizon = horizonDays <= 0 ? DEFAULT_HORIZON_DAYS : horizonDays;
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .filter(ws -> ws.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        List<SpendableDataQualityWarning> warnings = new ArrayList<>();
        List<SpendableBreakdownItem> breakdown = new ArrayList<>();

        BigDecimal availableLedger = availableLedger(workspaceId, breakdown);
        Reserve reserve = reserve(workspaceId, warnings, breakdown);
        ObligationTotals obligations = obligations(workspaceId, asOf, horizon, warnings, breakdown);
        noWalletIncome(workspaceId, asOf, warnings);

        BigDecimal actuallySpendable = availableLedger
                .subtract(reserve.amount())
                .subtract(obligations.upcoming())
                .subtract(obligations.overdue());
        if (actuallySpendable.compareTo(BigDecimal.ZERO) <= 0) {
            warnings.add(warning(SpendableWarningCode.NEGATIVE_SPENDABLE, "Actually spendable is zero or negative.", InsightSeverity.WARNING, null));
        }

        return new ActuallySpendableSnapshot(
                workspaceId,
                asOf,
                horizon,
                workspace.getCurrency() == null ? "VND" : workspace.getCurrency(),
                availableLedger,
                reserve.amount(),
                obligations.upcoming(),
                obligations.overdue(),
                actuallySpendable,
                obligations.expectedIncoming(),
                breakdown,
                warnings,
                clock.instant());
    }

    private BigDecimal availableLedger(UUID workspaceId, List<SpendableBreakdownItem> breakdown) {
        List<Wallet> wallets = walletRepository.findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(workspaceId);
        Map<UUID, BigDecimal> balances = walletBalanceService.calculateCurrentBalances(wallets);
        BigDecimal total = BigDecimal.ZERO;
        for (Wallet wallet : wallets) {
            BigDecimal balance = money(balances.get(wallet.getId()));
            total = total.add(balance);
            breakdown.add(new SpendableBreakdownItem(
                    SpendableSourceType.WALLET_BALANCE,
                    wallet.getId(),
                    wallet.getName(),
                    balance,
                    null,
                    InsightConfidence.HIGH,
                    true));
        }
        return total;
    }

    private Reserve reserve(UUID workspaceId, List<SpendableDataQualityWarning> warnings, List<SpendableBreakdownItem> breakdown) {
        BigDecimal sinking = money(sinkingFundAllocationRepository.sumActiveWorkspaceReservedAmount(workspaceId));
        BigDecimal savings = money(savingsGoalLedgerEntryRepository.sumActiveWorkspaceReservedAmount(workspaceId));
        BigDecimal emergency = money(emergencyFundLedgerEntryRepository.sumActiveWorkspaceReservedAmount(workspaceId));
        addReserve(breakdown, "Active sinking funds", sinking);
        addReserve(breakdown, "Active savings goals", savings);
        addReserve(breakdown, "Active emergency fund", emergency);
        BigDecimal total = sinking.add(savings).add(emergency);
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            warnings.add(warning(SpendableWarningCode.RESERVE_DATA_UNAVAILABLE, "No active reserved money found.", InsightSeverity.INFO, null));
            warnings.add(warning(SpendableWarningCode.PARTIAL_DATA, "Spendable result may be partial if reserves are not configured.", InsightSeverity.INFO, null));
        }
        return new Reserve(total);
    }

    private void addReserve(List<SpendableBreakdownItem> breakdown, String name, BigDecimal amount) {
        breakdown.add(new SpendableBreakdownItem(SpendableSourceType.RESERVE, null, name, amount, null, InsightConfidence.MEDIUM, true));
    }

    private ObligationTotals obligations(UUID workspaceId, LocalDate asOf, int horizonDays, List<SpendableDataQualityWarning> warnings,
                                         List<SpendableBreakdownItem> breakdown) {
        LocalDate upcomingTo = asOf.plusDays(horizonDays);
        List<ObligationOccurrence> upcoming = obligationOccurrenceRepository.findPendingSpendableOccurrences(
                workspaceId, ObligationDirection.PAYABLE, asOf, upcomingTo);
        List<ObligationOccurrence> overdue = obligationOccurrenceRepository.findOverdueSpendableOccurrences(
                workspaceId, ObligationDirection.PAYABLE, asOf);
        List<ObligationOccurrence> incoming = obligationOccurrenceRepository.findPendingSpendableOccurrences(
                workspaceId, ObligationDirection.RECEIVABLE, asOf, upcomingTo);
        Totals upcomingTotals = obligationTotal(upcoming, SpendableSourceType.UPCOMING_OBLIGATION, true, breakdown);
        Totals overdueTotals = obligationTotal(overdue, SpendableSourceType.OVERDUE_OBLIGATION, true, breakdown);
        Totals incomingTotals = obligationTotal(incoming, SpendableSourceType.EXPECTED_INCOMING, false, breakdown);
        if (upcoming.isEmpty() && overdue.isEmpty()) {
            warnings.add(warning(SpendableWarningCode.UPCOMING_OBLIGATION_DATA_UNAVAILABLE, "No pending payable obligations found in horizon.", InsightSeverity.INFO, null));
            warnings.add(warning(SpendableWarningCode.PARTIAL_DATA, "Spendable result may be partial if obligations are not configured.", InsightSeverity.INFO, null));
        }
        long unknown = upcomingTotals.unknown() + overdueTotals.unknown();
        if (unknown > 0) {
            warnings.add(warning(SpendableWarningCode.OBLIGATION_STATUS_UNAVAILABLE, "Some payable obligations have no expected amount and were excluded.", InsightSeverity.WARNING, unknown));
            warnings.add(warning(SpendableWarningCode.PARTIAL_DATA, "Spendable result excludes obligations without known amount.", InsightSeverity.WARNING, unknown));
        }
        return new ObligationTotals(upcomingTotals.amount(), overdueTotals.amount(), incomingTotals.amount());
    }

    private Totals obligationTotal(List<ObligationOccurrence> occurrences, SpendableSourceType sourceType, boolean includedInFormula,
                                   List<SpendableBreakdownItem> breakdown) {
        BigDecimal total = BigDecimal.ZERO;
        long unknown = 0;
        for (ObligationOccurrence occurrence : occurrences) {
            BigDecimal amount = occurrence.getExpectedAmount();
            if (amount == null) {
                unknown++;
                continue;
            }
            total = total.add(amount);
            breakdown.add(new SpendableBreakdownItem(
                    sourceType,
                    occurrence.getId(),
                    occurrence.getTemplate().getName(),
                    amount,
                    occurrence.getDueDate(),
                    InsightConfidence.HIGH,
                    includedInFormula));
        }
        return new Totals(total, unknown);
    }

    private void noWalletIncome(UUID workspaceId, LocalDate asOf, List<SpendableDataQualityWarning> warnings) {
        LocalDate from = asOf.withDayOfMonth(1);
        NoWalletIncomeMetric metric = metricQueryService.getNoWalletIncome(workspaceId, from, asOf);
        if (metric.totalAmount().compareTo(BigDecimal.ZERO) > 0) {
            warnings.add(warning(SpendableWarningCode.NO_WALLET_INCOME_EXCLUDED_FROM_SPENDABLE,
                    "No-wallet income is excluded from actually spendable until attached to a wallet.",
                    InsightSeverity.INFO,
                    metric.count()));
        }
    }

    private SpendableDataQualityWarning warning(SpendableWarningCode code, String message, InsightSeverity severity, Long affectedCount) {
        return new SpendableDataQualityWarning(code, message, severity, affectedCount);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Reserve(BigDecimal amount) {
    }

    private record Totals(BigDecimal amount, long unknown) {
    }

    private record ObligationTotals(BigDecimal upcoming, BigDecimal overdue, BigDecimal expectedIncoming) {
    }
}
