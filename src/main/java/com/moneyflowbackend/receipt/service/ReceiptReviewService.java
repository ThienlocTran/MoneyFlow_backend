package com.moneyflowbackend.receipt.service;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.category.repository.CategoryKeywordRepository;
import com.moneyflowbackend.category.repository.CategoryRepository;
import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.quickentry.parser.VietnameseTextNormalizer;
import com.moneyflowbackend.receipt.dto.ReceiptReviewParseRequest;
import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;
import com.moneyflowbackend.receipt.dto.ReceiptReviewSource;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.repository.WalletRepository;
import com.moneyflowbackend.workspace.model.Workspace;
import com.moneyflowbackend.workspace.model.WorkspaceMember;
import com.moneyflowbackend.workspace.repository.WorkspaceMemberRepository;
import com.moneyflowbackend.workspace.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ReceiptReviewService {
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";

    private final ReceiptTextParser parser;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryKeywordRepository categoryKeywordRepository;
    private final Clock clock;

    public ReceiptReviewService(
            ReceiptTextParser parser,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            WalletRepository walletRepository,
            CategoryRepository categoryRepository,
            CategoryKeywordRepository categoryKeywordRepository,
            Clock clock) {
        this.parser = parser;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.categoryKeywordRepository = categoryKeywordRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReceiptReviewParseResponse parse(UUID workspaceId, ReceiptReviewParseRequest req, UUID userId) {
        Workspace workspace = requireMember(workspaceId, userId).getWorkspace();
        ReceiptReviewSource source = req == null || req.getSource() == null ? ReceiptReviewSource.MANUAL_TEXT : req.getSource();
        String rawText = normalize(req == null ? null : req.getRawText());
        List<ReceiptReviewParseResponse.Warning> warnings = new ArrayList<>();
        if (rawText == null || rawText.length() < 4) {
            warnings.add(warning("RECEIPT_TEXT_TOO_SHORT", "rawText"));
            warnings.add(warning("RECEIPT_UNSUPPORTED_TEXT", "rawText"));
            return response(source, rawText, null, null, null, warnings, "UNSUPPORTED");
        }

        ReceiptTextParser.ParsedReceipt parsed = parser.parse(rawText);
        if (parsed.totalAmount() == null) {
            warnings.add(warning("RECEIPT_TOTAL_NOT_FOUND", "amount"));
        } else if (parsed.totalInferred()) {
            warnings.add(warning("RECEIPT_TOTAL_INFERRED", "amount"));
        }
        if (parsed.merchantName() == null) {
            warnings.add(warning("RECEIPT_MERCHANT_UNCERTAIN", "merchantName"));
        }
        LocalDate date = parsed.receiptDate();
        if (date == null && req != null && req.getOccurredAtHint() != null) {
            date = req.getOccurredAtHint().toLocalDate();
        }
        if (date == null) {
            date = LocalDate.now(clock.withZone(zone(workspace)));
            warnings.add(warning("RECEIPT_DATE_INFERRED", "occurredAt"));
        }

        Wallet wallet = wallet(req == null ? null : req.getWalletId(), workspaceId);
        Category category = category(workspaceId, rawText);
        List<String> needs = new ArrayList<>();
        if (parsed.totalAmount() == null) needs.add("amount");
        if (wallet == null) {
            needs.add("walletId");
            warnings.add(warning("RECEIPT_WALLET_NOT_SELECTED", "walletId"));
        }
        if (category == null) {
            needs.add("categoryId");
            warnings.add(warning("RECEIPT_CATEGORY_NOT_SELECTED", "categoryId"));
        }

        OffsetDateTime occurredAt = OffsetDateTime.of(date, LocalTime.MIDNIGHT, zone(workspace).getRules().getOffset(clock.instant()));
        ReceiptReviewParseResponse.Candidate candidate = ReceiptReviewParseResponse.Candidate.builder()
                .type(TransactionType.EXPENSE)
                .amount(parsed.totalAmount())
                .currency(currency(workspace))
                .occurredAt(occurredAt)
                .walletId(wallet == null ? null : wallet.getId())
                .categoryId(category == null ? null : category.getId())
                .categoryName(category == null ? null : category.getName())
                .merchantName(parsed.merchantName())
                .note(parsed.merchantName() == null ? "Receipt" : parsed.merchantName())
                .needsFields(needs.stream().distinct().toList())
                .build();
        ReceiptReviewParseResponse.Extracted extracted = ReceiptReviewParseResponse.Extracted.builder()
                .merchantName(parsed.merchantName())
                .receiptDate(parsed.receiptDate())
                .totalAmount(parsed.totalAmount())
                .lineAmounts(parsed.lineAmounts())
                .build();
        return response(source, rawText, candidate, extracted, parsed.totalAmount(), warnings, "NEEDS_REVIEW");
    }

    private ReceiptReviewParseResponse response(
            ReceiptReviewSource source,
            String rawText,
            ReceiptReviewParseResponse.Candidate candidate,
            ReceiptReviewParseResponse.Extracted extracted,
            BigDecimal amount,
            List<ReceiptReviewParseResponse.Warning> warnings,
            String status) {
        return ReceiptReviewParseResponse.builder()
                .mode("RECEIPT_REVIEW")
                .status(amount == null && "NEEDS_REVIEW".equals(status) ? "UNSUPPORTED" : status)
                .source(source)
                .rawText(rawText)
                .candidate(candidate)
                .extracted(extracted)
                .warnings(warnings.stream().distinct().toList())
                .build();
    }

    private Category category(UUID workspaceId, String rawText) {
        String comparable = VietnameseTextNormalizer.comparable(rawText);
        List<CategoryKeyword> keywords = categoryKeywordRepository.findAllByWorkspaceIdOrderByPriorityDescKeywordAsc(workspaceId);
        Category keywordMatch = keywords.stream()
                .filter(keyword -> keyword.getCategory() != null && keyword.getCategory().getCategoryType() == CategoryType.EXPENSE)
                .filter(keyword -> contains(comparable, keyword.getKeyword()))
                .map(CategoryKeyword::getCategory)
                .filter(this::usable)
                .findFirst()
                .orElse(null);
        if (keywordMatch != null) return keywordMatch;
        List<String> preferredNames = preferredCategoryNames(comparable);
        if (preferredNames.isEmpty()) return null;
        return categoryRepository.findList(workspaceId, CategoryType.EXPENSE, null, true, false, null, false, false).stream()
                .filter(category -> preferredNames.stream().anyMatch(name -> contains(VietnameseTextNormalizer.comparable(category.getName()), name)))
                .min(Comparator.comparingInt(Category::getDisplayOrder))
                .orElse(null);
    }

    private List<String> preferredCategoryNames(String text) {
        if (hasAny(text, "xang", "petrol", "fuel", "grab", "taxi")) return List.of("xang", "di lai", "di chuyen", "transport");
        if (hasAny(text, "cafe", "coffee", "tra sua", "ca phe")) return List.of("an uong", "do uong", "cafe", "coffee");
        if (hasAny(text, "sieu thi", "coopmart", "bach hoa", "cua hang")) return List.of("groceries", "an uong", "cho");
        if (hasAny(text, "thuoc", "pharmacy")) return List.of("y te", "health");
        return List.of();
    }

    private boolean hasAny(String text, String... values) {
        for (String value : values) {
            if (contains(text, value)) return true;
        }
        return false;
    }

    private boolean contains(String text, String value) {
        String needle = VietnameseTextNormalizer.comparable(value);
        return !needle.isBlank() && (" " + text + " ").contains(" " + needle + " ");
    }

    private boolean usable(Category category) {
        return category.isActive() && !category.isArchived();
    }

    private Wallet wallet(UUID walletId, UUID workspaceId) {
        if (walletId == null) return null;
        Wallet wallet = walletRepository.findByIdAndWorkspaceId(walletId, workspaceId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Wallet not found", HttpStatus.NOT_FOUND));
        if (!wallet.isActive()) {
            throw new BusinessException("WALLET_INACTIVE", "Wallet is inactive");
        }
        return wallet;
    }

    private ReceiptReviewParseResponse.Warning warning(String code, String field) {
        return ReceiptReviewParseResponse.Warning.builder()
                .code(code)
                .field(field)
                .message(message(code))
                .build();
    }

    private String message(String code) {
        return switch (code) {
            case "RECEIPT_TOTAL_INFERRED" -> "Receipt total was inferred from the largest plausible amount.";
            case "RECEIPT_TOTAL_NOT_FOUND" -> "Receipt total was not found.";
            case "RECEIPT_MERCHANT_UNCERTAIN" -> "Merchant could not be detected confidently.";
            case "RECEIPT_DATE_INFERRED" -> "Receipt date was inferred.";
            case "RECEIPT_CATEGORY_NOT_SELECTED" -> "Choose a category before saving.";
            case "RECEIPT_WALLET_NOT_SELECTED" -> "Choose a wallet before saving.";
            case "RECEIPT_TEXT_TOO_SHORT" -> "Receipt text is too short.";
            default -> "Receipt text is not supported yet.";
        };
    }

    private WorkspaceMember requireMember(UUID workspaceId, UUID userId) {
        findWorkspace(workspaceId);
        return workspaceMemberRepository.findByWorkspaceIdAndUserIdAndMemberStatus(workspaceId, userId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("WORKSPACE_ACCESS_DENIED", "Workspace access denied", HttpStatus.FORBIDDEN));
    }

    private Workspace findWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .filter(workspace -> workspace.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("WORKSPACE_NOT_FOUND", "Workspace not found", HttpStatus.NOT_FOUND));
    }

    private ZoneId zone(Workspace workspace) {
        try {
            return ZoneId.of(workspace.getTimezone() == null ? FALLBACK_ZONE : workspace.getTimezone());
        } catch (DateTimeException ex) {
            return ZoneId.of(FALLBACK_ZONE);
        }
    }

    private String currency(Workspace workspace) {
        return workspace.getCurrency() == null ? "VND" : workspace.getCurrency().strip().toUpperCase();
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isBlank() ? null : trimmed;
    }
}
