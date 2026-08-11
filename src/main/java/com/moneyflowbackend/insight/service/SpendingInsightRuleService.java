package com.moneyflowbackend.insight.service;

import com.moneyflowbackend.insight.dto.FinancialPeriodMetric;
import com.moneyflowbackend.insight.dto.InsightCard;
import com.moneyflowbackend.insight.dto.InsightConfidence;
import com.moneyflowbackend.insight.dto.InsightEvidence;
import com.moneyflowbackend.insight.dto.InsightSeverity;
import com.moneyflowbackend.insight.dto.InsightType;
import com.moneyflowbackend.insight.dto.MetricBreakdownRow;
import com.moneyflowbackend.insight.dto.NoWalletIncomeMetric;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SpendingInsightRuleService {
    private static final int DEFAULT_BASELINE_PERIODS = 3;
    private static final int DEFAULT_MAX_CARDS = 8;
    private static final BigDecimal CATEGORY_RATIO = new BigDecimal("1.35");
    private static final BigDecimal CATEGORY_CRITICAL_RATIO = new BigDecimal("2.00");
    private static final BigDecimal CATEGORY_DELTA = new BigDecimal("200000");
    private static final BigDecimal CATEGORY_CRITICAL_DELTA = new BigDecimal("500000");
    private static final BigDecimal CATEGORY_MIN_CURRENT = new BigDecimal("300000");
    private static final BigDecimal CATEGORY_NEW_HIGH = new BigDecimal("500000");
    private static final BigDecimal JAR_RATIO = new BigDecimal("1.30");
    private static final BigDecimal JAR_CRITICAL_RATIO = new BigDecimal("2.00");
    private static final BigDecimal JAR_DELTA = new BigDecimal("300000");
    private static final BigDecimal JAR_CRITICAL_DELTA = new BigDecimal("1000000");
    private static final BigDecimal JAR_MIN_CURRENT = new BigDecimal("500000");
    private static final BigDecimal SPIKE_RATIO = new BigDecimal("1.30");
    private static final BigDecimal SPIKE_CRITICAL_RATIO = new BigDecimal("1.75");
    private static final BigDecimal SPIKE_DELTA = new BigDecimal("500000");
    private static final BigDecimal SPIKE_CRITICAL_DELTA = new BigDecimal("1000000");
    private static final BigDecimal UNCATEGORIZED_AMOUNT = new BigDecimal("200000");
    private static final BigDecimal UNCATEGORIZED_SHARE = new BigDecimal("15.00");
    private static final BigDecimal CONCENTRATION_TOTAL = new BigDecimal("500000");
    private static final BigDecimal CONCENTRATION_SHARE = new BigDecimal("40.00");
    private static final BigDecimal CONCENTRATION_WARNING_SHARE = new BigDecimal("60.00");

    private final FinancialMetricQueryService metricQueryService;
    private final Clock clock;

    public SpendingInsightRuleService(FinancialMetricQueryService metricQueryService, Clock clock) {
        this.metricQueryService = metricQueryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InsightCard> generate(UUID workspaceId, LocalDate from, LocalDate to) {
        return generate(workspaceId, from, to, DEFAULT_BASELINE_PERIODS, DEFAULT_MAX_CARDS);
    }

    @Transactional(readOnly = true)
    public List<InsightCard> generate(UUID workspaceId, LocalDate from, LocalDate to, int baselinePeriodCount, int maxCards) {
        int baselines = Math.max(1, baselinePeriodCount);
        int limit = Math.max(1, maxCards);
        Instant generatedAt = Instant.now(clock);
        FinancialPeriodMetric current = metricQueryService.getPeriodTotals(workspaceId, from, to);
        List<MetricBreakdownRow> categories = metricQueryService.getExpenseByCategory(workspaceId, from, to);
        List<MetricBreakdownRow> jars = metricQueryService.getExpenseByJar(workspaceId, from, to);
        MetricBreakdownRow uncategorized = metricQueryService.getUncategorizedExpense(workspaceId, from, to);
        NoWalletIncomeMetric noWalletIncome = metricQueryService.getNoWalletIncome(workspaceId, from, to);
        Baseline baseline = baseline(workspaceId, from, to, baselines);

        List<InsightCard> cards = new ArrayList<>();
        categoryOverspend(workspaceId, current, categories, baseline.categoryAverage(), generatedAt, cards);
        jarOverspend(workspaceId, current, jars, baseline.jarAverage(), generatedAt, cards);
        spendingSpike(workspaceId, current, baseline.averageExpense(), generatedAt, cards);
        uncategorized(workspaceId, current, uncategorized, generatedAt, cards);
        concentration(workspaceId, current, categories, generatedAt, cards);
        noWalletIncome(workspaceId, current, noWalletIncome, generatedAt, cards);

        return cards.stream()
                .sorted(Comparator.comparing(SpendingInsightRuleService::severityRank).reversed()
                        .thenComparing(InsightCard::amount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(InsightCard::deterministicKey))
                .limit(limit)
                .toList();
    }

    private void categoryOverspend(UUID workspaceId, FinancialPeriodMetric current, List<MetricBreakdownRow> rows,
                                   Map<Key, BigDecimal> baseline, Instant generatedAt, List<InsightCard> cards) {
        for (MetricBreakdownRow row : rows) {
            if (row.id() == null || row.amount().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal base = baseline.getOrDefault(Key.of(row), BigDecimal.ZERO);
            BigDecimal delta = row.amount().subtract(base);
            BigDecimal ratio = ratio(row.amount(), base);
            InsightSeverity severity = categorySeverity(row.amount(), base, delta, ratio);
            if (severity != null) {
                cards.add(card(workspaceId, InsightType.CATEGORY_OVERSPEND, severity, current, row.amount(), row.id(),
                        "CATEGORY", "Chi tiêu danh mục tăng cao", "%s đã chi %s trong kỳ này.".formatted(row.name(), money(row.amount())),
                        evidence("categoryOverspend", row.amount(), base, delta, ratio, row.percentage(), row.id(), row.name(), null, null, current, row.count()),
                        null, null, generatedAt));
            }
        }
    }

    private InsightSeverity categorySeverity(BigDecimal current, BigDecimal baseline, BigDecimal delta, BigDecimal ratio) {
        if (baseline.compareTo(BigDecimal.ZERO) == 0 && current.compareTo(CATEGORY_NEW_HIGH) >= 0) {
            return InsightSeverity.INFO;
        }
        if (current.compareTo(CATEGORY_MIN_CURRENT) < 0 || delta.compareTo(CATEGORY_DELTA) < 0 || ratio.compareTo(CATEGORY_RATIO) < 0) {
            return null;
        }
        return ratio.compareTo(CATEGORY_CRITICAL_RATIO) >= 0 && delta.compareTo(CATEGORY_CRITICAL_DELTA) >= 0
                ? InsightSeverity.CRITICAL : InsightSeverity.WARNING;
    }

    private void jarOverspend(UUID workspaceId, FinancialPeriodMetric current, List<MetricBreakdownRow> rows,
                              Map<Key, BigDecimal> baseline, Instant generatedAt, List<InsightCard> cards) {
        for (MetricBreakdownRow row : rows) {
            if (row.id() == null || row.amount().compareTo(JAR_MIN_CURRENT) < 0) continue;
            BigDecimal base = baseline.getOrDefault(Key.of(row), BigDecimal.ZERO);
            if (base.compareTo(BigDecimal.ZERO) == 0) continue;
            BigDecimal delta = row.amount().subtract(base);
            BigDecimal ratio = ratio(row.amount(), base);
            if (delta.compareTo(JAR_DELTA) >= 0 && ratio.compareTo(JAR_RATIO) >= 0) {
                InsightSeverity severity = ratio.compareTo(JAR_CRITICAL_RATIO) >= 0 && delta.compareTo(JAR_CRITICAL_DELTA) >= 0
                        ? InsightSeverity.CRITICAL : InsightSeverity.WARNING;
                cards.add(card(workspaceId, InsightType.JAR_OVERSPEND, severity, current, row.amount(), row.id(),
                        "JAR", "Hũ chi tiêu tăng cao", "%s đang dùng %s trong kỳ này.".formatted(row.name(), money(row.amount())),
                        evidence("jarOverspend", row.amount(), base, delta, ratio, row.percentage(), null, null, row.id(), row.name(), current, row.count()),
                        null, null, generatedAt));
            }
        }
    }

    private void spendingSpike(UUID workspaceId, FinancialPeriodMetric current, BigDecimal baseline, Instant generatedAt, List<InsightCard> cards) {
        BigDecimal expense = current.totalExpense();
        BigDecimal delta = expense.subtract(baseline);
        BigDecimal ratio = ratio(expense, baseline);
        if (baseline.compareTo(BigDecimal.ZERO) > 0 && delta.compareTo(SPIKE_DELTA) >= 0 && ratio.compareTo(SPIKE_RATIO) >= 0) {
            InsightSeverity severity = ratio.compareTo(SPIKE_CRITICAL_RATIO) >= 0 && delta.compareTo(SPIKE_CRITICAL_DELTA) >= 0
                    ? InsightSeverity.CRITICAL : InsightSeverity.WARNING;
            cards.add(card(workspaceId, InsightType.SPENDING_SPIKE, severity, current, expense, null,
                    null, "Tổng chi tiêu tăng cao", "Chi tiêu kỳ này cao hơn mức trung bình gần đây.",
                    evidence("spendingSpike", expense, baseline, delta, ratio, percent(expense, baseline), null, null, null, null, current, current.expenseTransactionCount()),
                    null, null, generatedAt));
        }
    }

    private void uncategorized(UUID workspaceId, FinancialPeriodMetric current, MetricBreakdownRow row, Instant generatedAt, List<InsightCard> cards) {
        if (row.amount().compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal share = percent(row.amount(), current.totalExpense());
        boolean warning = row.count() >= 3 || row.amount().compareTo(UNCATEGORIZED_AMOUNT) >= 0 || share.compareTo(UNCATEGORIZED_SHARE) >= 0;
        cards.add(card(workspaceId, InsightType.UNCATEGORIZED_SPENDING, warning ? InsightSeverity.WARNING : InsightSeverity.INFO,
                current, row.amount(), null, null, "Chi tiêu chưa phân loại",
                "Có %d khoản chi chưa phân loại, tổng %s.".formatted(row.count(), money(row.amount())),
                evidence("uncategorizedSpending", row.amount(), BigDecimal.ZERO, row.amount(), BigDecimal.ZERO, share, null, row.name(), null, null, current, row.count()),
                "CLASSIFY_TRANSACTIONS", "Phân loại lại", generatedAt));
    }

    private void concentration(UUID workspaceId, FinancialPeriodMetric current, List<MetricBreakdownRow> categories, Instant generatedAt, List<InsightCard> cards) {
        if (current.totalExpense().compareTo(CONCENTRATION_TOTAL) < 0 || categories.isEmpty()) return;
        MetricBreakdownRow top = categories.getFirst();
        if (top.id() == null || top.percentage().compareTo(CONCENTRATION_SHARE) < 0) return;
        InsightSeverity severity = top.percentage().compareTo(CONCENTRATION_WARNING_SHARE) >= 0 ? InsightSeverity.WARNING : InsightSeverity.INFO;
        cards.add(card(workspaceId, InsightType.SPENDING_CONCENTRATION, severity, current, top.amount(), top.id(),
                "CATEGORY", "Chi tiêu tập trung vào một danh mục", "%s chiếm %s%% tổng chi tiêu kỳ này.".formatted(top.name(), top.percentage()),
                evidence("spendingConcentration", top.amount(), current.totalExpense(), top.amount(), BigDecimal.ZERO, top.percentage(), top.id(), top.name(), null, null, current, top.count()),
                null, null, generatedAt));
    }

    private void noWalletIncome(UUID workspaceId, FinancialPeriodMetric current, NoWalletIncomeMetric metric, Instant generatedAt, List<InsightCard> cards) {
        if (metric.totalAmount().compareTo(BigDecimal.ZERO) <= 0) return;
        cards.add(card(workspaceId, InsightType.NO_WALLET_INCOME, InsightSeverity.INFO, current, metric.totalAmount(),
                null, null, "Thu nhập chưa gắn ví", "Có %s thu nhập chưa gắn vào ví nào.".formatted(money(metric.totalAmount())),
                evidence("noWalletIncome", metric.totalAmount(), BigDecimal.ZERO, metric.totalAmount(), BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null, current, metric.count()),
                "ATTACH_WALLET", "Gắn vào ví", generatedAt));
    }

    private Baseline baseline(UUID workspaceId, LocalDate from, LocalDate to, int count) {
        BigDecimal expense = BigDecimal.ZERO;
        Map<Key, BigDecimal> categories = new HashMap<>();
        Map<Key, BigDecimal> jars = new HashMap<>();
        for (Period period : baselinePeriods(from, to, count)) {
            expense = expense.add(metricQueryService.getPeriodTotals(workspaceId, period.from(), period.to()).totalExpense());
            add(categories, metricQueryService.getExpenseByCategory(workspaceId, period.from(), period.to()));
            add(jars, metricQueryService.getExpenseByJar(workspaceId, period.from(), period.to()));
        }
        return new Baseline(avg(expense, count), avg(categories, count), avg(jars, count));
    }

    private List<Period> baselinePeriods(LocalDate from, LocalDate to, int count) {
        List<Period> periods = new ArrayList<>();
        if (from.getDayOfMonth() == 1 && to.equals(YearMonth.from(to).atEndOfMonth())) {
            YearMonth month = YearMonth.from(from);
            for (int i = 1; i <= count; i++) {
                YearMonth previous = month.minusMonths(i);
                periods.add(new Period(previous.atDay(1), previous.atEndOfMonth()));
            }
            return periods;
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate end = from.minusDays(1);
        for (int i = 0; i < count; i++) {
            LocalDate start = end.minusDays(days - 1);
            periods.add(new Period(start, end));
            end = start.minusDays(1);
        }
        return periods;
    }

    private void add(Map<Key, BigDecimal> totals, List<MetricBreakdownRow> rows) {
        for (MetricBreakdownRow row : rows) {
            if (row.id() != null) totals.merge(Key.of(row), row.amount(), BigDecimal::add);
        }
    }

    private Map<Key, BigDecimal> avg(Map<Key, BigDecimal> totals, int count) {
        Map<Key, BigDecimal> averages = new HashMap<>();
        totals.forEach((key, value) -> averages.put(key, avg(value, count)));
        return averages;
    }

    private BigDecimal avg(BigDecimal value, int count) {
        return value.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private InsightCard card(UUID workspaceId, InsightType type, InsightSeverity severity, FinancialPeriodMetric period,
                             BigDecimal amount, UUID targetEntityId, String targetEntityType, String title, String message,
                             InsightEvidence evidence, String actionType, String actionLabel, Instant generatedAt) {
        return new InsightCard(
                key(workspaceId, type, targetEntityId, period),
                type,
                severity,
                title,
                message,
                amount,
                period.currency(),
                period.from(),
                period.to(),
                List.of(evidence),
                actionType,
                actionLabel,
                targetEntityType,
                targetEntityId,
                severity == InsightSeverity.INFO ? InsightConfidence.MEDIUM : InsightConfidence.HIGH,
                generatedAt);
    }

    private InsightEvidence evidence(String ruleKey, BigDecimal value, BigDecimal baselineValue, BigDecimal delta,
                                     BigDecimal ratio, BigDecimal percentage, UUID categoryId, String categoryName,
                                     UUID jarId, String jarName, FinancialPeriodMetric period, long count) {
        return new InsightEvidence(ruleKey, value, baselineValue, delta, ratio, percentage,
                categoryId, categoryName, jarId, jarName, period.from(), period.to(), ruleKey, count);
    }

    private String key(UUID workspaceId, InsightType type, UUID entityId, FinancialPeriodMetric period) {
        return "%s:%s:%s:%s:%s".formatted(workspaceId, type, entityId == null ? "workspace" : entityId, period.from(), period.to());
    }

    private BigDecimal ratio(BigDecimal current, BigDecimal baseline) {
        if (baseline.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return current.divide(baseline, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return amount.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString() + " VND";
    }

    private static int severityRank(InsightCard card) {
        return switch (card.severity()) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case INFO -> 1;
        };
    }

    private record Period(LocalDate from, LocalDate to) {
    }

    private record Baseline(BigDecimal averageExpense, Map<Key, BigDecimal> categoryAverage, Map<Key, BigDecimal> jarAverage) {
    }

    private record Key(UUID id, String name) {
        static Key of(MetricBreakdownRow row) {
            return new Key(row.id(), row.name());
        }
    }
}
