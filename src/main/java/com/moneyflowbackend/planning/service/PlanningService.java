package com.moneyflowbackend.planning.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.planning.dto.ActuallySpendableResponse;
import com.moneyflowbackend.planning.dto.AdvisoryCommitmentsResponse;
import com.moneyflowbackend.planning.dto.CommitmentBreakdownResponse;
import com.moneyflowbackend.planning.dto.ReserveBreakdownResponse;
import com.moneyflowbackend.planning.dto.SelectedWalletResponse;
import com.moneyflowbackend.planning.model.PlanningHorizon;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletBalanceService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlanningService {
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";

    private final WorkspaceService workspaceService;
    private final WorkspaceRepository workspaceRepository;
    private final WalletRepository walletRepository;
    private final WalletBalanceService walletBalanceService;
    private final Clock clock;

    public PlanningService(
            WorkspaceService workspaceService,
            WorkspaceRepository workspaceRepository,
            WalletRepository walletRepository,
            WalletBalanceService walletBalanceService,
            Clock clock) {
        this.workspaceService = workspaceService;
        this.workspaceRepository = workspaceRepository;
        this.walletRepository = walletRepository;
        this.walletBalanceService = walletBalanceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ActuallySpendableResponse actuallySpendable(UUID workspaceId, UUID userId, PlanningHorizon horizon, LocalDate from, LocalDate to, List<UUID> walletIds) {
        workspaceService.verifyMembership(workspaceId, userId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        Range range = resolveRange(workspace, horizon == null ? PlanningHorizon.CURRENT_MONTH : horizon, from, to);
        List<Wallet> wallets = selectWallets(workspaceId, walletIds);
        Map<UUID, BigDecimal> balances = walletBalanceService.calculateCurrentBalances(wallets);
        BigDecimal availableLedger = wallets.stream()
                .map(wallet -> balances.getOrDefault(wallet.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SelectedWalletResponse> selectedWallets = wallets.stream()
                .map(wallet -> new SelectedWalletResponse(wallet.getId(), wallet.getName(), wallet.isIncludeInTotal(), balances.getOrDefault(wallet.getId(), BigDecimal.ZERO)))
                .toList();
        List<String> assumptions = new ArrayList<>();
        assumptions.add("B1 foundation only: actuallySpendable equals availableLedger until reserves and commitments are enabled.");
        assumptions.add("availableLedger uses WalletBalanceService current ledger balance for selected wallets.");
        return new ActuallySpendableResponse(
                workspaceId,
                clock.instant(),
                range.horizon(),
                range.from(),
                range.to(),
                selectedWallets,
                availableLedger,
                zeroReserves(),
                new CommitmentBreakdownResponse(BigDecimal.ZERO, 0),
                new AdvisoryCommitmentsResponse(List.of(), BigDecimal.ZERO, false),
                availableLedger,
                false,
                List.of(),
                assumptions);
    }

    private Range resolveRange(Workspace workspace, PlanningHorizon horizon, LocalDate from, LocalDate to) {
        if (horizon == PlanningHorizon.CURRENT_MONTH) {
            LocalDate today = LocalDate.now(clock.withZone(zone(workspace.getTimezone())));
            LocalDate start = today.withDayOfMonth(1);
            return new Range(horizon, start, start.plusMonths(1).minusDays(1));
        }
        if (from == null || to == null) {
            throw new BusinessException("INVALID_PLANNING_HORIZON", "CUSTOM horizon requires from and to");
        }
        if (to.isBefore(from)) {
            throw new BusinessException("INVALID_PLANNING_HORIZON", "CUSTOM horizon to must be on or after from");
        }
        return new Range(horizon, from, to);
    }

    private List<Wallet> selectWallets(UUID workspaceId, List<UUID> walletIds) {
        if (walletIds == null || walletIds.isEmpty()) {
            return walletRepository.findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(workspaceId);
        }
        List<Wallet> wallets = walletRepository.findAllByWorkspaceIdAndIdInAndIsActiveTrue(workspaceId, walletIds);
        if (wallets.size() != walletIds.stream().distinct().count()) {
            throw new BusinessException("WALLET_NOT_FOUND", "One or more wallets are missing or inaccessible", HttpStatus.NOT_FOUND);
        }
        return walletIds.stream()
                .distinct()
                .map(id -> wallets.stream().filter(wallet -> wallet.getId().equals(id)).findFirst().orElseThrow())
                .toList();
    }

    private ReserveBreakdownResponse zeroReserves() {
        return new ReserveBreakdownResponse(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? FALLBACK_ZONE : timezone.trim());
        } catch (Exception ex) {
            return ZoneId.of(FALLBACK_ZONE);
        }
    }

    private record Range(PlanningHorizon horizon, LocalDate from, LocalDate to) {
    }
}
