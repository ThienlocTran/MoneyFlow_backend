package com.moneyflowbackend.voice.service;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.planning.dto.ActuallySpendableResponse;
import com.moneyflowbackend.planning.model.PlanningHorizon;
import com.moneyflowbackend.planning.service.PlanningService;
import com.moneyflowbackend.transaction.model.Transaction;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.voice.dto.VoiceQueryRequest;
import com.moneyflowbackend.voice.dto.VoiceQueryResponse;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class VoiceQueryService {
    private final TransactionRepository transactionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PlanningService planningService;
    private final VoiceQueryIntentClassifier classifier;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public VoiceQueryService(
            TransactionRepository transactionRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            PlanningService planningService,
            VoiceQueryIntentClassifier classifier,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.planningService = planningService;
        this.classifier = classifier;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public VoiceQueryResponse ask(UUID workspaceId, VoiceQueryRequest req, UUID userId) {
        WorkspaceMember member = requireActiveMember(workspaceId, userId);
        Workspace workspace = member.getWorkspace();
        String text = text(req);
        ZoneId zone = resolveZone(req.getTimezone(), workspace);
        LocalDate now = req.getNow() == null ? LocalDate.now(clock.withZone(zone)) : req.getNow().atZoneSameInstant(zone).toLocalDate();
        String normalized = classifier.normalize(text);
        String intent = classifier.isPotentialTransactionCommand(normalized) ? "TRANSACTION_COMMAND_DETECTED" : classifier.classifyIntent(normalized);
        String currency = workspace.getCurrency() == null || workspace.getCurrency().isBlank() ? "VND" : workspace.getCurrency().trim().toUpperCase(Locale.ROOT);

        return switch (intent) {
            case "TODAY_EXPENSE_TOTAL" -> totalExpense(workspaceId, text, now, now, "Hôm nay", currency);
            case "MONTH_EXPENSE_TOTAL" -> totalExpense(workspaceId, text, monthStart(now), monthEnd(now), "Tháng này", currency);
            case "MONTH_INCOME_TOTAL" -> totalIncome(workspaceId, text, monthStart(now), monthEnd(now), currency);
            case "TOP_EXPENSE_CATEGORIES_THIS_MONTH" -> topCategories(workspaceId, text, now, currency);
            case "LARGEST_EXPENSE_THIS_MONTH" -> largestExpense(workspaceId, text, now, currency);
            case "RECEIVABLE_SUMMARY" -> debtSummary(workspaceId, text, "RECEIVABLE_SUMMARY", "RECEIVABLE", "Tổng nợ phải thu", "receivableDebt", currency);
            case "PAYABLE_SUMMARY" -> debtSummary(workspaceId, text, "PAYABLE_SUMMARY", "PAYABLE", "Tổng nợ phải trả", "payableDebt", currency);
            case "ACTUALLY_SPENDABLE_SUMMARY" -> spendable(workspaceId, text, userId, now, currency);
            case "TRANSACTION_COMMAND_DETECTED" -> base(text, intent, "NEEDS_CLARIFICATION", currency)
                    .answerText("Bạn muốn ghi chép giao dịch hay hỏi thông tin? MoneyFlow không tự ghi nhận giao dịch từ câu hỏi để tránh sai số.")
                    .build();
            case "UNSUPPORTED" -> base(text, intent, "UNSUPPORTED", currency)
                    .answerText("MoneyFlow chưa hỗ trợ câu hỏi này.")
                    .build();
            default -> base(text, "UNKNOWN", "NEEDS_CLARIFICATION", currency)
                    .answerText("Bạn muốn xem tổng chi, tổng thu, nợ hay số tiền còn có thể chi?")
                    .build();
        };
    }

    private VoiceQueryResponse totalExpense(UUID workspaceId, String text, LocalDate from, LocalDate to, String label, String currency) {
        BigDecimal amount = transactionRepository.sumPostedExpenseInPeriod(workspaceId, from, to);
        long count = transactionRepository.countPostedExpenseInPeriod(workspaceId, from, to);
        String answer = amount.signum() == 0
                ? label + " bạn chưa có khoản chi nào được ghi nhận."
                : label + " bạn đã chi tổng cộng " + formatMoney(amount) + " " + currency + " với " + count + " giao dịch.";
        return base(text, label.equals("Hôm nay") ? "TODAY_EXPENSE_TOTAL" : "MONTH_EXPENSE_TOTAL", "ANSWERED", currency)
                .answerText(answer)
                .period(period(label, from, to))
                .metrics(List.of(new VoiceQueryResponse.MetricDto("totalExpense", "Đã chi", amount, count)))
                .links(transactionLink(from, to))
                .build();
    }

    private VoiceQueryResponse totalIncome(UUID workspaceId, String text, LocalDate from, LocalDate to, String currency) {
        BigDecimal amount = transactionRepository.sumPostedIncomeInPeriod(workspaceId, from, to);
        String answer = amount.signum() == 0
                ? "Tháng này bạn chưa có khoản thu nào được ghi nhận."
                : "Tổng thu nhập tháng này của bạn là " + formatMoney(amount) + " " + currency + ".";
        return base(text, "MONTH_INCOME_TOTAL", "ANSWERED", currency)
                .answerText(answer)
                .period(period("Tháng này", from, to))
                .metrics(List.of(new VoiceQueryResponse.MetricDto("totalIncome", "Đã thu", amount, null)))
                .links(transactionLink(from, to))
                .build();
    }

    private VoiceQueryResponse topCategories(UUID workspaceId, String text, LocalDate now, String currency) {
        LocalDate from = monthStart(now);
        LocalDate to = monthEnd(now);
        List<VoiceQueryResponse.BreakdownItemDto> items = transactionRepository.sumPostedExpenseByCategoryInPeriod(workspaceId, from, to).stream()
                .map(row -> new VoiceQueryResponse.BreakdownItemDto((String) row[0], (BigDecimal) row[1], ((Number) row[2]).longValue()))
                .toList();
        String answer = items.isEmpty()
                ? "Tháng này bạn chưa có khoản chi nào được phân loại theo danh mục."
                : "Tháng này tiền đi nhiều nhất vào " + items.get(0).getLabel() + ": " + formatMoney(items.get(0).getAmount()) + " " + currency + ".";
        return base(text, "TOP_EXPENSE_CATEGORIES_THIS_MONTH", "ANSWERED", currency)
                .answerText(answer)
                .period(period("Tháng này", from, to))
                .breakdowns(List.of(new VoiceQueryResponse.BreakdownDto("byCategory", "Theo danh mục", items)))
                .links(transactionLink(from, to))
                .build();
    }

    private VoiceQueryResponse largestExpense(UUID workspaceId, String text, LocalDate now, String currency) {
        LocalDate from = monthStart(now);
        LocalDate to = monthEnd(now);
        List<Transaction> largest = transactionRepository.findLargestPostedExpenseInPeriod(workspaceId, from, to, PageRequest.of(0, 1));
        List<VoiceQueryResponse.BreakdownItemDto> items = new ArrayList<>();
        String answer;
        List<VoiceQueryResponse.MetricDto> metrics = new ArrayList<>();
        if (largest.isEmpty()) {
            answer = "Tháng này bạn chưa có khoản chi nào.";
        } else {
            Transaction tx = largest.get(0);
            String label = tx.getCategory() == null ? "Không phân loại" : tx.getCategory().getName();
            String note = tx.getDescription() == null || tx.getDescription().isBlank() ? label : tx.getDescription();
            answer = "Khoản chi lớn nhất tháng này là '" + note + "' trị giá " + formatMoney(tx.getAmount()) + " " + currency
                    + " vào ngày " + tx.getTransactionDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".";
            metrics.add(new VoiceQueryResponse.MetricDto("largestExpense", "Khoản chi lớn nhất", tx.getAmount(), 1L));
            items.add(new VoiceQueryResponse.BreakdownItemDto(label, tx.getAmount(), 1L));
        }
        return base(text, "LARGEST_EXPENSE_THIS_MONTH", "ANSWERED", currency)
                .answerText(answer)
                .period(period("Tháng này", from, to))
                .metrics(metrics)
                .breakdowns(List.of(new VoiceQueryResponse.BreakdownDto("largestExpense", "Khoản chi lớn nhất", items)))
                .links(transactionLink(from, to))
                .build();
    }

    private VoiceQueryResponse debtSummary(UUID workspaceId, String text, String intent, String direction, String label, String metricKey, String currency) {
        List<VoiceQueryResponse.BreakdownItemDto> items = debtRows(workspaceId, direction).stream()
                .map(row -> new VoiceQueryResponse.BreakdownItemDto((String) row.get("person_name"), money(row.get("remaining_amount")), ((Number) row.get("debt_count")).longValue()))
                .toList();
        BigDecimal amount = items.stream().map(VoiceQueryResponse.BreakdownItemDto::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String answer = amount.signum() == 0
                ? "Hiện chưa có " + label.toLowerCase(Locale.ROOT) + " đang mở."
                : label + " còn lại là " + formatMoney(amount) + " " + currency + ".";
        return base(text, intent, "ANSWERED", currency)
                .answerText(answer)
                .metrics(List.of(new VoiceQueryResponse.MetricDto(metricKey, label, amount, null)))
                .breakdowns(List.of(new VoiceQueryResponse.BreakdownDto("byPerson", "Theo người", items)))
                .links(List.of(new VoiceQueryResponse.LinkDto("Quản lý công nợ", "/debts")))
                .build();
    }

    private VoiceQueryResponse spendable(UUID workspaceId, String text, UUID userId, LocalDate now, String currency) {
        ActuallySpendableResponse spendable = planningService.actuallySpendable(workspaceId, userId, PlanningHorizon.CURRENT_MONTH, null, null, null);
        if (spendable == null || spendable.actuallySpendable() == null) {
            return base(text, "ACTUALLY_SPENDABLE_SUMMARY", "UNSUPPORTED", currency)
                    .answerText("MoneyFlow chưa có số liệu đủ chắc để trả lời câu này.")
                    .build();
        }
        return base(text, "ACTUALLY_SPENDABLE_SUMMARY", "ANSWERED", currency)
                .answerText("Số tiền bạn thực sự có thể chi tiêu trong tháng này là " + formatMoney(spendable.actuallySpendable()) + " " + currency + ".")
                .period(period("Tháng này", monthStart(now), monthEnd(now)))
                .metrics(List.of(new VoiceQueryResponse.MetricDto("actuallySpendable", "Số tiền có thể chi", spendable.actuallySpendable(), null)))
                .links(List.of(new VoiceQueryResponse.LinkDto("Xem kế hoạch chi tiêu", "/planning")))
                .build();
    }

    private List<Tuple> debtRows(UUID workspaceId, String direction) {
        return entityManager.createNativeQuery("""
                SELECT p.display_name AS person_name,
                       COUNT(d.id) AS debt_count,
                       SUM(CASE
                           WHEN d.debt_status IN ('PAID', 'CANCELLED') THEN 0
                           ELSE GREATEST(d.principal_amount - COALESCE(payments.total_paid, 0), 0)
                       END) AS remaining_amount
                FROM debts d
                JOIN workspace_people p ON p.id = d.counterparty_person_id
                LEFT JOIN (
                    SELECT debt_id, SUM(amount) AS total_paid
                    FROM debt_payments
                    GROUP BY debt_id
                ) payments ON payments.debt_id = d.id
                WHERE d.workspace_id = :workspaceId AND d.direction = :direction
                GROUP BY p.display_name
                HAVING SUM(CASE
                           WHEN d.debt_status IN ('PAID', 'CANCELLED') THEN 0
                           ELSE GREATEST(d.principal_amount - COALESCE(payments.total_paid, 0), 0)
                       END) > 0
                ORDER BY remaining_amount DESC, p.display_name ASC
                """, Tuple.class)
                .setParameter("workspaceId", workspaceId)
                .setParameter("direction", direction)
                .getResultList();
    }

    private VoiceQueryResponse.VoiceQueryResponseBuilder base(String text, String intent, String status, String currency) {
        return VoiceQueryResponse.builder()
                .mode("READ_ONLY_QUERY")
                .intent(intent)
                .status(status)
                .question(text)
                .currency(currency)
                .metrics(new ArrayList<>())
                .breakdowns(new ArrayList<>())
                .links(new ArrayList<>())
                .warnings(new ArrayList<>());
    }

    private WorkspaceMember requireActiveMember(UUID workspaceId, UUID userId) {
        workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private String text(VoiceQueryRequest req) {
        String text = req == null ? "" : req.getText();
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException("VOICE_QUERY_TEXT_REQUIRED", "Query text is required", HttpStatus.BAD_REQUEST);
        }
        if (text.length() > 500) {
            throw new BusinessException("VOICE_QUERY_TEXT_TOO_LONG", "Query text is too long", HttpStatus.BAD_REQUEST);
        }
        return text.trim().replaceAll("\\s+", " ");
    }

    private LocalDate monthStart(LocalDate date) {
        return date.withDayOfMonth(1);
    }

    private LocalDate monthEnd(LocalDate date) {
        return date.withDayOfMonth(date.lengthOfMonth());
    }

    private VoiceQueryResponse.PeriodDto period(String label, LocalDate from, LocalDate to) {
        return VoiceQueryResponse.PeriodDto.builder().label(label).from(from).to(to).build();
    }

    private List<VoiceQueryResponse.LinkDto> transactionLink(LocalDate from, LocalDate to) {
        return List.of(new VoiceQueryResponse.LinkDto("Xem giao dịch", "/transactions?from=" + from + "&to=" + to));
    }

    private ZoneId resolveZone(String reqTimezone, Workspace workspace) {
        for (String value : new String[] {reqTimezone, workspace.getTimezone(), "Asia/Ho_Chi_Minh"}) {
            if (value == null || value.isBlank()) continue;
            try {
                return ZoneId.of(value.trim());
            } catch (Exception ignored) {
            }
        }
        return ZoneId.of("Asia/Ho_Chi_Minh");
    }

    private BigDecimal money(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return value instanceof BigDecimal amount ? amount : new BigDecimal(value.toString());
    }

    private String formatMoney(BigDecimal amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        return nf.format(amount.setScale(0, RoundingMode.HALF_UP));
    }
}
