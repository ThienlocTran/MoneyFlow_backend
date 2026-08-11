package com.moneyflowbackend.receipt.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.transaction.repository.TransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ReceiptCategorySuggestor {
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public ReceiptCategorySuggestor(CategoryRepository categoryRepository, TransactionRepository transactionRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    public ReceiptCategorySuggestion suggest(UUID workspaceId, String merchantName, String rawText) {
        List<Category> categories = categoryRepository.findList(workspaceId, CategoryType.EXPENSE, null, true, false, null, false, false);
        Map<UUID, Category> byId = categories.stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        List<ReceiptCategorySuggestion.CategoryCandidate> candidates = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        ReceiptCategorySuggestion history = history(workspaceId, merchantName, byId);
        if (history.categoryId() != null) return history;
        candidates.addAll(history.candidates());
        warnings.addAll(history.warnings());

        String comparable = VietnameseReceiptLexicon.comparable((merchantName == null ? "" : merchantName) + "\n" + (rawText == null ? "" : rawText));
        boolean groceryEvidence = VietnameseReceiptLexicon.hasAny(comparable, VietnameseReceiptLexicon.CATEGORY_HINTS_GROCERY);
        boolean deliveryEvidence = VietnameseReceiptLexicon.hasAny(comparable, VietnameseReceiptLexicon.CATEGORY_HINTS_DELIVERY);
        if (groceryEvidence) {
            ReceiptCategorySuggestion grocery = uniqueRule(categories, VietnameseReceiptLexicon.CATEGORY_NAMES_GROCERY,
                    "MERCHANT_OR_ITEM_RULE", "CATEGORY_MERCHANT_RULE_MATCH_USED", "Grocery merchant/item evidence");
            if (grocery.categoryId() != null) return append(candidates, warnings, grocery);
            candidates.addAll(grocery.candidates());
            warnings.addAll(grocery.warnings());
        }
        if (deliveryEvidence) {
            ReceiptCategorySuggestion delivery = uniqueRule(categories, VietnameseReceiptLexicon.CATEGORY_NAMES_DELIVERY,
                    "DELIVERY_RULE", "CATEGORY_MERCHANT_RULE_MATCH_USED", "Delivery evidence");
            if (delivery.categoryId() != null) return append(candidates, warnings, delivery);
            candidates.addAll(delivery.candidates());
            warnings.addAll(delivery.warnings());
        } else if (categories.stream().anyMatch(category -> matches(category, VietnameseReceiptLexicon.CATEGORY_NAMES_DELIVERY))) {
            warnings.add("CATEGORY_SHIPPER_MATCH_REJECTED");
        }
        if (warnings.isEmpty()) warnings.add("RECEIPT_CATEGORY_LOW_CONFIDENCE");
        return new ReceiptCategorySuggestion(null, null, ReceiptCategorySuggestion.Confidence.LOW, "No unique high-confidence category", candidates, warnings.stream().distinct().toList());
    }

    private ReceiptCategorySuggestion history(UUID workspaceId, String merchantName, Map<UUID, Category> categories) {
        if (merchantName == null || merchantName.isBlank()) {
            return empty("RECEIPT_CATEGORY_LOW_CONFIDENCE");
        }
        List<TransactionRepository.MerchantCategoryHistoryRow> rows = transactionRepository.findReceiptMerchantCategoryHistory(
                workspaceId, merchantName, PageRequest.of(0, 3));
        List<ReceiptCategorySuggestion.CategoryCandidate> candidates = rows.stream()
                .map(row -> categories.get(row.getCategoryId()))
                .filter(category -> category != null && category.isActive() && !category.isArchived())
                .map(category -> candidate(category, "HISTORY", 100, ReceiptCategorySuggestion.Confidence.HIGH, false, null, "Merchant history"))
                .toList();
        if (candidates.size() == 1) {
            ReceiptCategorySuggestion.CategoryCandidate selected = selected(candidates.getFirst());
            return new ReceiptCategorySuggestion(selected.categoryId(), selected.categoryName(), selected.confidence(), selected.evidence(),
                    List.of(selected), List.of("CATEGORY_HISTORY_MATCH_USED"));
        }
        if (candidates.size() > 1) {
            return new ReceiptCategorySuggestion(null, null, ReceiptCategorySuggestion.Confidence.LOW, "Ambiguous merchant history",
                    candidates, List.of("CATEGORY_AMBIGUOUS_MATCH"));
        }
        return empty("RECEIPT_CATEGORY_LOW_CONFIDENCE");
    }

    private ReceiptCategorySuggestion uniqueRule(List<Category> categories, List<String> names, String source, String warning, String evidence) {
        List<ReceiptCategorySuggestion.CategoryCandidate> matches = categories.stream()
                .filter(category -> matches(category, names))
                .map(category -> candidate(category, source, 80, ReceiptCategorySuggestion.Confidence.HIGH, false, null, evidence))
                .toList();
        if (matches.size() == 1) {
            ReceiptCategorySuggestion.CategoryCandidate selected = selected(matches.getFirst());
            return new ReceiptCategorySuggestion(selected.categoryId(), selected.categoryName(), selected.confidence(), selected.evidence(),
                    List.of(selected), List.of(warning));
        }
        if (matches.size() > 1) {
            return new ReceiptCategorySuggestion(null, null, ReceiptCategorySuggestion.Confidence.LOW, "Ambiguous category rule",
                    matches, List.of("CATEGORY_AMBIGUOUS_MATCH"));
        }
        return empty("CATEGORY_NO_ACTIVE_MATCH");
    }

    private ReceiptCategorySuggestion append(List<ReceiptCategorySuggestion.CategoryCandidate> existingCandidates,
                                             List<String> existingWarnings,
                                             ReceiptCategorySuggestion selected) {
        List<ReceiptCategorySuggestion.CategoryCandidate> candidates = new ArrayList<>(existingCandidates);
        candidates.addAll(selected.candidates());
        List<String> warnings = new ArrayList<>(existingWarnings);
        warnings.addAll(selected.warnings());
        return new ReceiptCategorySuggestion(selected.categoryId(), selected.categoryName(), selected.confidence(), selected.evidence(),
                candidates, warnings.stream().distinct().toList());
    }

    private boolean matches(Category category, List<String> names) {
        String name = VietnameseReceiptLexicon.comparable(category.getName());
        return names.stream().anyMatch(value -> VietnameseReceiptLexicon.contains(name, value));
    }

    private ReceiptCategorySuggestion.CategoryCandidate candidate(Category category, String source, int score,
                                                                  ReceiptCategorySuggestion.Confidence confidence,
                                                                  boolean rejected, String rejectedReason, String evidence) {
        return new ReceiptCategorySuggestion.CategoryCandidate(category.getId(), category.getName(), source, score, confidence,
                false, rejected, rejectedReason, evidence);
    }

    private ReceiptCategorySuggestion.CategoryCandidate selected(ReceiptCategorySuggestion.CategoryCandidate candidate) {
        return new ReceiptCategorySuggestion.CategoryCandidate(candidate.categoryId(), candidate.categoryName(), candidate.source(),
                candidate.score(), candidate.confidence(), true, candidate.rejected(), candidate.rejectedReason(), candidate.evidence());
    }

    private ReceiptCategorySuggestion empty(String warning) {
        return new ReceiptCategorySuggestion(null, null, ReceiptCategorySuggestion.Confidence.LOW, null, List.of(), List.of(warning));
    }
}
