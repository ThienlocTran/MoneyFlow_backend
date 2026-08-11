package com.moneyflowbackend;

import com.moneyflowbackend.insight.dto.FinancialPeriodMetric;
import com.moneyflowbackend.insight.dto.InsightCard;
import com.moneyflowbackend.insight.dto.InsightSeverity;
import com.moneyflowbackend.insight.dto.InsightType;
import com.moneyflowbackend.insight.dto.MetricBreakdownRow;
import com.moneyflowbackend.insight.dto.NoWalletIncomeMetric;
import com.moneyflowbackend.insight.service.FinancialMetricQueryService;
import com.moneyflowbackend.insight.service.SpendingInsightRuleService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialInsightRuleServiceTests {
    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID FOOD = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID FUEL = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final UUID NEEDS = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final LocalDate FROM = LocalDate.parse("2026-04-01");
    private static final LocalDate TO = LocalDate.parse("2026-04-30");

    @Test
    void categoryOverspendWarningUsesTrailingThreeMonthBaseline() {
        StubMetrics metrics = base();
        metrics.currentExpense("1350000").currentCategory(row(FOOD, "Food", "1350000", 10, "100"));
        metrics.baselineCategory(FOOD, "Food", "1000000");
        SpendingInsightRuleService service = service(metrics);

        List<InsightCard> cards = service.generate(WORKSPACE, FROM, TO);

        InsightCard card = card(cards, InsightType.CATEGORY_OVERSPEND);
        assertThat(card.severity()).isEqualTo(InsightSeverity.WARNING);
        assertThat(card.evidence().getFirst().baselineValue()).isEqualByComparingTo("1000000");
        assertThat(card.evidence().getFirst().delta()).isEqualByComparingTo("350000");
    }

    @Test
    void categoryOverspendCanBeCritical() {
        StubMetrics metrics = base();
        metrics.currentExpense("2200000").currentCategory(row(FOOD, "Food", "2200000", 10, "100"));
        metrics.baselineCategory(FOOD, "Food", "1000000");

        InsightCard card = card(service(metrics).generate(WORKSPACE, FROM, TO), InsightType.CATEGORY_OVERSPEND);

        assertThat(card.severity()).isEqualTo(InsightSeverity.CRITICAL);
    }

    @Test
    void smallDeltaDoesNotCreateNoisyCategoryCard() {
        StubMetrics metrics = base();
        metrics.currentExpense("1350").currentCategory(row(FOOD, "Food", "1350", 1, "100"));
        metrics.baselineCategory(FOOD, "Food", "1000");

        assertThat(service(metrics).generate(WORKSPACE, FROM, TO))
                .noneMatch(card -> card.type() == InsightType.CATEGORY_OVERSPEND);
    }

    @Test
    void newCategoryHighSpendCreatesInfoCard() {
        StubMetrics metrics = base();
        metrics.currentExpense("600000").currentCategory(row(FOOD, "Food", "600000", 2, "100"));

        InsightCard card = card(service(metrics).generate(WORKSPACE, FROM, TO), InsightType.CATEGORY_OVERSPEND);

        assertThat(card.severity()).isEqualTo(InsightSeverity.INFO);
        assertThat(card.evidence().getFirst().baselineValue()).isEqualByComparingTo("0");
    }

    @Test
    void jarOverspendUsesBaseline() {
        StubMetrics metrics = base();
        metrics.currentExpense("1500000").currentJar(row(NEEDS, "Needs", "1400000", 7, "93.33"));
        metrics.baselineJar(NEEDS, "Needs", "1000000");

        InsightCard card = card(service(metrics).generate(WORKSPACE, FROM, TO), InsightType.JAR_OVERSPEND);

        assertThat(card.severity()).isEqualTo(InsightSeverity.WARNING);
        assertThat(card.evidence().getFirst().jarId()).isEqualTo(NEEDS);
    }

    @Test
    void overallSpendingSpikeUsesBaselineTotals() {
        StubMetrics metrics = base();
        metrics.currentExpense("1600000").baselineExpense("1000000");

        InsightCard card = card(service(metrics).generate(WORKSPACE, FROM, TO), InsightType.SPENDING_SPIKE);

        assertThat(card.severity()).isEqualTo(InsightSeverity.WARNING);
        assertThat(card.evidence().getFirst().delta()).isEqualByComparingTo("600000");
    }

    @Test
    void uncategorizedSpendingWarningHasAction() {
        StubMetrics metrics = base();
        metrics.currentExpense("1000000").uncategorized("300000", 4, "30");

        InsightCard card = card(service(metrics).generate(WORKSPACE, FROM, TO), InsightType.UNCATEGORIZED_SPENDING);

        assertThat(card.severity()).isEqualTo(InsightSeverity.WARNING);
        assertThat(card.actionLabel()).isEqualTo("Phân loại lại");
    }

    @Test
    void spendingConcentrationDetectsTopCategoryShare() {
        StubMetrics metrics = base();
        metrics.currentExpense("1000000")
                .currentCategory(row(FOOD, "Food", "500000", 5, "50"))
                .currentCategory(row(FUEL, "Fuel", "300000", 3, "30"));

        InsightCard card = card(service(metrics).generate(WORKSPACE, FROM, TO), InsightType.SPENDING_CONCENTRATION);

        assertThat(card.severity()).isEqualTo(InsightSeverity.INFO);
        assertThat(card.targetEntityId()).isEqualTo(FOOD);
    }

    @Test
    void noWalletIncomeCreatesInfoCard() {
        StubMetrics metrics = base();
        metrics.currentIncome("800000").noWallet("800000", 1);

        InsightCard card = card(service(metrics).generate(WORKSPACE, FROM, TO), InsightType.NO_WALLET_INCOME);

        assertThat(card.severity()).isEqualTo(InsightSeverity.INFO);
        assertThat(card.evidence().getFirst().value()).isEqualByComparingTo("800000");
    }

    @Test
    void rankingLimitsCardsBySeverityThenAmount() {
        StubMetrics metrics = base();
        metrics.currentExpense("3500000")
                .currentCategory(row(FOOD, "Food", "2200000", 6, "62.86"))
                .currentCategory(row(FUEL, "Fuel", "600000", 3, "17.14"))
                .currentJar(row(NEEDS, "Needs", "2500000", 9, "71.43"))
                .uncategorized("300000", 4, "8.57")
                .noWallet("800000", 1)
                .baselineExpense("1000000")
                .baselineCategory(FOOD, "Food", "1000000")
                .baselineJar(NEEDS, "Needs", "1000000");

        List<InsightCard> cards = service(metrics).generate(WORKSPACE, FROM, TO, 3, 3);

        assertThat(cards).hasSize(3);
        assertThat(cards.get(0).severity()).isEqualTo(InsightSeverity.CRITICAL);
        assertThat(cards).extracting(InsightCard::type)
                .contains(InsightType.CATEGORY_OVERSPEND, InsightType.JAR_OVERSPEND);
    }

    @Test
    void workspaceIsolationPassesRequestedWorkspaceToMetricLayerOnly() {
        StubMetrics metrics = base();
        metrics.currentExpense("600000").currentCategory(row(FOOD, "Food", "600000", 2, "100"));
        metrics.otherWorkspaceCategory(row(FUEL, "Fuel", "9999999", 1, "100"));

        List<InsightCard> cards = service(metrics).generate(WORKSPACE, FROM, TO);

        assertThat(cards).extracting(InsightCard::targetEntityId).doesNotContain(OTHER_WORKSPACE, FUEL);
        assertThat(metrics.workspaceIds).containsOnly(WORKSPACE);
    }

    @Test
    void emptyMetricsReturnNoCards() {
        assertThat(service(base()).generate(WORKSPACE, FROM, TO)).isEmpty();
    }

    private SpendingInsightRuleService service(StubMetrics metrics) {
        return new SpendingInsightRuleService(metrics, Clock.fixed(Instant.parse("2026-04-30T00:00:00Z"), ZoneOffset.UTC));
    }

    private InsightCard card(List<InsightCard> cards, InsightType type) {
        return cards.stream()
                .filter(card -> card.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private StubMetrics base() {
        return new StubMetrics()
                .baselineExpense("0")
                .uncategorized("0", 0, "0")
                .noWallet("0", 0);
    }

    private static MetricBreakdownRow row(UUID id, String name, String amount, long count, String percentage) {
        return new MetricBreakdownRow(id, name, new BigDecimal(amount), count, new BigDecimal(percentage));
    }

    private static class StubMetrics extends FinancialMetricQueryService {
        private final List<UUID> workspaceIds = new ArrayList<>();
        private final Map<PeriodKey, FinancialPeriodMetric> totals = new HashMap<>();
        private final Map<PeriodKey, List<MetricBreakdownRow>> categories = new HashMap<>();
        private final Map<PeriodKey, List<MetricBreakdownRow>> jars = new HashMap<>();
        private MetricBreakdownRow uncategorized = row(null, "Chưa phân loại", "0", 0, "0");
        private NoWalletIncomeMetric noWallet = new NoWalletIncomeMetric(BigDecimal.ZERO, 0, List.of());

        StubMetrics() {
            super(null);
            currentExpense("0");
        }

        StubMetrics currentExpense(String expense) {
            totals.put(key(WORKSPACE, FROM, TO), metric("0", expense, FROM, TO));
            return this;
        }

        StubMetrics currentIncome(String income) {
            FinancialPeriodMetric existing = totals.get(key(WORKSPACE, FROM, TO));
            totals.put(key(WORKSPACE, FROM, TO), metric(income, existing.totalExpense().toPlainString(), FROM, TO));
            return this;
        }

        StubMetrics baselineExpense(String expense) {
            for (LocalDate month : List.of(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01"))) {
                totals.put(key(WORKSPACE, month, month.withDayOfMonth(month.lengthOfMonth())), metric("0", expense, month, month.withDayOfMonth(month.lengthOfMonth())));
            }
            return this;
        }

        StubMetrics currentCategory(MetricBreakdownRow row) {
            categories.computeIfAbsent(key(WORKSPACE, FROM, TO), ignored -> new ArrayList<>()).add(row);
            return this;
        }

        StubMetrics baselineCategory(UUID id, String name, String amount) {
            for (LocalDate month : List.of(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01"))) {
                categories.computeIfAbsent(key(WORKSPACE, month, month.withDayOfMonth(month.lengthOfMonth())), ignored -> new ArrayList<>())
                        .add(row(id, name, amount, 1, "100"));
            }
            return this;
        }

        StubMetrics otherWorkspaceCategory(MetricBreakdownRow row) {
            categories.computeIfAbsent(key(OTHER_WORKSPACE, FROM, TO), ignored -> new ArrayList<>()).add(row);
            return this;
        }

        StubMetrics currentJar(MetricBreakdownRow row) {
            jars.computeIfAbsent(key(WORKSPACE, FROM, TO), ignored -> new ArrayList<>()).add(row);
            return this;
        }

        StubMetrics baselineJar(UUID id, String name, String amount) {
            for (LocalDate month : List.of(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01"))) {
                jars.computeIfAbsent(key(WORKSPACE, month, month.withDayOfMonth(month.lengthOfMonth())), ignored -> new ArrayList<>())
                        .add(row(id, name, amount, 1, "100"));
            }
            return this;
        }

        StubMetrics uncategorized(String amount, long count, String percentage) {
            this.uncategorized = row(null, "Chưa phân loại", amount, count, percentage);
            return this;
        }

        StubMetrics noWallet(String amount, long count) {
            this.noWallet = new NoWalletIncomeMetric(new BigDecimal(amount), count, List.of());
            return this;
        }

        @Override
        public FinancialPeriodMetric getPeriodTotals(UUID workspaceId, LocalDate from, LocalDate to) {
            workspaceIds.add(workspaceId);
            return totals.getOrDefault(key(workspaceId, from, to), metric("0", "0", from, to));
        }

        @Override
        public List<MetricBreakdownRow> getExpenseByCategory(UUID workspaceId, LocalDate from, LocalDate to) {
            workspaceIds.add(workspaceId);
            return categories.getOrDefault(key(workspaceId, from, to), List.of());
        }

        @Override
        public List<MetricBreakdownRow> getExpenseByJar(UUID workspaceId, LocalDate from, LocalDate to) {
            workspaceIds.add(workspaceId);
            return jars.getOrDefault(key(workspaceId, from, to), List.of());
        }

        @Override
        public MetricBreakdownRow getUncategorizedExpense(UUID workspaceId, LocalDate from, LocalDate to) {
            workspaceIds.add(workspaceId);
            return uncategorized;
        }

        @Override
        public NoWalletIncomeMetric getNoWalletIncome(UUID workspaceId, LocalDate from, LocalDate to) {
            workspaceIds.add(workspaceId);
            return noWallet;
        }

        private FinancialPeriodMetric metric(String income, String expense, LocalDate from, LocalDate to) {
            BigDecimal totalIncome = new BigDecimal(income);
            BigDecimal totalExpense = new BigDecimal(expense);
            return new FinancialPeriodMetric(WORKSPACE, from, to, "VND", totalIncome, totalExpense, totalIncome.subtract(totalExpense),
                    totalExpense.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0, totalIncome.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0, totalExpense.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
        }

        private PeriodKey key(UUID workspaceId, LocalDate from, LocalDate to) {
            return new PeriodKey(workspaceId, from, to);
        }
    }

    private record PeriodKey(UUID workspaceId, LocalDate from, LocalDate to) {
    }
}
