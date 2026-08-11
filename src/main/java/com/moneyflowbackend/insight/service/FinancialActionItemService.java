package com.moneyflowbackend.insight.service;

import com.moneyflowbackend.insight.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialActionItemService {
    private static final int DEFAULT_MAX_ITEMS = 10;
    private static final BigDecimal MISSING_CATEGORY_WARNING_AMOUNT = new BigDecimal("200000");
    private static final BigDecimal LOW_SPENDABLE_THRESHOLD = new BigDecimal("500000");

    private final FinancialActionItemQueryService queryService;
    private final FinancialMetricQueryService metricQueryService;
    private final ActuallySpendableService actuallySpendableService;
    private final Clock clock;

    public FinancialActionItemService(FinancialActionItemQueryService queryService,
                                      FinancialMetricQueryService metricQueryService,
                                      ActuallySpendableService actuallySpendableService,
                                      Clock clock) {
        this.queryService = queryService;
        this.metricQueryService = metricQueryService;
        this.actuallySpendableService = actuallySpendableService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinancialActionItemReport generateActionItems(UUID workspaceId, LocalDate from, LocalDate to, LocalDate asOfDate) {
        return generateActionItems(workspaceId, from, to, asOfDate, DEFAULT_MAX_ITEMS);
    }

    @Transactional(readOnly = true)
    public FinancialActionItemReport generateActionItems(UUID workspaceId, LocalDate from, LocalDate to, LocalDate asOfDate, int maxItems) {
        Instant generatedAt = Instant.now(clock);
        LocalDate asOf = asOfDate == null ? LocalDate.now(clock) : asOfDate;
        List<FinancialActionItem> items = new ArrayList<>();
        List<SpendableDataQualityWarning> dataQualityWarnings = new ArrayList<>();

        missingCategory(workspaceId, from, to, generatedAt, items);
        missingWallet(workspaceId, from, to, generatedAt, items);
        noWalletIncome(workspaceId, from, to, generatedAt, items);
        pendingDrafts(workspaceId, from, to, generatedAt, items);
        debtMissingDueDate(workspaceId, from, to, generatedAt, items);
        historicalData(workspaceId, from, to, generatedAt, items);
        spendableWarnings(workspaceId, from, to, asOf, generatedAt, items, dataQualityWarnings);

        List<FinancialActionItem> ranked = items.stream()
                .sorted(Comparator.comparing(FinancialActionItemService::severityRank).reversed()
                        .thenComparing(FinancialActionItemService::weight, Comparator.reverseOrder())
                        .thenComparing(FinancialActionItem::deterministicKey))
                .limit(Math.max(1, maxItems))
                .toList();
        return new FinancialActionItemReport(workspaceId, from, to, asOf, ranked, dataQualityWarnings, generatedAt);
    }

    private void missingCategory(UUID workspaceId, LocalDate from, LocalDate to, Instant generatedAt, List<FinancialActionItem> items) {
        FinancialActionMetric metric = queryService.missingCategoryExpense(workspaceId, from, to);
        if (metric.count() == 0) return;
        InsightSeverity severity = metric.count() >= 3 || metric.amount().compareTo(MISSING_CATEGORY_WARNING_AMOUNT) >= 0
                ? InsightSeverity.WARNING : InsightSeverity.INFO;
        items.add(item(workspaceId, ActionItemType.TRANSACTION_MISSING_CATEGORY, severity, "Chi tiêu chưa phân loại",
                "Có %d khoản chi chưa phân loại, tổng %s.".formatted(metric.count(), money(metric.amount())),
                metric, "TRANSACTION", null, "/transactions?missingCategory=true", "Phân loại lại", from, to, generatedAt));
    }

    private void missingWallet(UUID workspaceId, LocalDate from, LocalDate to, Instant generatedAt, List<FinancialActionItem> items) {
        FinancialActionMetric metric = queryService.missingWalletExpense(workspaceId, from, to);
        if (metric.count() == 0) return;
        items.add(item(workspaceId, ActionItemType.TRANSACTION_MISSING_WALLET, InsightSeverity.WARNING, "Giao dịch cần chọn ví",
                "Có %d giao dịch cần chọn ví để số dư ví chính xác hơn.".formatted(metric.count()),
                metric, "TRANSACTION", null, "/transactions?missingWallet=true", "Chọn ví", from, to, generatedAt));
    }

    private void noWalletIncome(UUID workspaceId, LocalDate from, LocalDate to, Instant generatedAt, List<FinancialActionItem> items) {
        NoWalletIncomeMetric metric = metricQueryService.getNoWalletIncome(workspaceId, from, to);
        if (metric.totalAmount().compareTo(BigDecimal.ZERO) <= 0) return;
        FinancialActionMetric actionMetric = new FinancialActionMetric(metric.totalAmount(), metric.count());
        items.add(item(workspaceId, ActionItemType.INCOME_WITHOUT_WALLET, InsightSeverity.INFO, "Thu nhập chưa gắn ví",
                "Có %s thu nhập chưa gắn vào ví. Khoản này vẫn tính vào thống kê thu nhập nhưng không làm tăng số dư ví.".formatted(money(metric.totalAmount())),
                actionMetric, "TRANSACTION", null, "/transactions?incomeWithoutWallet=true", "Gắn vào ví", from, to, generatedAt));
    }

    private void pendingDrafts(UUID workspaceId, LocalDate from, LocalDate to, Instant generatedAt, List<FinancialActionItem> items) {
        long voice = queryService.pendingVoiceDrafts(workspaceId);
        if (voice > 0) {
            items.add(item(workspaceId, ActionItemType.VOICE_DRAFT_PENDING, voice >= 3 ? InsightSeverity.WARNING : InsightSeverity.INFO,
                    "Bản nháp giọng nói chờ xác nhận", "Có %d bản nháp giọng nói chưa xác nhận.".formatted(voice),
                    new FinancialActionMetric(BigDecimal.ZERO, voice), "VOICE_SESSION", null, "/voice/sessions?status=pending", "Kiểm tra bản nháp", from, to, generatedAt));
        }
        long receipt = queryService.pendingReceiptDrafts(workspaceId);
        if (receipt > 0) {
            items.add(item(workspaceId, ActionItemType.RECEIPT_DRAFT_PENDING, InsightSeverity.INFO,
                    "Hóa đơn chờ kiểm tra", "Có %d hóa đơn OCR cần kiểm tra trước khi ghi sổ.".formatted(receipt),
                    new FinancialActionMetric(BigDecimal.ZERO, receipt), "RECEIPT_SESSION", null, "/receipts?status=pending", "Kiểm tra hóa đơn", from, to, generatedAt));
        }
        long ocr = queryService.ocrReviewRequired(workspaceId);
        if (ocr > 0) {
            items.add(item(workspaceId, ActionItemType.OCR_REVIEW_REQUIRED, InsightSeverity.WARNING,
                    "OCR cần rà soát", "Có %d bản nháp hóa đơn thiếu dữ liệu hoặc độ tin cậy thấp.".formatted(ocr),
                    new FinancialActionMetric(BigDecimal.ZERO, ocr), "RECEIPT_SESSION", null, "/receipts?review=required", "Rà soát OCR", from, to, generatedAt));
        }
    }

    private void debtMissingDueDate(UUID workspaceId, LocalDate from, LocalDate to, Instant generatedAt, List<FinancialActionItem> items) {
        long count = queryService.payableDebtsMissingDueDate(workspaceId);
        if (count == 0) return;
        items.add(item(workspaceId, ActionItemType.DEBT_MISSING_DUE_DATE, InsightSeverity.WARNING,
                "Khoản phải trả thiếu ngày đến hạn", "Có %d khoản phải trả chưa có ngày đến hạn.".formatted(count),
                new FinancialActionMetric(BigDecimal.ZERO, count), "DEBT", null, "/debts?missingDueDate=true", "Bổ sung ngày", from, to, generatedAt));
    }

    private void historicalData(UUID workspaceId, LocalDate from, LocalDate to, Instant generatedAt, List<FinancialActionItem> items) {
        FinancialActionMetric metric = queryService.historicalAnalyticsOnly(workspaceId, from, to);
        if (metric.count() == 0) return;
        items.add(item(workspaceId, ActionItemType.HISTORICAL_DATA_EXCLUDED_FROM_WALLET, InsightSeverity.INFO,
                "Dữ liệu lịch sử không cộng vào ví", "Có dữ liệu lịch sử chỉ dùng cho thống kê, không cộng vào số dư ví.",
                metric, "TRANSACTION", null, null, null, from, to, generatedAt));
    }

    private void spendableWarnings(UUID workspaceId, LocalDate from, LocalDate to, LocalDate asOf, Instant generatedAt,
                                   List<FinancialActionItem> items, List<SpendableDataQualityWarning> warnings) {
        ActuallySpendableSnapshot snapshot = actuallySpendableService.calculate(workspaceId, asOf, 30);
        warnings.addAll(snapshot.dataQualityWarnings());
        for (SpendableDataQualityWarning warning : snapshot.dataQualityWarnings()) {
            if (warning.code() == SpendableWarningCode.RESERVE_DATA_UNAVAILABLE) {
                items.add(item(workspaceId, ActionItemType.RESERVE_DATA_MISSING, InsightSeverity.INFO,
                        "Chưa có dữ liệu tiền giữ riêng", "MoneyFlow chưa có dữ liệu tiền đã giữ riêng nên số có thể chi có thể chưa đủ chính xác.",
                        new FinancialActionMetric(BigDecimal.ZERO, 0), "RESERVE", null, null, null, from, to, generatedAt));
            } else if (warning.code() == SpendableWarningCode.UPCOMING_OBLIGATION_DATA_UNAVAILABLE) {
                items.add(item(workspaceId, ActionItemType.UPCOMING_OBLIGATION_DATA_MISSING, InsightSeverity.INFO,
                        "Chưa có dữ liệu khoản sắp phải trả", "Thêm tiền thuê nhà, hóa đơn hoặc khoản nợ đến hạn để tính tiền còn xài chính xác hơn.",
                        new FinancialActionMetric(BigDecimal.ZERO, 0), "OBLIGATION", null, null, null, from, to, generatedAt));
            } else if (warning.code() == SpendableWarningCode.PARTIAL_DATA) {
                items.add(item(workspaceId, ActionItemType.DATA_QUALITY_PARTIAL, warning.severity(),
                        "Dữ liệu tính toán chưa đầy đủ", warning.message(),
                        new FinancialActionMetric(BigDecimal.ZERO, warning.affectedCount() == null ? 0 : warning.affectedCount()), null, null, null, null, from, to, generatedAt));
            }
        }
        if (snapshot.actuallySpendable().compareTo(BigDecimal.ZERO) < 0) {
            items.add(item(workspaceId, ActionItemType.NEGATIVE_ACTUALLY_SPENDABLE, InsightSeverity.CRITICAL,
                    "Tiền có thể chi đang âm", "Sau khi trừ tiền đã giữ và khoản sắp phải trả, số tiền thật sự còn xài được đang âm.",
                    new FinancialActionMetric(snapshot.actuallySpendable().abs(), 1), null, null, null, null, from, to, generatedAt));
        } else if (snapshot.actuallySpendable().compareTo(LOW_SPENDABLE_THRESHOLD) < 0) {
            items.add(item(workspaceId, ActionItemType.LOW_ACTUALLY_SPENDABLE, InsightSeverity.WARNING,
                    "Tiền có thể chi còn thấp", "Số tiền thật sự còn xài được đang dưới %s.".formatted(money(LOW_SPENDABLE_THRESHOLD)),
                    new FinancialActionMetric(snapshot.actuallySpendable(), 1), null, null, null, null, from, to, generatedAt));
        }
    }

    private FinancialActionItem item(UUID workspaceId, ActionItemType type, InsightSeverity severity, String title, String message,
                                     FinancialActionMetric metric, String targetEntityType, UUID targetEntityId, String targetRoute,
                                     String actionLabel, LocalDate from, LocalDate to, Instant generatedAt) {
        return new FinancialActionItem(
                "%s:%s:%s:%s".formatted(workspaceId, type, from, to),
                type,
                severity,
                title,
                message,
                metric.count(),
                metric.amount(),
                "VND",
                from,
                to,
                targetEntityType,
                targetEntityId,
                targetRoute,
                actionLabel,
                List.of(new InsightEvidence(type.name(), metric.amount(), null, null, null, null, null, null, null, null, from, to, type.name(), metric.count())),
                generatedAt);
    }

    private String money(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString() + " VND";
    }

    private static int severityRank(FinancialActionItem item) {
        return switch (item.severity()) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case INFO -> 1;
        };
    }

    private static BigDecimal weight(FinancialActionItem item) {
        return item.amount() == null || item.amount().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.valueOf(item.count())
                : item.amount();
    }
}
