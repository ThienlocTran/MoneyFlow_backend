package com.moneyflowbackend.insight.controller;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.dto.ApiResponse;
import com.moneyflowbackend.insight.dto.ActuallySpendableSnapshot;
import com.moneyflowbackend.insight.dto.FinancialActionItem;
import com.moneyflowbackend.insight.dto.FinancialActionItemReport;
import com.moneyflowbackend.insight.dto.FinancialPeriodMetric;
import com.moneyflowbackend.insight.dto.InsightCard;
import com.moneyflowbackend.insight.dto.MetricBreakdownRow;
import com.moneyflowbackend.insight.dto.NoWalletIncomeMetric;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.ActuallySpendableResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.FinancialActionItemListResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.FinancialActionItemResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.FinancialInsightOverviewResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.FinancialMetricResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.FinancialTotalsResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.InsightCardListResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.InsightCardResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.MetricBreakdownResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.NoWalletIncomeResponse;
import com.moneyflowbackend.insight.dto.response.FinancialInsightResponses.PeriodResponse;
import com.moneyflowbackend.insight.service.ActuallySpendableService;
import com.moneyflowbackend.insight.service.FinancialActionItemService;
import com.moneyflowbackend.insight.service.FinancialMetricQueryService;
import com.moneyflowbackend.insight.service.SpendingInsightRuleService;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/insights")
public class FinancialInsightController {
    private static final int DEFAULT_HORIZON_DAYS = 30;
    private static final int DEFAULT_MAX_CARDS = 8;
    private static final int MAX_CARDS_CAP = 20;
    private static final String NO_WALLET_INCOME_MESSAGE =
            "Khoản này vẫn tính vào thống kê thu nhập nhưng không làm tăng số dư ví.";

    private final FinancialMetricQueryService metricQueryService;
    private final SpendingInsightRuleService spendingInsightRuleService;
    private final ActuallySpendableService actuallySpendableService;
    private final FinancialActionItemService actionItemService;
    private final WorkspaceService workspaceService;
    private final Clock clock;

    public FinancialInsightController(FinancialMetricQueryService metricQueryService,
                                      SpendingInsightRuleService spendingInsightRuleService,
                                      ActuallySpendableService actuallySpendableService,
                                      FinancialActionItemService actionItemService,
                                      WorkspaceService workspaceService,
                                      Clock clock) {
        this.metricQueryService = metricQueryService;
        this.spendingInsightRuleService = spendingInsightRuleService;
        this.actuallySpendableService = actuallySpendableService;
        this.actionItemService = actionItemService;
        this.workspaceService = workspaceService;
        this.clock = clock;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<FinancialInsightOverviewResponse>> overview(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) Integer horizonDays,
            @RequestParam(required = false) Integer maxCards) {
        verify(workspaceId);
        PeriodResponse range = period(from, to, period);
        int horizon = positiveOrDefault(horizonDays, DEFAULT_HORIZON_DAYS, "horizonDays");
        int cardLimit = maxCards(maxCards);
        FinancialMetricResponse metrics = metricsResponse(workspaceId, range);
        List<InsightCardResponse> cards = spendingInsightRuleService.generate(workspaceId, range.from(), range.to(), 3, cardLimit)
                .stream().map(this::card).toList();
        ActuallySpendableResponse spendable = spendable(actuallySpendableService.calculate(workspaceId, null, horizon));
        FinancialActionItemReport actionReport = actionItemService.generateActionItems(workspaceId, range.from(), range.to(), null);
        FinancialInsightOverviewResponse response = new FinancialInsightOverviewResponse(
                workspaceId,
                range,
                actionReport.generatedAt(),
                metrics.currency(),
                metrics.totals(),
                cards,
                spendable,
                actionReport.actionItems().stream().map(this::actionItem).toList(),
                actionReport.dataQualityWarnings());
        return ResponseEntity.ok(ApiResponse.ok("Financial insights loaded", response));
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<FinancialMetricResponse>> metrics(
            @PathVariable UUID workspaceId,
            @RequestParam String from,
            @RequestParam String to) {
        verify(workspaceId);
        return ResponseEntity.ok(ApiResponse.ok("Financial insight metrics loaded",
                metricsResponse(workspaceId, customPeriod(parseDate(from, "from"), parseDate(to, "to")))));
    }

    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<InsightCardListResponse>> cards(
            @PathVariable UUID workspaceId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer maxCards) {
        verify(workspaceId);
        PeriodResponse range = customPeriod(parseDate(from, "from"), parseDate(to, "to"));
        List<InsightCardResponse> cards = spendingInsightRuleService.generate(workspaceId, range.from(), range.to(), 3, maxCards(maxCards))
                .stream().map(this::card).toList();
        return ResponseEntity.ok(ApiResponse.ok("Financial insight cards loaded", new InsightCardListResponse(workspaceId, range, cards)));
    }

    @GetMapping("/actually-spendable")
    public ResponseEntity<ApiResponse<ActuallySpendableResponse>> actuallySpendable(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String asOfDate,
            @RequestParam(required = false) Integer horizonDays) {
        verify(workspaceId);
        LocalDate asOf = parseOptionalDate(asOfDate, "asOfDate");
        return ResponseEntity.ok(ApiResponse.ok("Actually spendable insight loaded",
                spendable(actuallySpendableService.calculate(workspaceId, asOf, positiveOrDefault(horizonDays, DEFAULT_HORIZON_DAYS, "horizonDays")))));
    }

    @GetMapping("/action-items")
    public ResponseEntity<ApiResponse<FinancialActionItemListResponse>> actionItems(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String asOfDate) {
        verify(workspaceId);
        PeriodResponse range = optionalCustomOrCurrentMonth(from, to);
        LocalDate asOf = parseOptionalDate(asOfDate, "asOfDate");
        FinancialActionItemReport report = actionItemService.generateActionItems(workspaceId, range.from(), range.to(), asOf);
        FinancialActionItemListResponse response = new FinancialActionItemListResponse(
                workspaceId,
                range,
                report.asOfDate(),
                report.actionItems().stream().map(this::actionItem).toList(),
                report.dataQualityWarnings(),
                report.generatedAt());
        return ResponseEntity.ok(ApiResponse.ok("Financial insight action items loaded", response));
    }

    private FinancialMetricResponse metricsResponse(UUID workspaceId, PeriodResponse range) {
        FinancialPeriodMetric totals = metricQueryService.getPeriodTotals(workspaceId, range.from(), range.to());
        return new FinancialMetricResponse(
                workspaceId,
                range,
                totals.currency(),
                totals(totals),
                metricQueryService.getIncomeBySource(workspaceId, range.from(), range.to()).stream().map(this::breakdown).toList(),
                metricQueryService.getExpenseByCategory(workspaceId, range.from(), range.to()).stream().map(this::breakdown).toList(),
                metricQueryService.getExpenseByJar(workspaceId, range.from(), range.to()).stream().map(this::breakdown).toList(),
                noWallet(metricQueryService.getNoWalletIncome(workspaceId, range.from(), range.to())),
                breakdown(metricQueryService.getUncategorizedExpense(workspaceId, range.from(), range.to())));
    }

    private void verify(UUID workspaceId) {
        workspaceService.verifyMembership(workspaceId, currentUserId());
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }

    private PeriodResponse period(String from, String to, String period) {
        if (hasText(from) || hasText(to)) {
            return customPeriod(parseRequiredDate(from, "from"), parseRequiredDate(to, "to"));
        }
        String normalized = hasText(period) ? period.trim().toLowerCase() : "month";
        LocalDate today = LocalDate.now(clock);
        return switch (normalized) {
            case "today" -> new PeriodResponse(today, today, "today");
            case "week" -> new PeriodResponse(today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
                    today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)), "week");
            case "month" -> {
                YearMonth month = YearMonth.from(today);
                yield new PeriodResponse(month.atDay(1), month.atEndOfMonth(), "month");
            }
            case "custom" -> throw bad("INVALID_INSIGHT_DATE_RANGE", "Custom insight period requires from and to.");
            default -> throw bad("INVALID_INSIGHT_PERIOD", "Insight period must be today, week, month, or custom.");
        };
    }

    private PeriodResponse optionalCustomOrCurrentMonth(String from, String to) {
        if (hasText(from) || hasText(to)) {
            return customPeriod(parseRequiredDate(from, "from"), parseRequiredDate(to, "to"));
        }
        return period(null, null, "month");
    }

    private PeriodResponse customPeriod(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw bad("INVALID_INSIGHT_DATE_RANGE", "Insight date range is invalid.");
        }
        return new PeriodResponse(from, to, "custom");
    }

    private LocalDate parseRequiredDate(String raw, String field) {
        if (!hasText(raw)) {
            throw bad("INVALID_INSIGHT_DATE_RANGE", "Both from and to are required.");
        }
        return parseDate(raw, field);
    }

    private LocalDate parseOptionalDate(String raw, String field) {
        return hasText(raw) ? parseDate(raw, field) : null;
    }

    private LocalDate parseDate(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw bad("INVALID_INSIGHT_DATE", field + " must use YYYY-MM-DD.");
        }
    }

    private int positiveOrDefault(Integer value, int defaultValue, String field) {
        int result = value == null ? defaultValue : value;
        if (result <= 0) {
            throw bad("INVALID_INSIGHT_PARAMETER", field + " must be greater than zero.");
        }
        return result;
    }

    private int maxCards(Integer value) {
        return Math.min(positiveOrDefault(value, DEFAULT_MAX_CARDS, "maxCards"), MAX_CARDS_CAP);
    }

    private BusinessException bad(String code, String message) {
        return new BusinessException(code, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private FinancialTotalsResponse totals(FinancialPeriodMetric metric) {
        return new FinancialTotalsResponse(metric.totalIncome(), metric.totalExpense(), metric.netCashflow(),
                metric.transactionCount(), metric.incomeTransactionCount(), metric.expenseTransactionCount());
    }

    private MetricBreakdownResponse breakdown(MetricBreakdownRow row) {
        return new MetricBreakdownResponse(row.id(), row.name(), row.amount(), row.count(), row.percentage());
    }

    private NoWalletIncomeResponse noWallet(NoWalletIncomeMetric metric) {
        return new NoWalletIncomeResponse(metric.totalAmount(), metric.count(), metric.sampleTransactionIds(), NO_WALLET_INCOME_MESSAGE);
    }

    private InsightCardResponse card(InsightCard card) {
        return new InsightCardResponse(card.deterministicKey(), card.type(), card.severity(), card.title(), card.message(),
                card.amount(), card.currency(), card.periodFrom(), card.periodTo(), card.evidence(),
                card.actionLabel(), null, card.confidence());
    }

    private ActuallySpendableResponse spendable(ActuallySpendableSnapshot snapshot) {
        return new ActuallySpendableResponse(snapshot.workspaceId(), snapshot.asOfDate(), snapshot.horizonDays(), snapshot.currency(),
                snapshot.availableLedgerBalance(), snapshot.activeReserveAmount(), snapshot.upcomingRequiredOutflowAmount(),
                snapshot.overdueRequiredOutflowAmount(), snapshot.actuallySpendable(), snapshot.expectedIncomingAmount(),
                snapshot.sourceBreakdowns(), snapshot.dataQualityWarnings(), snapshot.generatedAt());
    }

    private FinancialActionItemResponse actionItem(FinancialActionItem item) {
        return new FinancialActionItemResponse(item.deterministicKey(), item.type(), item.severity(), item.title(), item.message(),
                item.count(), item.amount(), item.currency(), item.actionLabel(), item.targetRoute(), item.evidence(), item.generatedAt());
    }
}
