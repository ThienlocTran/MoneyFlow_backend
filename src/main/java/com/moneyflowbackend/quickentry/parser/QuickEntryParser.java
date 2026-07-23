package com.moneyflowbackend.quickentry.parser;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.dto.VoiceCandidateStatus;
import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
import com.moneyflowbackend.transaction.model.TransactionStatus;
import com.moneyflowbackend.transaction.model.TransactionType;
import com.moneyflowbackend.wallet.model.Wallet;
import com.moneyflowbackend.wallet.model.WalletType;
import com.moneyflowbackend.workspace.model.Workspace;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QuickEntryParser {
    private static final Set<String> INCOME_WORDS = Set.of(
            "thu", "nhan", "luong", "thuong", "me cho", "ba cho", "duoc cho", "duoc tang",
            "hoan tien", "gia dinh gui");
    private static final Set<String> EXPENSE_WORDS = Set.of(
            "chi", "mua", "tra", "dong", "dong tien", "thanh toan", "an", "uong", "cafe",
            "ca phe", "xang", "gui xe");
    private static final List<AliasRule> CATEGORY_ALIASES = List.of(
            new AliasRule(CategoryType.EXPENSE, List.of("do an ngoai", "mua do an ngoai", "an ngoai"),
                    List.of("Mua do an ngoai")),
            new AliasRule(CategoryType.EXPENSE, List.of("cafe", "ca phe"),
                    List.of("Mua nuoc uong")),
            new AliasRule(CategoryType.EXPENSE, List.of("xang", "xang xe"),
                    List.of("Xang xe")),
            new AliasRule(CategoryType.EXPENSE, List.of("gui xe"),
                    List.of("Gui xe")),
            new AliasRule(CategoryType.EXPENSE, List.of("an", "an sang", "an trua", "an toi", "com", "bun", "pho", "do an", "tien an"),
                    List.of("An uong", "An", "Food", "Food & Drink")),
            new AliasRule(CategoryType.EXPENSE, List.of("ca phe", "cafe", "coffee", "uong ca phe", "tra sua", "nuoc", "uong nuoc"),
                    List.of("Ca phe", "An uong", "Do uong", "Food & Drink")),
            new AliasRule(CategoryType.EXPENSE, List.of("xang", "xang xe", "gui xe", "grab", "taxi"),
                    List.of("Di lai", "Di chuyen", "Transport")),
            new AliasRule(CategoryType.INCOME, List.of("luong", "thuong"),
                    List.of("Luong", "Thu nhap")),
            new AliasRule(CategoryType.INCOME, List.of("me cho", "duoc cho", "nhan tien"),
                    List.of("Thu nhap khac", "Qua tang", "Thu nhap"))
    );
    private static final Pattern TRANSFER_PATTERN = Pattern.compile("\\b(chuyen|chuyen tien|chuyen khoan|transfer|tu .+ (sang|qua) .+)\\b");
    private static final Pattern FROM_TO = Pattern.compile("\\btu\\s+(.+?)\\s+(?:sang|qua)\\s+(.+)$");
    private static final Pattern TO_ONLY = Pattern.compile("^(.+?)\\s+(?:sang|qua)\\s+(.+)$");
    private static final Pattern WALLET_HINT = Pattern.compile("\\b(vi|wallet|tk|tai khoan|bank|ngan hang)\\b");

    private final QuickAmountParser amountParser;
    private final QuickDateParser dateParser;

    public QuickEntryParser(QuickAmountParser amountParser, QuickDateParser dateParser) {
        this.amountParser = amountParser;
        this.dateParser = dateParser;
    }

    public QuickEntryPreviewResponse parse(
            String rawInput,
            Workspace workspace,
            List<CategoryKeyword> keywords,
            List<Category> categories,
            List<Wallet> wallets) {
        return parse(rawInput, workspace, keywords, categories, wallets, null);
    }

    public QuickEntryPreviewResponse parse(
            String rawInput,
            Workspace workspace,
            List<CategoryKeyword> keywords,
            List<Category> categories,
            List<Wallet> wallets,
            UUID suggestedWalletId) {
        String display = VietnameseTextNormalizer.compact(rawInput);
        String normalized = VietnameseTextNormalizer.comparable(rawInput);
        String quickAmountUnit = workspace == null ? null : workspace.getQuickAmountUnit();
        String timezone = workspace == null ? null : workspace.getTimezone();

        LinkedHashSet<String> missing = new LinkedHashSet<>();
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        List<Span> removableSpans = new ArrayList<>();

        QuickAmountParser.AmountParseResult amountResult = amountParser.parse(rawInput, quickAmountUnit);
        List<QuickAmountParser.AmountCandidate> amountCandidates = amountResult.candidates();
        BigDecimal amount = null;
        QuickAmountParser.AmountCandidate amountCandidate = null;
        if (amountResult.negativeAmount() || amountResult.zeroAmount()) {
            missing.add("AMOUNT");
            warnings.add("INVALID_AMOUNT");
        } else if (amountCandidates.size() > 1) {
            amountCandidate = amountCandidates.get(0);
            amount = amountCandidate.amount();
            removableSpans.add(new Span(amountCandidate.start(), amountCandidate.end()));
            warnings.add("MULTIPLE_ITEMS_DETECTED");
        } else if (amountResult.single().isPresent()) {
            amountCandidate = amountResult.single().orElseThrow();
            amount = amountCandidate.amount();
            removableSpans.add(new Span(amountCandidate.start(), amountCandidate.end()));
            if (amountCandidate.assumedThousand()) {
                warnings.add("ASSUMED_THOUSAND_UNIT");
            }
        } else {
            missing.add("AMOUNT");
        }

        QuickDateParser.DateParseResult dateResult = dateParser.parse(rawInput, timezone);
        LocalDate transactionDate = dateResult.date();
        TransactionStatus status = dateResult.status();
        dateResult.spans().forEach(span -> removableSpans.add(new Span(span.start(), span.end())));
        if (dateResult.invalidDate()) {
            missing.add("DATE");
            warnings.add("INVALID_DATE");
        }
        if (dateResult.invalidTime()) {
            warnings.add("INVALID_TIME");
        }
        if (dateResult.future()) {
            warnings.add("FUTURE_DATE_ASSUMED_PLANNED");
        }

        VoiceIntentType explicitIntent = detectNonTransactionIntent(normalized);
        if (explicitIntent != null) {
            return unsupportedDraft(rawInput, display, explicitIntent, amount, transactionDate, dateResult.time(),
                    confidence(false, missing, warnings), missing, warnings);
        }

        boolean transferText = TRANSFER_PATTERN.matcher(normalized).find() || looksLikeBareWalletTransfer(normalized, display, wallets);
        CategoryMatch categoryMatch = matchCategory(normalized, display, keywords, categories).orElse(null);
        boolean ambiguousCategory = categoryMatch != null && categoryMatch.ambiguous();
        TransactionType type = inferType(normalized, transferText, categoryMatch);
        if (type == null) {
            missing.add("TYPE");
            warnings.add("UNSUPPORTED_INTENT");
        }

        Category category = null;
        String matchedKeyword = null;
        if (type == TransactionType.TRANSFER) {
            categoryMatch = null;
        } else if (ambiguousCategory) {
            missing.add("CATEGORY");
            warnings.add("AMBIGUOUS_CATEGORY");
        } else if (categoryMatch != null && categoryMatch.category() != null) {
            if (!categoryMatchesType(categoryMatch.category(), type)) {
                missing.add("CATEGORY");
                warnings.add("CATEGORY_TYPE_MISMATCH");
            } else {
            category = categoryMatch.category();
            matchedKeyword = categoryMatch.keyword() == null ? categoryMatch.text() : categoryMatch.keyword().getKeyword();
            }
        } else if (type == TransactionType.INCOME || type == TransactionType.EXPENSE) {
            missing.add("CATEGORY");
            warnings.add("UNKNOWN_CATEGORY");
        }

        Wallet wallet = null;
        Wallet sourceWallet = null;
        Wallet destinationWallet = null;
        String matchedWalletText = null;

        if (type == TransactionType.TRANSFER) {
            TransferWallets transferWallets = matchTransferWallets(normalized, display, removableSpans, wallets);
            sourceWallet = transferWallets.source();
            destinationWallet = transferWallets.destination();
            if (transferWallets.sourceMatch() != null) {
                removableSpans.add(new Span(transferWallets.sourceMatch().start(), transferWallets.sourceMatch().end()));
            }
            if (transferWallets.destinationMatch() != null) {
                removableSpans.add(new Span(transferWallets.destinationMatch().start(), transferWallets.destinationMatch().end()));
            }
            if (sourceWallet == null) {
                missing.add("SOURCE_WALLET");
            }
            if (destinationWallet == null) {
                missing.add("DESTINATION_WALLET");
            }
            if (sourceWallet != null && destinationWallet != null && sourceWallet.getId().equals(destinationWallet.getId())) {
                warnings.add("TRANSFER_SAME_WALLET");
            }
        } else {
            WalletMatch walletMatch = matchWallet(normalized, display, wallets).orElse(null);
            if (walletMatch != null && walletMatch.ambiguous()) {
                missing.add("WALLET");
                warnings.add("AMBIGUOUS_WALLET");
            } else if (walletMatch != null) {
                wallet = walletMatch.wallet();
                matchedWalletText = walletMatch.text();
                removableSpans.add(new Span(walletMatch.start(), walletMatch.end()));
            } else if (type == TransactionType.INCOME || type == TransactionType.EXPENSE || type == null) {
                if (WALLET_HINT.matcher(normalized).find()) {
                    missing.add("WALLET");
                    warnings.add("UNKNOWN_WALLET");
                } else {
                    wallet = preferredWallet(wallets, suggestedWalletId).orElse(null);
                    if (wallet == null) {
                        missing.add("WALLET");
                    } else if (suggestedWalletId != null && wallet.getId().equals(suggestedWalletId)) {
                        warnings.add("SUGGESTED_WALLET_USED");
                    } else {
                        warnings.add("DEFAULT_WALLET_USED");
                    }
                }
            }
        }

        List<QuickEntryPreviewResponse.Candidate> candidates = buildCandidates(display, amountCandidates, keywords, categories,
                type, status, transactionDate, dateResult.time(), wallet, sourceWallet, destinationWallet);
        String description = amountCandidates.size() > 1
                ? candidates.stream().findFirst().map(QuickEntryPreviewResponse.Candidate::getDescription).orElse(null)
                : description(display, removableSpans, type, destinationWallet, amountCandidate, matchedWalletText);
        boolean ready = ready(type, amount, category, wallet, sourceWallet, destinationWallet, transactionDate, warnings, missing);
        double confidence = confidence(ready, missing, warnings);

        return QuickEntryPreviewResponse.builder()
                .candidateId(candidateId(display, 0, display, amount))
                .intentType(intentType(type))
                .candidateStatus(type == null ? VoiceCandidateStatus.UNSUPPORTED : ready ? VoiceCandidateStatus.READY : VoiceCandidateStatus.NEEDS_REVIEW)
                .rawInput(rawInput)
                .normalizedInput(display)
                .type(type)
                .status(status)
                .amount(amount)
                .walletId(wallet == null ? null : wallet.getId())
                .walletName(wallet == null ? null : wallet.getName())
                .categoryId(category == null ? null : category.getId())
                .categoryName(category == null ? null : category.getName())
                .sourceWalletId(sourceWallet == null ? null : sourceWallet.getId())
                .sourceWalletName(sourceWallet == null ? null : sourceWallet.getName())
                .destinationWalletId(destinationWallet == null ? null : destinationWallet.getId())
                .destinationWalletName(destinationWallet == null ? null : destinationWallet.getName())
                .transactionDate(transactionDate)
                .transactionTime(dateResult.time())
                .description(description)
                .note(null)
                .confidence(confidence)
                .readyToConfirm(ready)
                .unsupportedReason(type == null ? "UNKNOWN_UNSUPPORTED" : null)
                .missingFields(new ArrayList<>(missing))
                .warnings(new ArrayList<>(warnings))
                .matchedKeyword(matchedKeyword)
                .matchedCategoryId(category == null ? null : category.getId())
                .matchedWalletText(matchedWalletText)
                .candidates(candidates)
                .build();
    }

    private QuickEntryPreviewResponse unsupportedDraft(
            String rawInput,
            String display,
            VoiceIntentType intentType,
            BigDecimal amount,
            LocalDate date,
            java.time.LocalTime time,
            double confidence,
            LinkedHashSet<String> missing,
            LinkedHashSet<String> warnings) {
        if (amount == null && amountIntent(intentType)) {
            missing.add("AMOUNT");
        }
        if (date == null) {
            missing.add("DATE");
        }
        missing.addAll(requiredReferenceFields(intentType));
        warnings.add("UNSUPPORTED_INTENT");
        warnings.add("VOICE_INTENT_NOT_COMMITTABLE");
        String reason = "VOICE_COMMIT_NOT_SUPPORTED";
        String candidateId = candidateId(display, 0, display, amount);
        return QuickEntryPreviewResponse.builder()
                .candidateId(candidateId)
                .intentType(intentType)
                .candidateStatus(VoiceCandidateStatus.UNSUPPORTED)
                .rawInput(rawInput)
                .normalizedInput(display)
                .amount(amount)
                .transactionDate(date)
                .transactionTime(time)
                .description(VietnameseTextNormalizer.capitalize(display))
                .confidence(confidence)
                .readyToConfirm(false)
                .missingFields(new ArrayList<>(missing))
                .warnings(new ArrayList<>(warnings))
                .unsupportedReason(reason)
                .candidates(List.of(QuickEntryPreviewResponse.Candidate.builder()
                        .candidateId(candidateId)
                        .intentType(intentType)
                        .candidateStatus(VoiceCandidateStatus.UNSUPPORTED)
                        .description(VietnameseTextNormalizer.capitalize(display))
                        .amount(amount)
                        .transactionDate(date)
                        .transactionTime(time)
                        .confidence(confidence)
                        .readyToConfirm(false)
                        .missingFields(new ArrayList<>(missing))
                        .warnings(new ArrayList<>(warnings))
                        .unsupportedReason(reason)
                        .build()))
                .build();
    }

    private VoiceIntentType detectNonTransactionIntent(String normalized) {
        if (hasAny(normalized, "xem bao cao", "bao cao", "thong ke", "xem thong ke", "report", "dashboard")) {
            return VoiceIntentType.UNKNOWN_UNSUPPORTED;
        }
        if (hasAny(normalized, "so du", "cap nhat so du", "chot so du", "balance snapshot")) {
            return VoiceIntentType.WALLET_BALANCE_SNAPSHOT;
        }
        if (hasAny(normalized, "tra no", "thanh toan no", "dong no")) {
            return VoiceIntentType.DEBT_PAYMENT;
        }
        if (hasAny(normalized, "tao no", "them no", "cho vay", "di vay", "muon no")) {
            return VoiceIntentType.DEBT_CREATE;
        }
        if (hasAny(normalized, "muc tieu tiet kiem", "tiet kiem cho", "gop tiet kiem")) {
            return VoiceIntentType.SAVINGS_GOAL_CONTRIBUTION;
        }
        if (hasAny(normalized, "quy chim", "sinking fund", "gop quy")) {
            return VoiceIntentType.SINKING_FUND_CONTRIBUTION;
        }
        if (hasAny(normalized, "quy khan cap", "emergency fund", "khan cap")) {
            return VoiceIntentType.EMERGENCY_FUND_CONTRIBUTION;
        }
        if (hasAny(normalized, "hoa don dinh ky", "nghia vu dinh ky", "dong tien nha", "tra tien nha")) {
            return VoiceIntentType.RECURRING_OBLIGATION_PAYMENT;
        }
        return null;
    }

    private boolean hasAny(String normalized, String... phrases) {
        for (String phrase : phrases) {
            if (containsWordOrPhrase(normalized, phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean amountIntent(VoiceIntentType intentType) {
        return intentType != VoiceIntentType.UNKNOWN_UNSUPPORTED;
    }

    private List<String> requiredReferenceFields(VoiceIntentType intentType) {
        return switch (intentType) {
            case WALLET_BALANCE_SNAPSHOT -> List.of("WALLET");
            case DEBT_PAYMENT -> List.of("DEBT");
            case DEBT_CREATE -> List.of("COUNTERPARTY", "DIRECTION");
            case SAVINGS_GOAL_CONTRIBUTION -> List.of("SAVINGS_GOAL");
            case SINKING_FUND_CONTRIBUTION -> List.of("SINKING_FUND");
            case RECURRING_OBLIGATION_PAYMENT -> List.of("RECURRING_OBLIGATION");
            default -> List.of();
        };
    }


    private boolean looksLikeBareWalletTransfer(String normalized, String display, List<Wallet> wallets) {
        if (!normalized.contains(" sang ") && !normalized.contains(" qua ")) {
            return false;
        }
        Matcher matcher = TO_ONLY.matcher(normalized);
        if (!matcher.find()) {
            return false;
        }
        return matchWallet(VietnameseTextNormalizer.comparable(matcher.group(1)), matcher.group(1), wallets).isPresent()
                && matchWallet(VietnameseTextNormalizer.comparable(matcher.group(2)), matcher.group(2), wallets).isPresent();
    }
    private TransactionType inferType(String normalized, boolean transferText, CategoryMatch categoryMatch) {
        if (transferText) {
            return TransactionType.TRANSFER;
        }
        boolean income = INCOME_WORDS.stream().anyMatch(word -> containsWordOrPhrase(normalized, word));
        boolean expense = EXPENSE_WORDS.stream().anyMatch(word -> containsWordOrPhrase(normalized, word));
        if (income && expense) {
            return null;
        }
        if (income != expense) {
            return income ? TransactionType.INCOME : TransactionType.EXPENSE;
        }
        if (categoryMatch != null && !categoryMatch.ambiguous() && categoryMatch.category() != null) {
            CategoryType type = categoryMatch.category().getCategoryType();
            if (type == CategoryType.INCOME) {
                return TransactionType.INCOME;
            }
            if (type == CategoryType.EXPENSE) {
                return TransactionType.EXPENSE;
            }
        }
        return null;
    }

    private boolean categoryMatchesType(Category category, TransactionType type) {
        if (type == TransactionType.INCOME) {
            return category.getCategoryType() == CategoryType.INCOME;
        }
        if (type == TransactionType.EXPENSE) {
            return category.getCategoryType() == CategoryType.EXPENSE;
        }
        return false;
    }

    private Optional<CategoryMatch> matchCategory(String normalized, String display, List<CategoryKeyword> keywords, List<Category> categories) {
        List<CategoryMatch> matches = new ArrayList<>();
        for (CategoryKeyword keyword : keywords == null ? List.<CategoryKeyword>of() : keywords) {
            Category category = keyword.getCategory();
            if (category == null || !category.isActive() || category.isArchived()) {
                continue;
            }
            if (category.getCategoryType() != CategoryType.INCOME && category.getCategoryType() != CategoryType.EXPENSE) {
                continue;
            }
            String phrase = VietnameseTextNormalizer.comparable(keyword.getKeyword());
            if (phrase.isBlank()) {
                continue;
            }
            PhraseMatch phraseMatch = findPhrase(normalized, phrase).orElse(null);
            if (phraseMatch == null) {
                continue;
            }
            matches.add(new CategoryMatch(category, keyword, phraseMatch.exact(), phrase.length(), safePriority(keyword), phraseMatch.start(), phraseMatch.end(), displayText(display, phraseMatch.start(), phraseMatch.end()), false));
        }
        matches.sort((left, right) -> {
            int byExact = Boolean.compare(right.exact(), left.exact());
            if (byExact != 0) return byExact;
            int byLength = Integer.compare(right.length(), left.length());
            if (byLength != 0) return byLength;
            return Integer.compare(right.priority(), left.priority());
        });
        if (matches.isEmpty()) {
            return matchDirectCategory(normalized, display, categories)
                    .or(() -> matchAliasCategory(normalized, display, categories));
        }
        CategoryMatch top = matches.get(0);
        boolean ambiguous = matches.stream()
                .skip(1)
                .anyMatch(match -> match.sameRank(top) && !match.category().getId().equals(top.category().getId()));
        return Optional.of(ambiguous ? top.asAmbiguous() : top);
    }

    private Optional<CategoryMatch> matchDirectCategory(String normalized, String display, List<Category> categories) {
        List<CategoryMatch> matches = new ArrayList<>();
        for (Category category : categories == null ? List.<Category>of() : categories) {
            if (!category.isActive() || category.isArchived()) {
                continue;
            }
            if (category.getCategoryType() != CategoryType.INCOME && category.getCategoryType() != CategoryType.EXPENSE) {
                continue;
            }
            String name = VietnameseTextNormalizer.comparable(category.getName());
            PhraseMatch phraseMatch = findPhrase(normalized, name).orElse(null);
            if (phraseMatch == null) {
                continue;
            }
            int length = phraseMatch.end() - phraseMatch.start();
            matches.add(new CategoryMatch(category, null, phraseMatch.exact(), length, 0, phraseMatch.start(), phraseMatch.end(), displayText(display, phraseMatch.start(), phraseMatch.end()), false));
        }
        matches.sort((left, right) -> {
            int byExact = Boolean.compare(right.exact(), left.exact());
            if (byExact != 0) return byExact;
            int byLength = Integer.compare(right.length(), left.length());
            if (byLength != 0) return byLength;
            return Integer.compare(left.start(), right.start());
        });
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        CategoryMatch top = matches.get(0);
        boolean ambiguous = matches.stream()
                .skip(1)
                .anyMatch(match -> match.sameRank(top) && !match.category().getId().equals(top.category().getId()));
        return Optional.of(ambiguous ? top.asAmbiguous() : top);
    }

    private Optional<CategoryMatch> matchAliasCategory(String normalized, String display, List<Category> categories) {
        List<CategoryMatch> matches = new ArrayList<>();
        for (AliasRule rule : CATEGORY_ALIASES) {
            PhraseMatch aliasMatch = rule.aliases().stream()
                    .map(alias -> findPhrase(normalized, alias).orElse(null))
                    .filter(match -> match != null)
                    .max(Comparator.comparingInt(match -> match.end() - match.start()))
                    .orElse(null);
            if (aliasMatch == null) {
                continue;
            }
            Category category = findExistingCategory(categories, rule.type(), rule.categoryNames()).orElse(null);
            if (category == null) {
                continue;
            }
            int length = aliasMatch.end() - aliasMatch.start();
            matches.add(new CategoryMatch(category, null, aliasMatch.exact(), length, 0, aliasMatch.start(), aliasMatch.end(), displayText(display, aliasMatch.start(), aliasMatch.end()), false));
        }
        matches.sort((left, right) -> {
            int byExact = Boolean.compare(right.exact(), left.exact());
            if (byExact != 0) return byExact;
            int byLength = Integer.compare(right.length(), left.length());
            if (byLength != 0) return byLength;
            return Integer.compare(right.priority(), left.priority());
        });
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        CategoryMatch top = matches.get(0);
        boolean ambiguous = matches.stream()
                .skip(1)
                .anyMatch(match -> match.sameRank(top) && !match.category().getId().equals(top.category().getId()));
        return Optional.of(ambiguous ? top.asAmbiguous() : top);
    }

    private Optional<Category> findExistingCategory(List<Category> categories, CategoryType type, List<String> names) {
        for (String name : names) {
            String comparableName = VietnameseTextNormalizer.comparable(name);
            Optional<Category> match = (categories == null ? List.<Category>of() : categories).stream()
                    .filter(category -> category.isActive() && !category.isArchived())
                    .filter(category -> category.getCategoryType() == type)
                    .filter(category -> VietnameseTextNormalizer.comparable(category.getName()).equals(comparableName))
                    .findFirst();
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    private List<QuickEntryPreviewResponse.Candidate> buildCandidates(
            String display,
            List<QuickAmountParser.AmountCandidate> amountCandidates,
            List<CategoryKeyword> keywords,
            List<Category> categories,
            TransactionType fallbackType,
            TransactionStatus status,
            LocalDate transactionDate,
            LocalTime transactionTime,
            Wallet wallet,
            Wallet sourceWallet,
            Wallet destinationWallet) {
        if (amountCandidates.size() <= 1) {
            return List.of();
        }
        List<QuickEntryPreviewResponse.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < amountCandidates.size(); i++) {
            QuickAmountParser.AmountCandidate amountCandidate = amountCandidates.get(i);
            String segment = amountSegment(display, amountCandidates, i);
            String normalizedSegment = VietnameseTextNormalizer.comparable(segment);
            CategoryMatch segmentCategory = matchCategory(normalizedSegment, segment, keywords, categories).orElse(null);
            TransactionType segmentType = inferType(normalizedSegment, false, segmentCategory);
            if (segmentType == null) {
                segmentType = fallbackType == TransactionType.INCOME ? TransactionType.INCOME : TransactionType.EXPENSE;
            }
            Category category = segmentCategory == null || segmentCategory.ambiguous() || !categoryMatchesType(segmentCategory.category(), segmentType)
                    ? null
                    : segmentCategory.category();
            String description = candidateDescription(segment, amountCandidate);
            List<String> missingFields = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            if (category == null && segmentType != TransactionType.TRANSFER) {
                missingFields.add("CATEGORY");
                warnings.add("UNKNOWN_CATEGORY");
            }
            if (segmentType == TransactionType.INCOME || segmentType == TransactionType.EXPENSE) {
                if (wallet == null) {
                    missingFields.add("WALLET");
                }
            } else if (segmentType == TransactionType.TRANSFER) {
                if (sourceWallet == null) {
                    missingFields.add("SOURCE_WALLET");
                }
                if (destinationWallet == null) {
                    missingFields.add("DESTINATION_WALLET");
                }
            }
            if (transactionDate == null) {
                missingFields.add("DATE");
            }
            boolean ready = missingFields.isEmpty();
            candidates.add(QuickEntryPreviewResponse.Candidate.builder()
                    .candidateId(candidateId(display, i, segment, amountCandidate.amount()))
                    .clientCandidateId(candidateId(display, i, segment, amountCandidate.amount()))
                    .intentType(intentType(segmentType))
                    .candidateStatus(ready ? VoiceCandidateStatus.READY : VoiceCandidateStatus.NEEDS_REVIEW)
                    .originalText(segment)
                    .description(description)
                    .amount(amountCandidate.amount())
                    .type(segmentType)
                    .status(status)
                    .walletId(wallet == null ? null : wallet.getId())
                    .walletName(wallet == null ? null : wallet.getName())
                    .categoryId(category == null ? null : category.getId())
                    .categoryName(category == null ? null : category.getName())
                    .sourceWalletId(sourceWallet == null ? null : sourceWallet.getId())
                    .sourceWalletName(sourceWallet == null ? null : sourceWallet.getName())
                    .destinationWalletId(destinationWallet == null ? null : destinationWallet.getId())
                    .destinationWalletName(destinationWallet == null ? null : destinationWallet.getName())
                    .transactionDate(transactionDate)
                    .transactionTime(transactionTime)
                    .spendingScope(spendingScope(category))
                    .confidence(ready ? 0.95 : 0.65)
                    .readyToConfirm(ready)
                    .validationStatus(ready ? "READY" : "NEEDS_REVIEW")
                    .missingFields(missingFields)
                    .warnings(warnings)
                    .build());
        }
        return candidates;
    }

    private com.moneyflowbackend.common.model.SpendingScope spendingScope(Category category) {
        return category == null ? null : category.getDefaultSpendingScope();
    }

    private VoiceIntentType intentType(TransactionType type) {
        if (type == TransactionType.EXPENSE) {
            return VoiceIntentType.TRANSACTION_EXPENSE;
        }
        if (type == TransactionType.INCOME) {
            return VoiceIntentType.TRANSACTION_INCOME;
        }
        if (type == TransactionType.TRANSFER) {
            return VoiceIntentType.TRANSACTION_TRANSFER;
        }
        return VoiceIntentType.UNKNOWN_UNSUPPORTED;
    }

    private String candidateId(String display, int index, String segment, BigDecimal amount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((display + "|" + index + "|" + segment + "|" + amount).getBytes(StandardCharsets.UTF_8));
            return "cand_" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            return "cand_" + index;
        }
    }

    private String amountSegment(String display, List<QuickAmountParser.AmountCandidate> amountCandidates, int index) {
        QuickAmountParser.AmountCandidate current = amountCandidates.get(index);
        int start = index == 0 ? 0 : amountCandidates.get(index - 1).end();
        int end = current.end();
        String segment = displayText(display, start, end);
        return segment.isBlank() ? display : segment;
    }

    private String candidateDescription(String segment, QuickAmountParser.AmountCandidate amountCandidate) {
        String cleaned = segment == null ? "" : segment;
        if (amountCandidate != null && amountCandidate.text() != null && !amountCandidate.text().isBlank()) {
            cleaned = cleaned.replace(amountCandidate.text(), " ");
        }
        cleaned = cleaned
                .replaceAll("(?i)\\b(hôm nay|hom nay|ngày|ngay|nay)\\b", " ")
                .replaceAll("\\b\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return VietnameseTextNormalizer.capitalize(cleaned);
    }

    private TransferWallets matchTransferWallets(String normalized, String display, List<Span> removableSpans, List<Wallet> wallets) {
        String clean = removeSpans(normalized, removableSpans)
                .replaceAll("\\b(chuyen tien|chuyen|transfer)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher fromTo = FROM_TO.matcher(clean);
        if (fromTo.find()) {
            WalletMatch source = matchWallet(VietnameseTextNormalizer.comparable(fromTo.group(1)), fromTo.group(1), wallets).orElse(null);
            WalletMatch destination = matchWallet(VietnameseTextNormalizer.comparable(fromTo.group(2)), fromTo.group(2), wallets).orElse(null);
            return new TransferWallets(source == null ? null : source.wallet(), destination == null ? null : destination.wallet(), source, destination);
        }
        Matcher toOnly = TO_ONLY.matcher(clean);
        if (toOnly.find()) {
            WalletMatch source = matchWallet(VietnameseTextNormalizer.comparable(toOnly.group(1)), toOnly.group(1), wallets).orElse(null);
            WalletMatch destination = matchWallet(VietnameseTextNormalizer.comparable(toOnly.group(2)), toOnly.group(2), wallets).orElse(null);
            return new TransferWallets(source == null ? null : source.wallet(), destination == null ? null : destination.wallet(), source, destination);
        }
        List<WalletMatch> allMatches = allWalletMatches(normalized, display, wallets);
        if (allMatches.size() >= 2) {
            WalletMatch source = allMatches.get(0);
            WalletMatch destination = allMatches.get(1);
            return new TransferWallets(source.wallet(), destination.wallet(), source, destination);
        }
        return new TransferWallets(null, null, null, null);
    }

    private Optional<WalletMatch> matchWallet(String normalized, String display, List<Wallet> wallets) {
        List<WalletMatch> matches = allWalletMatches(normalized, display, wallets);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        WalletMatch top = matches.get(0);
        boolean ambiguous = matches.stream()
                .skip(1)
                .anyMatch(match -> match.sameRank(top) && !match.wallet().getId().equals(top.wallet().getId()));
        return Optional.of(ambiguous ? top.asAmbiguous() : top);
    }

    private List<WalletMatch> allWalletMatches(String normalized, String display, List<Wallet> wallets) {
        List<WalletMatch> matches = new ArrayList<>();
        for (Wallet wallet : wallets == null ? List.<Wallet>of() : wallets) {
            if (!wallet.isActive()) {
                continue;
            }
            for (WalletAlias alias : aliases(wallet)) {
                PhraseMatch phraseMatch = findPhrase(normalized, alias.text()).orElse(null);
                if (phraseMatch == null) {
                    continue;
                }
                matches.add(new WalletMatch(wallet, alias.score(), alias.text().length(), phraseMatch.start(), phraseMatch.end(), displayText(display, phraseMatch.start(), phraseMatch.end()), false));
            }
        }
        matches.sort((left, right) -> {
            int byScore = Integer.compare(right.score(), left.score());
            if (byScore != 0) return byScore;
            int byLength = Integer.compare(right.length(), left.length());
            if (byLength != 0) return byLength;
            return Integer.compare(left.start(), right.start());
        });
        return distinctWallets(matches);
    }

    private List<WalletMatch> distinctWallets(List<WalletMatch> matches) {
        List<WalletMatch> distinct = new ArrayList<>();
        for (WalletMatch match : matches) {
            boolean exists = distinct.stream().anyMatch(existing -> existing.wallet().getId().equals(match.wallet().getId()));
            if (!exists) {
                distinct.add(match);
            }
        }
        return distinct;
    }

    private List<WalletAlias> aliases(Wallet wallet) {
        List<WalletAlias> aliases = new ArrayList<>();
        String name = VietnameseTextNormalizer.comparable(wallet.getName());
        aliases.add(new WalletAlias(name, 100));
        String acronym = acronym(name);
        if (acronym.length() >= 2) {
            aliases.add(new WalletAlias(acronym, 90));
        }
        if (wallet.getWalletType() == WalletType.CASH || name.contains("tien mat")) {
            aliases.add(new WalletAlias("cash", 80));
            aliases.add(new WalletAlias("tm", 75));
        }
        return aliases.stream().filter(alias -> !alias.text().isBlank()).toList();
    }

    private Optional<Wallet> preferredWallet(List<Wallet> wallets, UUID suggestedWalletId) {
        List<Wallet> activeWallets = (wallets == null ? List.<Wallet>of() : wallets).stream()
                .filter(Wallet::isActive)
                .toList();
        if (suggestedWalletId != null) {
            Optional<Wallet> suggested = activeWallets.stream()
                    .filter(wallet -> wallet.getId().equals(suggestedWalletId))
                    .findFirst();
            if (suggested.isPresent()) {
                return suggested;
            }
        }
        return activeWallets.stream()
                .filter(Wallet::isDefault)
                .findFirst();
    }

    private boolean ready(
            TransactionType type,
            BigDecimal amount,
            Category category,
            Wallet wallet,
            Wallet sourceWallet,
            Wallet destinationWallet,
            LocalDate transactionDate,
            Set<String> warnings,
            Set<String> missing) {
        if (!missing.isEmpty() || warnings.contains("AMBIGUOUS_AMOUNT") || warnings.contains("AMBIGUOUS_CATEGORY")
                || warnings.contains("AMBIGUOUS_WALLET") || warnings.contains("TRANSFER_SAME_WALLET")
                || warnings.contains("INVALID_AMOUNT") || warnings.contains("INVALID_DATE")) {
            return false;
        }
        if (type == TransactionType.INCOME || type == TransactionType.EXPENSE) {
            return amount != null && category != null && wallet != null && transactionDate != null;
        }
        if (type == TransactionType.TRANSFER) {
            return amount != null && sourceWallet != null && destinationWallet != null && transactionDate != null
                    && !sourceWallet.getId().equals(destinationWallet.getId());
        }
        return false;
    }

    private double confidence(boolean ready, Set<String> missing, Set<String> warnings) {
        double score = ready ? 1.0 : 0.75;
        score -= missing.size() * 0.16;
        score -= warnings.stream().filter(warning -> warning.startsWith("AMBIGUOUS") || warning.startsWith("INVALID")).count() * 0.18;
        score -= warnings.stream().filter(warning -> warning.equals("DEFAULT_WALLET_USED") || warning.equals("ASSUMED_THOUSAND_UNIT") || warning.equals("FUTURE_DATE_ASSUMED_PLANNED")).count() * 0.05;
        return Math.max(0.0, Math.min(1.0, Math.round(score * 100.0) / 100.0));
    }

    private String description(
            String display,
            List<Span> removableSpans,
            TransactionType type,
            Wallet destinationWallet,
            QuickAmountParser.AmountCandidate amountCandidate,
            String matchedWalletText) {
        if (type == TransactionType.TRANSFER) {
            return destinationWallet == null ? "Chuyen tien" : "Chuyen sang " + destinationWallet.getName();
        }
        String cleaned = removeSpans(display, removableSpans);
        if (amountCandidate != null && !amountCandidate.text().isBlank()) {
            cleaned = cleaned.replace(amountCandidate.text(), " ");
        }
        if (matchedWalletText != null && !matchedWalletText.isBlank()) {
            cleaned = cleaned.replace(matchedWalletText, " ");
        }
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return VietnameseTextNormalizer.capitalize(cleaned);
    }

    private String removeSpans(String text, List<Span> spans) {
        if (text == null || text.isBlank() || spans.isEmpty()) {
            return text == null ? "" : text;
        }
        StringBuilder builder = new StringBuilder(text);
        spans.stream()
                .sorted(Comparator.comparingInt(Span::start).reversed())
                .forEach(span -> {
                    int start = Math.max(0, Math.min(span.start(), builder.length()));
                    int end = Math.max(start, Math.min(span.end(), builder.length()));
                    builder.replace(start, end, " ");
                });
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    private Optional<PhraseMatch> findPhrase(String normalized, String phrase) {
        if (normalized == null || normalized.isBlank() || phrase == null || phrase.isBlank()) {
            return Optional.empty();
        }
        Pattern exact = Pattern.compile("(?<!\\S)" + Pattern.quote(phrase) + "(?!\\S)");
        Matcher matcher = exact.matcher(normalized);
        if (matcher.find()) {
            return Optional.of(new PhraseMatch(matcher.start(), matcher.end(), true));
        }
        int index = normalized.indexOf(phrase);
        return index < 0 ? Optional.empty() : Optional.of(new PhraseMatch(index, index + phrase.length(), false));
    }

    private boolean containsWordOrPhrase(String normalized, String phrase) {
        if (normalized == null || normalized.isBlank() || phrase == null || phrase.isBlank()) {
            return false;
        }
        return Pattern.compile("(?<!\\S)" + Pattern.quote(phrase) + "(?!\\S)").matcher(normalized).find();
    }

    private int safePriority(CategoryKeyword keyword) {
        return keyword.getPriority() == null ? 0 : keyword.getPriority();
    }

    private String displayText(String display, int start, int end) {
        if (display == null) {
            return "";
        }
        int safeStart = Math.max(0, Math.min(start, display.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, display.length()));
        return display.substring(safeStart, safeEnd);
    }

    private String acronym(String normalizedName) {
        StringBuilder builder = new StringBuilder();
        for (String part : normalizedName.split("\\s+")) {
            if (!part.isBlank()) {
                builder.append(part.charAt(0));
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private record Span(int start, int end) {
    }

    private record PhraseMatch(int start, int end, boolean exact) {
    }

    private record WalletAlias(String text, int score) {
    }

    private record CategoryMatch(Category category, CategoryKeyword keyword, boolean exact, int length, int priority, int start, int end, String text, boolean ambiguous) {
        boolean sameRank(CategoryMatch other) {
            return exact == other.exact && length == other.length && priority == other.priority;
        }

        CategoryMatch asAmbiguous() {
            return new CategoryMatch(category, keyword, exact, length, priority, start, end, text, true);
        }
    }

    private record WalletMatch(Wallet wallet, int score, int length, int start, int end, String text, boolean ambiguous) {
        boolean sameRank(WalletMatch other) {
            return score == other.score && length == other.length;
        }

        WalletMatch asAmbiguous() {
            return new WalletMatch(wallet, score, length, start, end, text, true);
        }
    }

    private record TransferWallets(Wallet source, Wallet destination, WalletMatch sourceMatch, WalletMatch destinationMatch) {
    }

    private record AliasRule(CategoryType type, List<String> aliases, List<String> categoryNames) {
    }
}
