package com.moneyflowbackend;

import com.moneyflowbackend.auth.model.User;
import com.moneyflowbackend.emergencyfund.repository.EmergencyFundLedgerEntryRepository;
import com.moneyflowbackend.insight.dto.SpendableSourceType;
import com.moneyflowbackend.insight.dto.SpendableWarningCode;
import com.moneyflowbackend.insight.service.ActuallySpendableService;
import com.moneyflowbackend.insight.service.FinancialMetricQueryService;
import com.moneyflowbackend.obligation.model.ObligationAmountMode;
import com.moneyflowbackend.obligation.model.ObligationDirection;
import com.moneyflowbackend.obligation.model.ObligationFrequency;
import com.moneyflowbackend.obligation.model.ObligationOccurrence;
import com.moneyflowbackend.obligation.model.RecurringObligationTemplate;
import com.moneyflowbackend.obligation.repository.ObligationOccurrenceRepository;
import com.moneyflowbackend.savingsgoal.repository.SavingsGoalLedgerEntryRepository;
import com.moneyflowbackend.sinkingfund.repository.SinkingFundAllocationRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialInsightActuallySpendableServiceTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000502");
    private static final LocalDate AS_OF = LocalDate.parse("2026-08-11");

    @Test
    void basicActuallySpendableSubtractsReservesUpcomingAndOverdue() {
        Fixture fx = fixture();
        fx.walletBalance("5000000");
        fx.reserves("400000", "300000", "300000");
        fx.upcoming(payable("Rent", "2000000", AS_OF.plusDays(10)));
        fx.overdue(payable("Old bill", "200000", AS_OF.minusDays(2)));

        var snapshot = fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.availableLedgerBalance()).isEqualByComparingTo("5000000");
        assertThat(snapshot.activeReserveAmount()).isEqualByComparingTo("1000000");
        assertThat(snapshot.upcomingRequiredOutflowAmount()).isEqualByComparingTo("2000000");
        assertThat(snapshot.overdueRequiredOutflowAmount()).isEqualByComparingTo("200000");
        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("1800000");
    }

    @Test
    void noReservesModelAvailableReturnsZeroAndPartialWarnings() {
        Fixture fx = fixture();
        fx.walletBalance("1000000");
        fx.reserves("0", "0", "0");

        var snapshot = fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.activeReserveAmount()).isEqualByComparingTo("0");
        assertThat(snapshot.dataQualityWarnings()).extracting("code")
                .contains(SpendableWarningCode.RESERVE_DATA_UNAVAILABLE, SpendableWarningCode.PARTIAL_DATA);
    }

    @Test
    void noUpcomingObligationsReturnsZeroAndWarning() {
        Fixture fx = fixture();
        fx.walletBalance("1000000").reserves("1", "0", "0");

        var snapshot = fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.upcomingRequiredOutflowAmount()).isEqualByComparingTo("0");
        assertThat(snapshot.dataQualityWarnings()).extracting("code")
                .contains(SpendableWarningCode.UPCOMING_OBLIGATION_DATA_UNAVAILABLE);
    }

    @Test
    void negativeSpendableIsNotClampedAndWarns() {
        Fixture fx = fixture();
        fx.walletBalance("1000000").reserves("500000", "0", "0");
        fx.upcoming(payable("Rent", "1200000", AS_OF.plusDays(5)));

        var snapshot = fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("-700000");
        assertThat(snapshot.dataQualityWarnings()).extracting("code")
                .contains(SpendableWarningCode.NEGATIVE_SPENDABLE);
    }

    @Test
    void noWalletIncomeIsExcludedAndReported() {
        Fixture fx = fixture();
        fx.walletBalance("1000000").reserves("1", "0", "0").noWalletIncome("800000", 1);

        var snapshot = fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("999999");
        assertThat(snapshot.dataQualityWarnings()).extracting("code")
                .contains(SpendableWarningCode.NO_WALLET_INCOME_EXCLUDED_FROM_SPENDABLE);
    }

    @Test
    void expectedIncomingIsInformationalOnly() {
        Fixture fx = fixture();
        fx.walletBalance("1000000").reserves("1", "0", "0");
        fx.incoming(receivable("Expected salary", "2000000", AS_OF.plusDays(8)));

        var snapshot = fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.expectedIncomingAmount()).isEqualByComparingTo("2000000");
        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("999999");
        assertThat(snapshot.sourceBreakdowns())
                .filteredOn(item -> item.sourceType() == SpendableSourceType.EXPECTED_INCOMING)
                .singleElement()
                .extracting("includedInFormula")
                .isEqualTo(false);
    }

    @Test
    void overdueObligationIsSubtractedSeparately() {
        Fixture fx = fixture();
        fx.walletBalance("1000000").reserves("1", "0", "0");
        fx.overdue(payable("Late rent", "300000", AS_OF.minusDays(1)));

        var snapshot = fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.upcomingRequiredOutflowAmount()).isEqualByComparingTo("0");
        assertThat(snapshot.overdueRequiredOutflowAmount()).isEqualByComparingTo("300000");
        assertThat(snapshot.sourceBreakdowns()).extracting("sourceType")
                .contains(SpendableSourceType.OVERDUE_OBLIGATION);
    }

    @Test
    void horizonHandlingUsesAsOfThroughAsOfPlusHorizon() {
        Fixture fx = fixture();
        fx.walletBalance("1000000").reserves("1", "0", "0");

        fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        verify(fx.obligationOccurrenceRepository).findPendingSpendableOccurrences(
                WORKSPACE_ID, ObligationDirection.PAYABLE, AS_OF, AS_OF.plusDays(30));
        verify(fx.obligationOccurrenceRepository).findPendingSpendableOccurrences(
                WORKSPACE_ID, ObligationDirection.RECEIVABLE, AS_OF, AS_OF.plusDays(30));
    }

    @Test
    void workspaceIsolationUsesOnlyRequestedWorkspaceId() {
        Fixture fx = fixture();
        fx.walletBalance("1000000").reserves("1", "0", "0");

        fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        verify(fx.walletRepository).findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(WORKSPACE_ID);
        verify(fx.sinkingFundAllocationRepository).sumActiveWorkspaceReservedAmount(WORKSPACE_ID);
        verify(fx.obligationOccurrenceRepository).findOverdueSpendableOccurrences(WORKSPACE_ID, ObligationDirection.PAYABLE, AS_OF);
        assertThat(OTHER_WORKSPACE_ID).isNotEqualTo(WORKSPACE_ID);
    }

    @Test
    void emptyDataReturnsZerosAndPartialWarnings() {
        Fixture fx = fixture();
        fx.noWallet();
        fx.reserves("0", "0", "0");

        var snapshot = fx.service.calculate(WORKSPACE_ID, AS_OF, 30);

        assertThat(snapshot.availableLedgerBalance()).isEqualByComparingTo("0");
        assertThat(snapshot.actuallySpendable()).isEqualByComparingTo("0");
        assertThat(snapshot.dataQualityWarnings()).extracting("code")
                .contains(SpendableWarningCode.PARTIAL_DATA, SpendableWarningCode.NEGATIVE_SPENDABLE);
    }

    @Test
    void currencyUsesWorkspaceConvention() {
        Fixture fx = fixture();
        fx.walletBalance("1000000").reserves("1", "0", "0");

        assertThat(fx.service.calculate(WORKSPACE_ID, AS_OF, 30).currency()).isEqualTo("VND");
    }

    private static Fixture fixture() {
        return new Fixture();
    }

    private static ObligationOccurrence payable(String name, String amount, LocalDate dueDate) {
        return occurrence(name, amount, dueDate, ObligationDirection.PAYABLE);
    }

    private static ObligationOccurrence receivable(String name, String amount, LocalDate dueDate) {
        return occurrence(name, amount, dueDate, ObligationDirection.RECEIVABLE);
    }

    private static ObligationOccurrence occurrence(String name, String amount, LocalDate dueDate, ObligationDirection direction) {
        Workspace workspace = Workspace.builder().id(WORKSPACE_ID).name("Workspace").createdByUser(User.builder().username("u").email("u@example.com").fullName("User").build()).build();
        return ObligationOccurrence.builder()
                .id(UUID.randomUUID())
                .workspace(workspace)
                .template(RecurringObligationTemplate.builder()
                        .id(UUID.randomUUID())
                        .workspace(workspace)
                        .name(name)
                        .direction(direction)
                        .amountMode(ObligationAmountMode.FIXED)
                        .frequency(ObligationFrequency.MONTHLY)
                        .startDate(AS_OF.minusMonths(1))
                        .createdByUser(workspace.getCreatedByUser())
                        .build())
                .periodKey(dueDate.toString())
                .dueDate(dueDate)
                .expectedAmount(new BigDecimal(amount))
                .build();
    }

    private static class Fixture {
        private final WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        private final WalletRepository walletRepository = mock(WalletRepository.class);
        private final WalletBalanceService walletBalanceService = mock(WalletBalanceService.class);
        private final SinkingFundAllocationRepository sinkingFundAllocationRepository = mock(SinkingFundAllocationRepository.class);
        private final SavingsGoalLedgerEntryRepository savingsGoalLedgerEntryRepository = mock(SavingsGoalLedgerEntryRepository.class);
        private final EmergencyFundLedgerEntryRepository emergencyFundLedgerEntryRepository = mock(EmergencyFundLedgerEntryRepository.class);
        private final ObligationOccurrenceRepository obligationOccurrenceRepository = mock(ObligationOccurrenceRepository.class);
        private final FinancialMetricQueryService metricQueryService = mock(FinancialMetricQueryService.class);
        private final Wallet wallet = Wallet.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000601"))
                .name("Cash")
                .walletType(WalletType.CASH)
                .includeInTotal(true)
                .isActive(true)
                .build();
        private final ActuallySpendableService service = new ActuallySpendableService(
                workspaceRepository,
                walletRepository,
                walletBalanceService,
                sinkingFundAllocationRepository,
                savingsGoalLedgerEntryRepository,
                emergencyFundLedgerEntryRepository,
                obligationOccurrenceRepository,
                metricQueryService,
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));

        Fixture() {
            Workspace workspace = Workspace.builder()
                    .id(WORKSPACE_ID)
                    .name("Workspace")
                    .currency("VND")
                    .createdByUser(User.builder().username("u").email("u@example.com").fullName("User").build())
                    .build();
            when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
            noWallet();
            reserves("0", "0", "0");
            noWalletIncome("0", 0);
            upcoming();
            overdue();
            incoming();
        }

        Fixture walletBalance(String amount) {
            when(walletRepository.findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(WORKSPACE_ID))
                    .thenReturn(List.of(wallet));
            when(walletBalanceService.calculateCurrentBalances(List.of(wallet)))
                    .thenReturn(Map.of(wallet.getId(), new BigDecimal(amount)));
            return this;
        }

        Fixture noWallet() {
            when(walletRepository.findAllByWorkspaceIdAndIsActiveTrueAndIncludeInTotalTrueOrderByCreatedAtAsc(WORKSPACE_ID))
                    .thenReturn(List.of());
            when(walletBalanceService.calculateCurrentBalances(List.of())).thenReturn(Map.of());
            return this;
        }

        Fixture reserves(String sinking, String savings, String emergency) {
            when(sinkingFundAllocationRepository.sumActiveWorkspaceReservedAmount(WORKSPACE_ID)).thenReturn(new BigDecimal(sinking));
            when(savingsGoalLedgerEntryRepository.sumActiveWorkspaceReservedAmount(WORKSPACE_ID)).thenReturn(new BigDecimal(savings));
            when(emergencyFundLedgerEntryRepository.sumActiveWorkspaceReservedAmount(WORKSPACE_ID)).thenReturn(new BigDecimal(emergency));
            return this;
        }

        Fixture upcoming(ObligationOccurrence... occurrences) {
            when(obligationOccurrenceRepository.findPendingSpendableOccurrences(WORKSPACE_ID, ObligationDirection.PAYABLE, AS_OF, AS_OF.plusDays(30)))
                    .thenReturn(List.of(occurrences));
            return this;
        }

        Fixture overdue(ObligationOccurrence... occurrences) {
            when(obligationOccurrenceRepository.findOverdueSpendableOccurrences(WORKSPACE_ID, ObligationDirection.PAYABLE, AS_OF))
                    .thenReturn(List.of(occurrences));
            return this;
        }

        Fixture incoming(ObligationOccurrence... occurrences) {
            when(obligationOccurrenceRepository.findPendingSpendableOccurrences(WORKSPACE_ID, ObligationDirection.RECEIVABLE, AS_OF, AS_OF.plusDays(30)))
                    .thenReturn(List.of(occurrences));
            return this;
        }

        Fixture noWalletIncome(String amount, long count) {
            when(metricQueryService.getNoWalletIncome(WORKSPACE_ID, AS_OF.withDayOfMonth(1), AS_OF))
                    .thenReturn(new com.moneyflowbackend.insight.dto.NoWalletIncomeMetric(new BigDecimal(amount), count, List.of()));
            return this;
        }
    }
}
