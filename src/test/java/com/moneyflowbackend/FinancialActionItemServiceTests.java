package com.moneyflowbackend;

import com.moneyflowbackend.insight.dto.*;
import com.moneyflowbackend.insight.service.ActuallySpendableService;
import com.moneyflowbackend.insight.service.FinancialActionItemQueryService;
import com.moneyflowbackend.insight.service.FinancialActionItemService;
import com.moneyflowbackend.insight.service.FinancialMetricQueryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinancialActionItemServiceTests {
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID OTHER_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final LocalDate FROM = LocalDate.parse("2026-08-01");
    private static final LocalDate TO = LocalDate.parse("2026-08-31");
    private static final LocalDate AS_OF = LocalDate.parse("2026-08-11");

    @Test
    void missingCategoryGroupedActionWarnsWhenCountAndAmountHigh() {
        Fixture fx = fixture();
        fx.missingCategory("320000", 4);

        FinancialActionItem item = item(fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF), ActionItemType.TRANSACTION_MISSING_CATEGORY);

        assertThat(item.severity()).isEqualTo(InsightSeverity.WARNING);
        assertThat(item.count()).isEqualTo(4);
        assertThat(item.amount()).isEqualByComparingTo("320000");
        assertThat(item.actionLabel()).isEqualTo("Phân loại lại");
    }

    @Test
    void missingCategorySmallAmountIsInfo() {
        Fixture fx = fixture();
        fx.missingCategory("50000", 1);

        FinancialActionItem item = item(fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF), ActionItemType.TRANSACTION_MISSING_CATEGORY);

        assertThat(item.severity()).isEqualTo(InsightSeverity.INFO);
    }

    @Test
    void incomeWithoutWalletIsInfoNotError() {
        Fixture fx = fixture();
        fx.noWalletIncome("800000", 1);

        FinancialActionItem item = item(fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF), ActionItemType.INCOME_WITHOUT_WALLET);

        assertThat(item.severity()).isEqualTo(InsightSeverity.INFO);
        assertThat(item.message()).contains("không làm tăng số dư ví");
    }

    @Test
    void expenseMissingWalletWarns() {
        Fixture fx = fixture();
        fx.missingWallet("200000", 2);

        FinancialActionItem item = item(fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF), ActionItemType.TRANSACTION_MISSING_WALLET);

        assertThat(item.severity()).isEqualTo(InsightSeverity.WARNING);
    }

    @Test
    void voicePendingDraftsCreateActionItem() {
        Fixture fx = fixture();
        fx.voiceDrafts(3);

        FinancialActionItem item = item(fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF), ActionItemType.VOICE_DRAFT_PENDING);

        assertThat(item.severity()).isEqualTo(InsightSeverity.WARNING);
        assertThat(item.actionLabel()).isEqualTo("Kiểm tra bản nháp");
    }

    @Test
    void receiptPendingAndOcrReviewCreateActionItems() {
        Fixture fx = fixture();
        fx.receiptDrafts(2).ocrReview(1);

        var report = fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF);

        assertThat(report.actionItems()).extracting(FinancialActionItem::type)
                .contains(ActionItemType.RECEIPT_DRAFT_PENDING, ActionItemType.OCR_REVIEW_REQUIRED);
    }

    @Test
    void actuallySpendableNegativeCreatesCriticalItem() {
        Fixture fx = fixture();
        fx.spendable("-100000", List.of(warning(SpendableWarningCode.NEGATIVE_SPENDABLE, InsightSeverity.WARNING)));

        FinancialActionItem item = item(fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF), ActionItemType.NEGATIVE_ACTUALLY_SPENDABLE);

        assertThat(item.severity()).isEqualTo(InsightSeverity.CRITICAL);
    }

    @Test
    void actuallySpendableLowCreatesWarningItem() {
        Fixture fx = fixture();
        fx.spendable("300000", List.of());

        FinancialActionItem item = item(fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF), ActionItemType.LOW_ACTUALLY_SPENDABLE);

        assertThat(item.severity()).isEqualTo(InsightSeverity.WARNING);
    }

    @Test
    void spendableWarningsMapToDataQualityItems() {
        Fixture fx = fixture();
        fx.spendable("1000000", List.of(
                warning(SpendableWarningCode.RESERVE_DATA_UNAVAILABLE, InsightSeverity.INFO),
                warning(SpendableWarningCode.UPCOMING_OBLIGATION_DATA_UNAVAILABLE, InsightSeverity.INFO)));

        var report = fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF);

        assertThat(report.actionItems()).extracting(FinancialActionItem::type)
                .contains(ActionItemType.RESERVE_DATA_MISSING, ActionItemType.UPCOMING_OBLIGATION_DATA_MISSING);
        assertThat(report.dataQualityWarnings()).hasSize(2);
    }

    @Test
    void debtMissingDueDateCreatesActionItem() {
        Fixture fx = fixture();
        fx.debtsMissingDueDate(2);

        FinancialActionItem item = item(fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF), ActionItemType.DEBT_MISSING_DUE_DATE);

        assertThat(item.severity()).isEqualTo(InsightSeverity.WARNING);
    }

    @Test
    void historicalDataExcludedCreatesInfoItem() {
        Fixture fx = fixture();
        fx.historical("1200000", 5);

        FinancialActionItem item = item(fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF), ActionItemType.HISTORICAL_DATA_EXCLUDED_FROM_WALLET);

        assertThat(item.severity()).isEqualTo(InsightSeverity.INFO);
    }

    @Test
    void rankingAndMaxItemsPutCriticalFirst() {
        Fixture fx = fixture();
        fx.missingCategory("320000", 4)
                .voiceDrafts(3)
                .receiptDrafts(2)
                .debtsMissingDueDate(1)
                .spendable("-100000", List.of(warning(SpendableWarningCode.NEGATIVE_SPENDABLE, InsightSeverity.WARNING)));

        List<FinancialActionItem> items = fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF, 3).actionItems();

        assertThat(items).hasSize(3);
        assertThat(items.getFirst().type()).isEqualTo(ActionItemType.NEGATIVE_ACTUALLY_SPENDABLE);
        assertThat(items.getFirst().severity()).isEqualTo(InsightSeverity.CRITICAL);
    }

    @Test
    void workspaceIsolationPassesRequestedWorkspaceOnly() {
        Fixture fx = fixture();
        fx.missingCategory("320000", 4);

        fx.service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF);

        verify(fx.queryService).missingCategoryExpense(WORKSPACE_ID, FROM, TO);
        verify(fx.metricQueryService).getNoWalletIncome(WORKSPACE_ID, FROM, TO);
        verify(fx.actuallySpendableService).calculate(WORKSPACE_ID, AS_OF, 30);
        assertThat(OTHER_WORKSPACE_ID).isNotEqualTo(WORKSPACE_ID);
    }

    @Test
    void emptyStateHasNoActionItems() {
        assertThat(fixture().service.generateActionItems(WORKSPACE_ID, FROM, TO, AS_OF).actionItems()).isEmpty();
    }

    private FinancialActionItem item(FinancialActionItemReport report, ActionItemType type) {
        return report.actionItems().stream()
                .filter(item -> item.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private static SpendableDataQualityWarning warning(SpendableWarningCode code, InsightSeverity severity) {
        return new SpendableDataQualityWarning(code, code.name(), severity, null);
    }

    private static Fixture fixture() {
        return new Fixture();
    }

    private static class Fixture {
        private final FinancialActionItemQueryService queryService = mock(FinancialActionItemQueryService.class);
        private final FinancialMetricQueryService metricQueryService = mock(FinancialMetricQueryService.class);
        private final ActuallySpendableService actuallySpendableService = mock(ActuallySpendableService.class);
        private final FinancialActionItemService service = new FinancialActionItemService(
                queryService,
                metricQueryService,
                actuallySpendableService,
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));

        Fixture() {
            missingCategory("0", 0);
            missingWallet("0", 0);
            historical("0", 0);
            voiceDrafts(0);
            receiptDrafts(0);
            ocrReview(0);
            debtsMissingDueDate(0);
            noWalletIncome("0", 0);
            spendable("1000000", List.of());
        }

        Fixture missingCategory(String amount, long count) {
            when(queryService.missingCategoryExpense(WORKSPACE_ID, FROM, TO)).thenReturn(metric(amount, count));
            return this;
        }

        Fixture missingWallet(String amount, long count) {
            when(queryService.missingWalletExpense(WORKSPACE_ID, FROM, TO)).thenReturn(metric(amount, count));
            return this;
        }

        Fixture historical(String amount, long count) {
            when(queryService.historicalAnalyticsOnly(WORKSPACE_ID, FROM, TO)).thenReturn(metric(amount, count));
            return this;
        }

        Fixture voiceDrafts(long count) {
            when(queryService.pendingVoiceDrafts(WORKSPACE_ID)).thenReturn(count);
            return this;
        }

        Fixture receiptDrafts(long count) {
            when(queryService.pendingReceiptDrafts(WORKSPACE_ID)).thenReturn(count);
            return this;
        }

        Fixture ocrReview(long count) {
            when(queryService.ocrReviewRequired(WORKSPACE_ID)).thenReturn(count);
            return this;
        }

        Fixture debtsMissingDueDate(long count) {
            when(queryService.payableDebtsMissingDueDate(WORKSPACE_ID)).thenReturn(count);
            return this;
        }

        Fixture noWalletIncome(String amount, long count) {
            when(metricQueryService.getNoWalletIncome(WORKSPACE_ID, FROM, TO))
                    .thenReturn(new NoWalletIncomeMetric(new BigDecimal(amount), count, List.of()));
            return this;
        }

        Fixture spendable(String amount, List<SpendableDataQualityWarning> warnings) {
            when(actuallySpendableService.calculate(WORKSPACE_ID, AS_OF, 30))
                    .thenReturn(new ActuallySpendableSnapshot(WORKSPACE_ID, AS_OF, 30, "VND",
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                            new BigDecimal(amount), BigDecimal.ZERO, List.of(), warnings, Instant.parse("2026-08-11T00:00:00Z")));
            return this;
        }

        private FinancialActionMetric metric(String amount, long count) {
            return new FinancialActionMetric(new BigDecimal(amount), count);
        }
    }
}
