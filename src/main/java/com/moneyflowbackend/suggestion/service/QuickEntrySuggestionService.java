package com.moneyflowbackend.suggestion.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.income.model.IncomeSource;
import com.moneyflowbackend.income.model.IncomeSourceStatus;
import com.moneyflowbackend.income.repository.IncomeSourceRepository;
import com.moneyflowbackend.suggestion.dto.QuickEntrySuggestionRequest;
import com.moneyflowbackend.suggestion.dto.QuickEntrySuggestionResponse;
import com.moneyflowbackend.suggestion.dto.SuggestionItemResponse;
import com.moneyflowbackend.suggestion.dto.SuggestionSource;
import com.moneyflowbackend.suggestion.dto.SuggestionTargetType;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.service.WorkspaceService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic suggestion foundation for quick entry / voice review.
 *
 * Rules only: exact name, category keyword, alias (token overlap), history frequency and
 * last used. No model inference, no writes, workspace scoped, active records only.
 */
@Service
public class QuickEntrySuggestionService {
    private static final int MAX_SUGGESTIONS = 5;
    private static final int HISTORY_WINDOW = 200;
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.6d;

    private final WorkspaceService workspaceService;
    private final CategoryRepository categoryRepository;
    private final CategoryKeywordRepository categoryKeywordRepository;
    private final WalletRepository walletRepository;
    private final IncomeSourceRepository incomeSourceRepository;
    private final TransactionRepository transactionRepository;
    private final SuggestionTextMatcher matcher;

    public QuickEntrySuggestionService(
            WorkspaceService workspaceService,
            CategoryRepository categoryRepository,
            CategoryKeywordRepository categoryKeywordRepository,
            WalletRepository walletRepository,
            IncomeSourceRepository incomeSourceRepository,
            TransactionRepository transactionRepository,
            SuggestionTextMatcher matcher) {
        this.workspaceService = workspaceService;
        this.categoryRepository = categoryRepository;
        this.categoryKeywordRepository = categoryKeywordRepository;
        this.walletRepository = walletRepository;
        this.incomeSourceRepository = incomeSourceRepository;
        this.transactionRepository = transactionRepository;
        this.matcher = matcher;
    }

    @Transactional(readOnly = true)
    public QuickEntrySuggestionResponse suggest(UUID workspaceId, QuickEntrySuggestionRequest req, UUID userId) {
        workspaceService.verifyMembership(workspaceId, userId);
        String text = req == null ? null : req.getText();
        if (text == null || text.trim().isBlank()) {
            throw new BusinessException("TEXT_REQUIRED", "Vui lòng nhập nội dung cần gợi ý.", HttpStatus.BAD_REQUEST);
        }

        List<QuickEntrySuggestionResponse.Warning> warnings = new ArrayList<>();
        Intent intent = resolveIntent(req.getIntentType(), warnings);
        warnAboutAmount(req.getAmount(), warnings);

        Set<String> textTokens = matcher.tokenSet(text);
        List<TransactionRepository.SuggestionHistoryRow> history = intent.transactionType() == null
                ? List.of()
                : transactionRepository.findSuggestionHistory(workspaceId, intent.transactionType(), PageRequest.of(0, HISTORY_WINDOW));

        List<SuggestionItemResponse> categories = intent.categoryType() == null
                ? List.of()
                : categorySuggestions(workspaceId, intent.categoryType(), text, textTokens, history);
        List<SuggestionItemResponse> incomeSources = intent.suggestIncomeSources()
                ? incomeSourceSuggestions(workspaceId, text, textTokens, history)
                : List.of();
        List<SuggestionItemResponse> wallets = intent.suggestWallets()
                ? walletSuggestions(workspaceId, text, textTokens, history)
                : List.of();

        if (intent.categoryType() == CategoryType.EXPENSE && categories.isEmpty()) {
            warnings.add(warning("NO_CATEGORY_MATCH", "Chưa tìm được danh mục phù hợp, bạn hãy chọn thủ công.", "INFO"));
        }
        if (intent.suggestIncomeSources() && incomeSources.isEmpty()) {
            warnings.add(warning("NO_INCOME_SOURCE_MATCH", "Chưa tìm được nguồn thu phù hợp, bạn hãy chọn thủ công.", "INFO"));
        }
        if (intent.suggestWallets() && wallets.isEmpty()) {
            warnings.add(warning("NO_WALLET_MATCH", "Chưa tìm được ví phù hợp, bạn hãy chọn thủ công.", "INFO"));
        }
        warnAboutLowConfidence(List.of(categories, incomeSources, wallets), warnings);

        return QuickEntrySuggestionResponse.builder()
                .categorySuggestions(categories)
                .walletSuggestions(wallets)
                .incomeSourceSuggestions(incomeSources)
                .warnings(warnings)
                .build();
    }

    private List<SuggestionItemResponse> categorySuggestions(
            UUID workspaceId,
            CategoryType categoryType,
            String text,
            Set<String> textTokens,
            List<TransactionRepository.SuggestionHistoryRow> history) {
        List<Category> candidates = categoryRepository.findAllByWorkspaceIdAndIsActiveTrueOrderByDisplayOrderAsc(workspaceId).stream()
                .filter(category -> !category.isArchived())
                .filter(category -> category.getCategoryType() == categoryType)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<CategoryKeyword>> keywordsByCategory = new HashMap<>();
        categoryKeywordRepository.findAllByWorkspaceIdOrderByPriorityDescKeywordAsc(workspaceId)
                .forEach(keyword -> keywordsByCategory
                        .computeIfAbsent(keyword.getCategory().getId(), key -> new ArrayList<>())
                        .add(keyword));

        Map<UUID, Long> historyHits = historyHits(history, textTokens, TransactionRepository.SuggestionHistoryRow::getCategoryId);
        UUID lastUsedId = lastUsedId(history, TransactionRepository.SuggestionHistoryRow::getCategoryId);

        Map<UUID, SuggestionItemResponse> best = new LinkedHashMap<>();
        for (Category category : candidates) {
            Scored scored = better(
                    scoreByName(text, textTokens, category.getName()),
                    scoreByKeyword(text, keywordsByCategory.getOrDefault(category.getId(), List.of())));
            scored = better(scored, scoreByHistory(historyHits.get(category.getId()), "danh mục"));
            if (scored == null && category.getId().equals(lastUsedId)) {
                scored = new Scored(0.3d, SuggestionSource.LAST_USED, "Danh mục bạn dùng gần đây nhất");
            }
            if (scored != null) {
                best.put(category.getId(), item(category.getId(), category.getName(), SuggestionTargetType.CATEGORY, scored));
            }
        }
        return sortAndLimit(best.values());
    }

    private List<SuggestionItemResponse> incomeSourceSuggestions(
            UUID workspaceId,
            String text,
            Set<String> textTokens,
            List<TransactionRepository.SuggestionHistoryRow> history) {
        List<IncomeSource> candidates = incomeSourceRepository
                .findAllByWorkspaceIdAndStatusOrderByNameAsc(workspaceId, IncomeSourceStatus.ACTIVE);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> historyHits = historyHits(history, textTokens, TransactionRepository.SuggestionHistoryRow::getIncomeSourceId);
        UUID lastUsedId = lastUsedId(history, TransactionRepository.SuggestionHistoryRow::getIncomeSourceId);

        Map<UUID, SuggestionItemResponse> best = new LinkedHashMap<>();
        for (IncomeSource source : candidates) {
            Scored scored = scoreByName(text, textTokens, source.getName());
            scored = better(scored, scoreByHistory(historyHits.get(source.getId()), "nguồn thu"));
            if (scored == null && source.getId().equals(lastUsedId)) {
                scored = new Scored(0.3d, SuggestionSource.LAST_USED, "Nguồn thu bạn ghi gần đây nhất");
            }
            if (scored != null) {
                best.put(source.getId(), item(source.getId(), source.getName(), SuggestionTargetType.INCOME_SOURCE, scored));
            }
        }
        return sortAndLimit(best.values());
    }

    private List<SuggestionItemResponse> walletSuggestions(
            UUID workspaceId,
            String text,
            Set<String> textTokens,
            List<TransactionRepository.SuggestionHistoryRow> history) {
        List<Wallet> candidates = walletRepository.findAllByWorkspaceIdAndIsActiveTrue(workspaceId).stream()
                .sorted(Comparator.comparing(Wallet::getName, Comparator.nullsLast(String::compareTo)))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> historyHits = historyHits(history, textTokens, TransactionRepository.SuggestionHistoryRow::getWalletId);
        Map<UUID, Long> walletUsage = usageCounts(history, TransactionRepository.SuggestionHistoryRow::getWalletId);
        UUID lastUsedId = lastUsedId(history, TransactionRepository.SuggestionHistoryRow::getWalletId);

        Map<UUID, SuggestionItemResponse> best = new LinkedHashMap<>();
        for (Wallet wallet : candidates) {
            Scored scored = scoreByName(text, textTokens, wallet.getName());
            if (scored == null && !matcher.initials(wallet.getName()).isBlank()
                    && textTokens.contains(matcher.initials(wallet.getName()))) {
                scored = new Scored(0.6d, SuggestionSource.ALIAS, "Khớp tên viết tắt của ví");
            }
            scored = better(scored, scoreByHistory(historyHits.get(wallet.getId()), "ví"));
            if (scored == null) {
                Long usage = walletUsage.get(wallet.getId());
                if (wallet.getId().equals(lastUsedId)) {
                    scored = new Scored(0.4d, SuggestionSource.LAST_USED, "Ví bạn dùng gần đây nhất");
                } else if (usage != null && usage > 0) {
                    scored = new Scored(Math.min(0.5d, 0.3d + 0.03d * Math.min(usage, 6)), SuggestionSource.HISTORY,
                            "Ví bạn thường dùng (" + usage + " giao dịch gần đây)");
                } else if (wallet.isDefault()) {
                    scored = new Scored(0.35d, SuggestionSource.LAST_USED, "Ví mặc định của sổ này");
                }
            }
            if (scored != null) {
                best.put(wallet.getId(), item(wallet.getId(), wallet.getName(), SuggestionTargetType.WALLET, scored));
            }
        }
        return sortAndLimit(best.values());
    }

    private Scored scoreByName(String text, Set<String> textTokens, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (matcher.containsPhrase(text, name)) {
            return new Scored(0.95d, SuggestionSource.EXACT_NAME, "Bạn đã nói đúng tên \"" + name + "\"");
        }
        double overlap = matcher.tokenOverlapRatio(textTokens, matcher.tokenSet(name));
        if (overlap > 0) {
            double confidence = Math.min(0.7d, 0.5d + 0.2d * overlap);
            return new Scored(confidence, SuggestionSource.ALIAS, "Khớp một phần tên \"" + name + "\"");
        }
        return null;
    }

    private Scored scoreByKeyword(String text, List<CategoryKeyword> keywords) {
        Scored best = null;
        for (CategoryKeyword keyword : keywords) {
            if (!matcher.containsPhrase(text, keyword.getKeyword())) {
                continue;
            }
            int priority = keyword.getPriority() == null ? 0 : Math.max(0, Math.min(keyword.getPriority(), 5));
            double confidence = Math.min(0.93d, 0.88d + 0.01d * priority);
            Scored candidate = new Scored(confidence, SuggestionSource.KEYWORD,
                    "Khớp từ khóa \"" + keyword.getKeyword() + "\"");
            best = better(best, candidate);
        }
        return best;
    }

    private Scored scoreByHistory(Long hits, String label) {
        if (hits == null || hits <= 0) {
            return null;
        }
        double confidence = Math.min(0.8d, 0.5d + 0.05d * Math.min(hits, 6));
        return new Scored(confidence, SuggestionSource.HISTORY,
                "Hay dùng " + label + " này cho nội dung tương tự (" + hits + " lần)");
    }

    private Map<UUID, Long> historyHits(
            List<TransactionRepository.SuggestionHistoryRow> history,
            Set<String> textTokens,
            java.util.function.Function<TransactionRepository.SuggestionHistoryRow, UUID> idExtractor) {
        Map<UUID, Long> hits = new HashMap<>();
        if (textTokens.isEmpty()) {
            return hits;
        }
        for (TransactionRepository.SuggestionHistoryRow row : history) {
            UUID id = idExtractor.apply(row);
            if (id == null) {
                continue;
            }
            Set<String> rowTokens = matcher.tokenSet(rowText(row));
            if (matcher.tokenOverlapRatio(textTokens, rowTokens) > 0) {
                hits.merge(id, 1L, Long::sum);
            }
        }
        return hits;
    }

    private Map<UUID, Long> usageCounts(
            List<TransactionRepository.SuggestionHistoryRow> history,
            java.util.function.Function<TransactionRepository.SuggestionHistoryRow, UUID> idExtractor) {
        Map<UUID, Long> counts = new HashMap<>();
        for (TransactionRepository.SuggestionHistoryRow row : history) {
            UUID id = idExtractor.apply(row);
            if (id != null) {
                counts.merge(id, 1L, Long::sum);
            }
        }
        return counts;
    }

    private UUID lastUsedId(
            List<TransactionRepository.SuggestionHistoryRow> history,
            java.util.function.Function<TransactionRepository.SuggestionHistoryRow, UUID> idExtractor) {
        for (TransactionRepository.SuggestionHistoryRow row : history) {
            UUID id = idExtractor.apply(row);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private String rowText(TransactionRepository.SuggestionHistoryRow row) {
        String description = row.getDescription() == null ? "" : row.getDescription();
        String rawInput = row.getRawInput() == null ? "" : row.getRawInput();
        return (description + " " + rawInput).trim();
    }

    private SuggestionItemResponse item(UUID id, String name, SuggestionTargetType type, Scored scored) {
        double confidence = Math.round(scored.confidence() * 100d) / 100d;
        return SuggestionItemResponse.builder()
                .id(id)
                .name(name)
                .type(type)
                .confidence(confidence)
                .reason(scored.reason())
                .source(scored.source())
                .lowConfidence(confidence < LOW_CONFIDENCE_THRESHOLD)
                .build();
    }

    private List<SuggestionItemResponse> sortAndLimit(java.util.Collection<SuggestionItemResponse> items) {
        return items.stream()
                .sorted(Comparator.comparingDouble(SuggestionItemResponse::getConfidence).reversed()
                        .thenComparing(SuggestionItemResponse::getName, Comparator.nullsLast(String::compareTo))
                        // Final tiebreaker so ties (same confidence and name) still order
                        // deterministically across calls; the frontend chips depend on stable output.
                        .thenComparing(item -> item.getId() == null ? "" : item.getId().toString()))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private Scored better(Scored current, Scored candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.confidence() > current.confidence()) {
            return candidate;
        }
        return current;
    }

    private Intent resolveIntent(String intentType, List<QuickEntrySuggestionResponse.Warning> warnings) {
        String value = intentType == null ? "" : intentType.trim().toUpperCase(java.util.Locale.ROOT);
        if (value.isBlank()) {
            warnings.add(warning("INTENT_ASSUMED_EXPENSE", "Chưa có loại giao dịch nên tạm gợi ý theo chi tiêu.", "INFO"));
            return new Intent(CategoryType.EXPENSE, false, true, TransactionType.EXPENSE);
        }
        return switch (value) {
            case "TRANSACTION_EXPENSE", "EXPENSE" -> new Intent(CategoryType.EXPENSE, false, true, TransactionType.EXPENSE);
            case "TRANSACTION_INCOME", "INCOME" -> new Intent(null, true, true, TransactionType.INCOME);
            case "TRANSACTION_TRANSFER", "TRANSFER" -> new Intent(null, false, true, TransactionType.TRANSFER);
            default -> {
                warnings.add(warning("UNSUPPORTED_INTENT_FOR_SUGGESTIONS",
                        "Loại giao dịch này chưa có gợi ý danh mục hoặc nguồn thu, chỉ gợi ý ví.", "INFO"));
                yield new Intent(null, false, true, TransactionType.EXPENSE);
            }
        };
    }

    private void warnAboutAmount(BigDecimal amount, List<QuickEntrySuggestionResponse.Warning> warnings) {
        if (amount == null || amount.signum() <= 0) {
            warnings.add(warning("AMOUNT_MISSING", "Chưa có số tiền hợp lệ nên gợi ý chỉ dựa trên nội dung chữ.", "INFO"));
        }
    }

    private void warnAboutLowConfidence(
            List<List<SuggestionItemResponse>> groups,
            List<QuickEntrySuggestionResponse.Warning> warnings) {
        List<SuggestionItemResponse> all = groups.stream().flatMap(List::stream).toList();
        if (all.isEmpty()) {
            return;
        }
        if (all.stream().allMatch(SuggestionItemResponse::isLowConfidence)) {
            warnings.add(warning("LOW_CONFIDENCE_ONLY",
                    "Các gợi ý đang có độ tin cậy thấp, bạn nên kiểm tra trước khi chọn.", "WARN"));
        }
    }

    private QuickEntrySuggestionResponse.Warning warning(String code, String message, String severity) {
        return QuickEntrySuggestionResponse.Warning.builder()
                .code(code)
                .message(message)
                .severity(severity)
                .build();
    }

    private record Scored(double confidence, SuggestionSource source, String reason) {
    }

    private record Intent(
            CategoryType categoryType,
            boolean suggestIncomeSources,
            boolean suggestWallets,
            TransactionType transactionType) {
    }
}
