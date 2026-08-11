package com.moneyflowbackend;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.insight.controller.FinancialInsightController;
import com.moneyflowbackend.insight.dto.ActionItemType;
import com.moneyflowbackend.insight.dto.ActuallySpendableSnapshot;
import com.moneyflowbackend.insight.dto.FinancialActionItem;
import com.moneyflowbackend.insight.dto.FinancialActionItemReport;
import com.moneyflowbackend.insight.dto.FinancialPeriodMetric;
import com.moneyflowbackend.insight.dto.InsightCard;
import com.moneyflowbackend.insight.dto.InsightConfidence;
import com.moneyflowbackend.insight.dto.InsightEvidence;
import com.moneyflowbackend.insight.dto.InsightSeverity;
import com.moneyflowbackend.insight.dto.InsightType;
import com.moneyflowbackend.insight.dto.MetricBreakdownRow;
import com.moneyflowbackend.insight.dto.NoWalletIncomeMetric;
import com.moneyflowbackend.insight.dto.SpendableDataQualityWarning;
import com.moneyflowbackend.insight.dto.SpendableWarningCode;
import com.moneyflowbackend.insight.service.ActuallySpendableService;
import com.moneyflowbackend.insight.service.FinancialActionItemService;
import com.moneyflowbackend.insight.service.FinancialMetricQueryService;
import com.moneyflowbackend.insight.service.SpendingInsightRuleService;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class FinancialInsightControllerTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final LocalDate FROM = LocalDate.parse("2026-08-01");
    private static final LocalDate TO = LocalDate.parse("2026-08-31");
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void overviewDefaultMonthCombinesMetricsCardsSpendableAndActionItems() {
        Fixture fx = fixture();

        var response = fx.controller.overview(WORKSPACE_ID, null, null, null, null, null).getBody().data();

        assertThat(response.period().from()).isEqualTo(FROM);
        assertThat(response.period().to()).isEqualTo(TO);
        assertThat(response.totals().totalIncome()).isEqualByComparingTo("1000");
        assertThat(response.topInsightCards()).hasSize(1);
        assertThat(response.actuallySpendable().actuallySpendable()).isEqualByComparingTo("700");
        assertThat(response.actionItems()).hasSize(1);
        assertThat(response.dataQualityWarnings()).extracting("code").contains(SpendableWarningCode.PARTIAL_DATA);
        verify(fx.workspaceService).verifyMembership(WORKSPACE_ID, USER_ID);
    }

    @Test
    void overviewCustomRangePassesRangeToServices() {
        Fixture fx = fixture();
        LocalDate from = LocalDate.parse("2026-07-01");
        LocalDate to = LocalDate.parse("2026-07-15");
        fx.stubRange(from, to);

        fx.controller.overview(WORKSPACE_ID, from.toString(), to.toString(), null, 10, 2);

        verify(fx.metricQueryService).getPeriodTotals(WORKSPACE_ID, from, to);
        verify(fx.spendingInsightRuleService).generate(WORKSPACE_ID, from, to, 3, 2);
        verify(fx.actuallySpendableService).calculate(WORKSPACE_ID, null, 10);
        verify(fx.actionItemService).generateActionItems(WORKSPACE_ID, from, to, null);
    }

    @Test
    void metricsEndpointSerializesBreakdownsAndNoWalletIncome() {
        Fixture fx = fixture();

        var response = fx.controller.metrics(WORKSPACE_ID, "2026-08-01", "2026-08-31").getBody().data();

        assertThat(response.incomeBySource()).extracting("name").containsExactly("Salary");
        assertThat(response.expenseByCategory()).extracting("name").containsExactly("Food");
        assertThat(response.expenseByJar()).extracting("name").containsExactly("Needs");
        assertThat(response.noWalletIncome().totalAmount()).isEqualByComparingTo("200");
        assertThat(response.noWalletIncome().message()).contains("thống kê thu nhập");
    }

    @Test
    void cardsEndpointCapsMaxCardsAtTwenty() {
        Fixture fx = fixture();

        fx.controller.cards(WORKSPACE_ID, "2026-08-01", "2026-08-31", 99);

        verify(fx.spendingInsightRuleService).generate(WORKSPACE_ID, FROM, TO, 3, 20);
    }

    @Test
    void actuallySpendableEndpointUsesDefaultAndCustomHorizon() {
        Fixture fx = fixture();

        assertThat(fx.controller.actuallySpendable(WORKSPACE_ID, null, null).getBody().data().horizonDays()).isEqualTo(30);
        fx.controller.actuallySpendable(WORKSPACE_ID, "2026-08-10", 14);

        verify(fx.actuallySpendableService).calculate(WORKSPACE_ID, null, 30);
        verify(fx.actuallySpendableService).calculate(WORKSPACE_ID, LocalDate.parse("2026-08-10"), 14);
    }

    @Test
    void actionItemsEndpointSerializesCleanupItems() {
        Fixture fx = fixture();

        var response = fx.controller.actionItems(WORKSPACE_ID, null, null, null).getBody().data();

        assertThat(response.actionItems()).extracting("type").contains(ActionItemType.INCOME_WITHOUT_WALLET);
        assertThat(response.actionItems().getFirst().targetRoute()).isEqualTo("/transactions?incomeWithoutWallet=true");
    }

    @Test
    void invalidDateRangeAndPeriodReturnBadRequestWithoutServiceCalls() {
        Fixture fx = fixture();

        assertThatThrownBy(() -> fx.controller.metrics(WORKSPACE_ID, "2026-08-31", "2026-08-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("range");
        assertThatThrownBy(() -> fx.controller.overview(WORKSPACE_ID, null, null, "quarter", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_INSIGHT_PERIOD");
        assertThatThrownBy(() -> fx.controller.cards(WORKSPACE_ID, "2026-08-01", "2026-08-31", 0))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INVALID_INSIGHT_PARAMETER");

        verify(fx.metricQueryService, never()).getPeriodTotals(WORKSPACE_ID, LocalDate.parse("2026-08-31"), LocalDate.parse("2026-08-01"));
    }

    @Test
    void workspaceIsolationBlocksBeforeInsightServices() {
        Fixture fx = fixture();
        BusinessException forbidden = new BusinessException("FORBIDDEN", "Denied", org.springframework.http.HttpStatus.FORBIDDEN);
        org.mockito.Mockito.doThrow(forbidden).when(fx.workspaceService).verifyMembership(WORKSPACE_ID, USER_ID);

        assertThatThrownBy(() -> fx.controller.metrics(WORKSPACE_ID, "2026-08-01", "2026-08-31"))
                .isSameAs(forbidden);

        verifyNoMoreInteractions(fx.metricQueryService, fx.spendingInsightRuleService, fx.actuallySpendableService, fx.actionItemService);
    }

    @Test
    void noWalletIncomeCopyIsInformationalNotErrorCopy() {
        Fixture fx = fixture();

        String copy = fx.controller.metrics(WORKSPACE_ID, "2026-08-01", "2026-08-31").getBody().data().noWalletIncome().message();

        assertThat(copy).contains("vẫn tính vào thống kê thu nhập");
        assertThat(copy).doesNotContain("thiếu ví");
    }

    @Test
    void emptyStateReturnsEmptyListsAndZeros() {
        Fixture fx = fixture();
        fx.empty();

        var response = fx.controller.overview(WORKSPACE_ID, null, null, null, null, null).getBody().data();

        assertThat(response.totals().transactionCount()).isZero();
        assertThat(response.topInsightCards()).isEmpty();
        assertThat(response.actionItems()).isEmpty();
    }

    @Test
    void getEndpointsHaveNoWriteSideEffectsInControllerScope() {
        Fixture fx = fixture();

        fx.controller.overview(WORKSPACE_ID, null, null, null, null, null);

        verify(fx.workspaceService).verifyMembership(WORKSPACE_ID, USER_ID);
        verify(fx.metricQueryService).getPeriodTotals(WORKSPACE_ID, FROM, TO);
        verify(fx.spendingInsightRuleService).generate(WORKSPACE_ID, FROM, TO, 3, 8);
        verify(fx.actuallySpendableService).calculate(WORKSPACE_ID, null, 30);
        verify(fx.actionItemService).generateActionItems(WORKSPACE_ID, FROM, TO, null);
    }

    private static Fixture fixture() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID.toString(), null));
        return new Fixture();
    }

    private static class Fixture {
        private final FinancialMetricQueryService metricQueryService = mock(FinancialMetricQueryService.class);
        private final SpendingInsightRuleService spendingInsightRuleService = mock(SpendingInsightRuleService.class);
        private final ActuallySpendableService actuallySpendableService = mock(ActuallySpendableService.class);
        private final FinancialActionItemService actionItemService = mock(FinancialActionItemService.class);
        private final WorkspaceService workspaceService = mock(WorkspaceService.class);
        private final FinancialInsightController controller = new FinancialInsightController(
                metricQueryService,
                spendingInsightRuleService,
                actuallySpendableService,
                actionItemService,
                workspaceService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        Fixture() {
            stubRange(FROM, TO);
        }

        void stubRange(LocalDate from, LocalDate to) {
            when(metricQueryService.getPeriodTotals(WORKSPACE_ID, from, to))
                    .thenReturn(new FinancialPeriodMetric(WORKSPACE_ID, from, to, "VND",
                            bd("1000"), bd("300"), bd("700"), 2, 1, 1));
            when(metricQueryService.getIncomeBySource(WORKSPACE_ID, from, to))
                    .thenReturn(List.of(row("00000000-0000-0000-0000-000000002001", "Salary", "1000", 1, "100")));
            when(metricQueryService.getExpenseByCategory(WORKSPACE_ID, from, to))
                    .thenReturn(List.of(row("00000000-0000-0000-0000-000000002002", "Food", "300", 1, "100")));
            when(metricQueryService.getExpenseByJar(WORKSPACE_ID, from, to))
                    .thenReturn(List.of(row("00000000-0000-0000-0000-000000002003", "Needs", "300", 1, "100")));
            when(metricQueryService.getNoWalletIncome(WORKSPACE_ID, from, to))
                    .thenReturn(new NoWalletIncomeMetric(bd("200"), 1, List.of(UUID.fromString("00000000-0000-0000-0000-000000002004"))));
            when(metricQueryService.getUncategorizedExpense(WORKSPACE_ID, from, to))
                    .thenReturn(new MetricBreakdownRow(null, "Uncategorized", bd("0"), 0, bd("0")));
            when(spendingInsightRuleService.generate(WORKSPACE_ID, from, to, 3, 8)).thenReturn(List.of(card(from, to)));
            when(spendingInsightRuleService.generate(WORKSPACE_ID, from, to, 3, 2)).thenReturn(List.of(card(from, to)));
            when(spendingInsightRuleService.generate(WORKSPACE_ID, from, to, 3, 20)).thenReturn(List.of(card(from, to)));
            when(actuallySpendableService.calculate(WORKSPACE_ID, null, 30)).thenReturn(spendable(null, 30));
            when(actuallySpendableService.calculate(WORKSPACE_ID, null, 10)).thenReturn(spendable(null, 10));
            when(actuallySpendableService.calculate(WORKSPACE_ID, LocalDate.parse("2026-08-10"), 14)).thenReturn(spendable(LocalDate.parse("2026-08-10"), 14));
            when(actionItemService.generateActionItems(WORKSPACE_ID, from, to, null)).thenReturn(actionReport(from, to, List.of(actionItem(from, to))));
        }

        void empty() {
            when(metricQueryService.getPeriodTotals(WORKSPACE_ID, FROM, TO))
                    .thenReturn(new FinancialPeriodMetric(WORKSPACE_ID, FROM, TO, "VND",
                            bd("0"), bd("0"), bd("0"), 0, 0, 0));
            when(metricQueryService.getIncomeBySource(WORKSPACE_ID, FROM, TO)).thenReturn(List.of());
            when(metricQueryService.getExpenseByCategory(WORKSPACE_ID, FROM, TO)).thenReturn(List.of());
            when(metricQueryService.getExpenseByJar(WORKSPACE_ID, FROM, TO)).thenReturn(List.of());
            when(metricQueryService.getNoWalletIncome(WORKSPACE_ID, FROM, TO)).thenReturn(new NoWalletIncomeMetric(bd("0"), 0, List.of()));
            when(spendingInsightRuleService.generate(WORKSPACE_ID, FROM, TO, 3, 8)).thenReturn(List.of());
            when(actionItemService.generateActionItems(WORKSPACE_ID, FROM, TO, null)).thenReturn(actionReport(FROM, TO, List.of()));
        }

        private MetricBreakdownRow row(String id, String name, String amount, long count, String percentage) {
            return new MetricBreakdownRow(UUID.fromString(id), name, bd(amount), count, bd(percentage));
        }

        private InsightCard card(LocalDate from, LocalDate to) {
            return new InsightCard("card-key", InsightType.NO_WALLET_INCOME, InsightSeverity.INFO, "Income without wallet",
                    "Income counts in stats but not wallet balance.", bd("200"), "VND", from, to,
                    List.of(evidence(from, to)), "ATTACH_WALLET", "Attach wallet", "TRANSACTION", null,
                    InsightConfidence.MEDIUM, NOW);
        }

        private ActuallySpendableSnapshot spendable(LocalDate asOf, int horizonDays) {
            return new ActuallySpendableSnapshot(WORKSPACE_ID, asOf == null ? LocalDate.parse("2026-08-11") : asOf, horizonDays, "VND",
                    bd("1000"), bd("100"), bd("200"), bd("0"), bd("700"), bd("50"), List.of(),
                    List.of(new SpendableDataQualityWarning(SpendableWarningCode.PARTIAL_DATA, "Partial data.", InsightSeverity.INFO, null)), NOW);
        }

        private FinancialActionItemReport actionReport(LocalDate from, LocalDate to, List<FinancialActionItem> items) {
            return new FinancialActionItemReport(WORKSPACE_ID, from, to, LocalDate.parse("2026-08-11"), items,
                    List.of(new SpendableDataQualityWarning(SpendableWarningCode.PARTIAL_DATA, "Partial data.", InsightSeverity.INFO, null)), NOW);
        }

        private FinancialActionItem actionItem(LocalDate from, LocalDate to) {
            return new FinancialActionItem("action-key", ActionItemType.INCOME_WITHOUT_WALLET, InsightSeverity.INFO,
                    "Income without wallet", "Counts in income stats, not wallet balance.", 1, bd("200"), "VND",
                    from, to, "TRANSACTION", null, "/transactions?incomeWithoutWallet=true", "Attach wallet", List.of(evidence(from, to)), NOW);
        }

        private InsightEvidence evidence(LocalDate from, LocalDate to) {
            return new InsightEvidence("noWalletIncome", bd("200"), null, null, null, null,
                    null, null, null, null, from, to, "NO_WALLET_INCOME", 1);
        }

        private BigDecimal bd(String value) {
            return new BigDecimal(value);
        }
    }
}
