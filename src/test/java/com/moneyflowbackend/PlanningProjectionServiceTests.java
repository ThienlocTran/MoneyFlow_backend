package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.planning.model.PlannedObligation;
import com.moneyflowbackend.planning.model.PlannedObligationPriority;
import com.moneyflowbackend.planning.model.PlannedObligationStatus;
import com.moneyflowbackend.planning.model.ReserveAllocation;
import com.moneyflowbackend.planning.model.ReserveAllocationStatus;
import com.moneyflowbackend.planning.model.ReservePurposeType;
import com.moneyflowbackend.planning.repository.PlannedObligationRepository;
import com.moneyflowbackend.planning.repository.ReserveAllocationRepository;
import com.moneyflowbackend.planning.service.PlanningProjectionService;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.wallet.service.WalletBalanceService;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanningProjectionServiceTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final LocalDate AS_OF = LocalDate.parse("2026-08-11");

    @Test
    void basicProjectionSubtractsReservesUpcomingAndOverdue() {
        Fixture fx = fixture().walletBalance("5000000");
        fx.reserves(reserve("Reserve", "1000000"));
        fx.obligations(
                obligation("Rent", "2500000", AS_OF.plusDays(7), PlannedObligationPriority.REQUIRED),
                obligation("Old bill", "300000", AS_OF.minusDays(1), PlannedObligationPriority.IMPORTANT));

        var snapshot = fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.availableLedgerBalance()).isEqualByComparingTo("5000000");
        assertThat(snapshot.activeReserveAmount()).isEqualByComparingTo("1000000");
        assertThat(snapshot.upcomingRequiredOutflowAmount()).isEqualByComparingTo("2500000");
        assertThat(snapshot.overdueRequiredOutflowAmount()).isEqualByComparingTo("300000");
        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("1200000");
        assertThat(snapshot.projectedShortfall()).isEqualByComparingTo("0");
    }

    @Test
    void negativeProjectionWarnsAndReportsShortfall() {
        Fixture fx = fixture().walletBalance("2000000");
        fx.reserves(reserve("Reserve", "1000000"));
        fx.obligations(obligation("Rent", "2500000", AS_OF.plusDays(1), PlannedObligationPriority.REQUIRED));

        var snapshot = fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("-1500000");
        assertThat(snapshot.projectedShortfall()).isEqualByComparingTo("1500000");
        assertThat(snapshot.warnings()).extracting("code")
                .contains("NEGATIVE_ACTUALLY_SPENDABLE", "PROJECTED_SHORTFALL");
    }

    @Test
    void lowPositiveSpendableWarns() {
        Fixture fx = fixture().walletBalance("1800000");
        fx.reserves(reserve("Reserve", "1000000"));
        fx.obligations(obligation("Rent", "500000", AS_OF.plusDays(1), PlannedObligationPriority.REQUIRED));

        var snapshot = fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("300000");
        assertThat(snapshot.warnings()).extracting("code").contains("LOW_ACTUALLY_SPENDABLE");
    }

    @Test
    void releasedAndCancelledReservesAreExcludedByRepositoryContract() {
        Fixture fx = fixture().walletBalance("1000000");
        fx.reserves(reserve("Active", "400000"));

        var snapshot = fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.activeReserveAmount()).isEqualByComparingTo("400000");
        verify(fx.reserveRepository).findActiveForProjection(WORKSPACE_ID);
    }

    @Test
    void paidCancelledAndOptionalObligationsAreExcluded() {
        Fixture fx = fixture().walletBalance("5000000");
        fx.obligations(
                obligation("Required", "1000000", AS_OF.plusDays(1), PlannedObligationPriority.REQUIRED),
                obligation("Optional", "9000000", AS_OF.plusDays(1), PlannedObligationPriority.OPTIONAL),
                obligation("Paid", "9000000", AS_OF.plusDays(1), PlannedObligationPriority.REQUIRED, PlannedObligationStatus.PAID),
                obligation("Cancelled", "9000000", AS_OF.plusDays(1), PlannedObligationPriority.REQUIRED, PlannedObligationStatus.CANCELLED));

        var snapshot = fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.upcomingRequiredOutflowAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void overdueAndHorizonBoundariesAreHandled() {
        Fixture fx = fixture().walletBalance("10000000");
        fx.obligations(
                obligation("Overdue", "100000", AS_OF.minusDays(1), PlannedObligationPriority.REQUIRED),
                obligation("Today", "200000", AS_OF, PlannedObligationPriority.REQUIRED),
                obligation("Boundary", "300000", AS_OF.plusDays(30), PlannedObligationPriority.REQUIRED),
                obligation("Future", "9000000", AS_OF.plusDays(31), PlannedObligationPriority.REQUIRED));

        var snapshot = fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.overdueRequiredOutflowAmount()).isEqualByComparingTo("100000");
        assertThat(snapshot.upcomingRequiredOutflowAmount()).isEqualByComparingTo("500000");
        verify(fx.obligationRepository).findProjectionObligations(WORKSPACE_ID, AS_OF.plusDays(30));
    }

    @Test
    void expectedIncomingIsZeroAndNotSpendableWhenNoSafeSourceExists() {
        Fixture fx = fixture().walletBalance("1000000");

        var snapshot = fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.expectedIncomingAmount()).isEqualByComparingTo("0");
        assertThat(snapshot.expectedIncomingBreakdown()).isEmpty();
        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("1000000");
        assertThat(snapshot.warnings()).extracting("code").contains("EXPECTED_INCOME_DATA_UNAVAILABLE");
    }

    @Test
    void emptyStateReturnsZeroSnapshotWithWarnings() {
        Fixture fx = fixture().noWallets();

        var snapshot = fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.availableLedgerBalance()).isEqualByComparingTo("0");
        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("0");
        assertThat(snapshot.warnings()).extracting("code")
                .contains("WALLET_BALANCE_SOURCE_UNCLEAR", "PARTIAL_PLANNING_DATA", "LOW_ACTUALLY_SPENDABLE");
    }

    @Test
    void workspaceIsolationUsesOnlyRequestedWorkspace() {
        Fixture fx = fixture().walletBalance("1000000");

        fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        verify(fx.walletRepository).findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(WORKSPACE_ID);
        verify(fx.reserveRepository).findActiveForProjection(WORKSPACE_ID);
        verify(fx.obligationRepository).findProjectionObligations(WORKSPACE_ID, AS_OF.plusDays(30));
        assertThat(OTHER_WORKSPACE_ID).isNotEqualTo(WORKSPACE_ID);
    }

    @Test
    void projectionHasNoRepositorySideEffects() {
        Fixture fx = fixture().walletBalance("1000000");
        fx.reserves(reserve("Reserve", "100000"));
        fx.obligations(obligation("Rent", "200000", AS_OF.plusDays(1), PlannedObligationPriority.REQUIRED));

        fx.service.calculateProjection(WORKSPACE_ID, AS_OF, 30);

        verify(fx.reserveRepository, never()).save(any());
        verify(fx.obligationRepository, never()).save(any());
        assertThat(fx.reserve.getStatus()).isEqualTo(ReserveAllocationStatus.ACTIVE);
        assertThat(fx.obligation.getStatus()).isEqualTo(PlannedObligationStatus.PLANNED);
    }

    private static Fixture fixture() {
        return new Fixture();
    }

    private static ReserveAllocation reserve(String name, String amount) {
        Workspace workspace = workspace();
        return ReserveAllocation.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .createdByUser(workspace.getCreatedByUser())
                .name(name)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .status(ReserveAllocationStatus.ACTIVE)
                .purposeType(ReservePurposeType.CUSTOM)
                .build();
    }

    private static PlannedObligation obligation(String name, String amount, LocalDate dueDate, PlannedObligationPriority priority) {
        return obligation(name, amount, dueDate, priority, PlannedObligationStatus.PLANNED);
    }

    private static PlannedObligation obligation(String name, String amount, LocalDate dueDate,
                                                PlannedObligationPriority priority, PlannedObligationStatus status) {
        Workspace workspace = workspace();
        return PlannedObligation.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .createdByUser(workspace.getCreatedByUser())
                .name(name)
                .amount(new BigDecimal(amount))
                .currency("VND")
                .dueDate(dueDate)
                .priority(priority)
                .status(status)
                .build();
    }

    private static Workspace workspace() {
        return Workspace.builder()
                .id(WORKSPACE_ID)
                .name("Workspace")
                .currency("VND")
                .createdByUser(User.builder().username("u").email("u@example.com").fullName("User").build())
                .build();
    }

    private static class Fixture {
        private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        private final WalletRepository walletRepository = mock(WalletRepository.class);
        private final WalletBalanceService walletBalanceService = mock(WalletBalanceService.class);
        private final ReserveAllocationRepository reserveRepository = mock(ReserveAllocationRepository.class);
        private final PlannedObligationRepository obligationRepository = mock(PlannedObligationRepository.class);
        private final Wallet wallet = Wallet.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000801"))
                .name("Cash")
                .walletType(WalletType.CASH)
                .includeInTotal(true)
                .isActive(true)
                .build();
        private ReserveAllocation reserve;
        private PlannedObligation obligation;
        private final PlanningProjectionService service = new PlanningProjectionService(
                workspaceRepository,
                walletRepository,
                walletBalanceService,
                reserveRepository,
                obligationRepository,
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));

        Fixture() {
            when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace()));
            walletBalance("0");
            reserves();
            obligations();
        }

        Fixture walletBalance(String amount) {
            when(walletRepository.findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(WORKSPACE_ID))
                    .thenReturn(List.of(wallet));
            when(walletBalanceService.calculateCurrentBalances(List.of(wallet)))
                    .thenReturn(Map.of(wallet.getId(), new BigDecimal(amount)));
            return this;
        }

        Fixture noWallets() {
            when(walletRepository.findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(WORKSPACE_ID))
                    .thenReturn(List.of());
            when(walletBalanceService.calculateCurrentBalances(List.of())).thenReturn(Map.of());
            return this;
        }

        Fixture reserves(ReserveAllocation... reserves) {
            if (reserves.length > 0) this.reserve = reserves[0];
            when(reserveRepository.findActiveForProjection(WORKSPACE_ID)).thenReturn(List.of(reserves));
            return this;
        }

        Fixture obligations(PlannedObligation... obligations) {
            if (obligations.length > 0) this.obligation = obligations[0];
            when(obligationRepository.findProjectionObligations(WORKSPACE_ID, AS_OF.plusDays(30))).thenReturn(List.of(obligations));
            return this;
        }
    }
}
