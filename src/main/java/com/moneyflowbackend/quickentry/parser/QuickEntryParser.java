package com.moneyflowbackend.quickentry.parser;

import com.moneyflowbackend.category.model.Category;
import com.moneyflowbackend.category.model.CategoryKeyword;
import com.moneyflowbackend.category.model.CategoryType;
import com.moneyflowbackend.common.model.SpendingScope;
import com.moneyflowbackend.quickentry.dto.QuickEntryPreviewResponse;
import com.moneyflowbackend.quickentry.dto.VoiceCandidateStatus;
import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
import com.moneyflowbackend.quickentry.dto.VoiceLedgerEffect;
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
            "hoan tien", "gia dinh gui", "kiem duoc", "kiem tien", "nhan luong", "duoc tra", "duoc chuyen", "ky");
    private static final Set<String> EXPENSE_WORDS = Set.of(
            "chi", "mua", "tra", "dong", "dong tien", "thanh toan", "an", "uong", "cafe",
            "ca phe", "xang", "do xang", "gui xe", "het", "cua hang");
    private static final List<AliasRule> CATEGORY_ALIASES = List.of(
            new AliasRule(CategoryType.EXPENSE, List.of("do an ngoai", "mua do an ngoai", "an ngoai"),
                    List.of("Mua do an ngoai")),
            new AliasRule(CategoryType.EXPENSE, List.of("cafe", "ca phe"),
                    List.of("Mua nuoc uong")),
            new AliasRule(CategoryType.EXPENSE, List.of("xang", "xang xe"),
                    List.of("Xang xe", "car")),
            new AliasRule(CategoryType.EXPENSE, List.of("gui xe"),
                    List.of("Gui xe")),
            new AliasRule(CategoryType.EXPENSE, List.of("an", "an sang", "an trua", "an toi", "com", "bun", "pho", "do an", "tien an"),
                    List.of("An uong", "An", "Food", "Food & Drink", "food")),
            new AliasRule(CategoryType.EXPENSE, List.of("ca phe", "cafe", "coffee", "uong ca phe", "tra sua", "nuoc", "uong nuoc"),
                    List.of("Ca phe", "An uong", "Do uong", "Food & Drink", "coffee")),
            new AliasRule(CategoryType.EXPENSE, List.of("xang", "xang xe", "gui xe", "grab", "taxi"),
                    List.of("Di lai", "Di chuyen", "Transport", "car")),
            new AliasRule(CategoryType.INCOME, List.of("luong", "thuong"),
                    List.of("Luong", "Thu nhap", "salary")),
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
        if (amountCandidates.size() > 1) {
            explicitIntent = null;
        }
        WalletMatch snapshotWallet = detectWalletSnapshot(normalized, display, wallets).orElse(null);
        if (snapshotWallet != null && amount != null && amountCandidates.size() == 1) {
            return walletSnapshotDraft(rawInput, display, amount, transactionDate, dateResult.time(), snapshotWallet);
        }
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
        } else if (type == TransactionType.INCOME) {
            missing.add("incomeSourceId");
            categoryMatch = null;
        } else if (ambiguousCategory) {
            missing.add("categoryId");
            warnings.add("AMBIGUOUS_CATEGORY");
        } else if (categoryMatch != null && categoryMatch.category() != null) {
            if (!categoryMatchesType(categoryMatch.category(), type)) {
                missing.add("categoryId");
                warnings.add("CATEGORY_TYPE_MISMATCH");
            } else {
            category = categoryMatch.category();
            matchedKeyword = categoryMatch.keyword() == null ? categoryMatch.text() : categoryMatch.keyword().getKeyword();
            }
        } else if (type == TransactionType.EXPENSE) {
            missing.add("categoryId");
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
                missing.add("sourceWalletId");
            }
            if (destinationWallet == null) {
                missing.add("destinationWalletId");
            }
            if (sourceWallet != null && destinationWallet != null && sourceWallet.getId().equals(destinationWallet.getId())) {
                warnings.add("TRANSFER_SAME_WALLET");
            }
        } else {
            WalletMatch walletMatch = matchWallet(normalized, display, wallets).orElse(null);
            if (walletMatch != null && walletMatch.ambiguous()) {
                missing.add("walletId");
                warnings.add("AMBIGUOUS_WALLET");
            } else if (walletMatch != null) {
                wallet = walletMatch.wallet();
                matchedWalletText = walletMatch.text();
                removableSpans.add(new Span(walletMatch.start(), walletMatch.end()));
            } else if (type == TransactionType.EXPENSE) {
                if (WALLET_HINT.matcher(normalized).find()) {
                    missing.add("walletId");
                    warnings.add("UNKNOWN_WALLET");
                } else {
                    wallet = preferredWallet(wallets, suggestedWalletId).orElse(null);
                    if (wallet == null) {
                        missing.add("walletId");
                    } else if (suggestedWalletId != null && wallet.getId().equals(suggestedWalletId)) {
                        warnings.add("SUGGESTED_WALLET_USED");
                    } else {
                        warnings.add("DEFAULT_WALLET_USED");
                    }
                }
            }
        }

        List<QuickEntryPreviewResponse.Candidate> candidates = buildCandidates(display, amountCandidates, keywords, categories, wallets,
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
                .ledgerEffect(ledgerEffect(type, wallet, sourceWallet, destinationWallet))
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
                .spendingScope(defaultExpenseScope(type, category))
                .description(description)
                .note(null)
                .confidence(confidence)
                .readyToConfirm(ready)
                .commitSupported(type != null)
                .affectsWalletBalance(type != TransactionType.INCOME || wallet != null)
                .unsupportedReason(type == null ? "UNKNOWN_UNSUPPORTED" : null)
                .targetModule(targetModule(intentType(type)))
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
        } else if (!amountIntent(intentType)) {
            missing.remove("AMOUNT");
        }
        if (date == null) {
            missing.add("DATE");
        }
        missing.addAll(requiredReferenceFields(intentType));
        warnings.add("UNSUPPORTED_INTENT");
        warnings.add("VOICE_INTENT_NOT_COMMITTABLE");
        String reason = unsupportedReason(intentType);
        String candidateId = candidateId(display, 0, display, amount);
        String route = suggestedManualRoute(intentType);
        String label = suggestedManualActionLabel(intentType);
        VoiceCandidateStatus candidateStatus = candidateStatus(intentType);
        return QuickEntryPreviewResponse.builder()
                .candidateId(candidateId)
                .intentType(intentType)
                .candidateStatus(candidateStatus)
                .ledgerEffect(ledgerEffect(intentType))
                .rawInput(rawInput)
                .normalizedInput(display)
                .amount(amount)
                .transactionDate(date)
                .transactionTime(time)
                .description(VietnameseTextNormalizer.capitalize(display))
                .confidence(confidence)
                .readyToConfirm(false)
                .commitSupported(false)
                .missingFields(new ArrayList<>(missing))
                .warnings(new ArrayList<>(warnings))
                .unsupportedReason(reason)
                .suggestedManualRoute(route)
                .targetModule(targetModule(intentType))
                .suggestedManualActionLabel(label)
                .candidates(List.of(QuickEntryPreviewResponse.Candidate.builder()
                        .candidateId(candidateId)
                        .intentType(intentType)
                        .candidateStatus(candidateStatus)
                        .ledgerEffect(ledgerEffect(intentType))
                        .description(VietnameseTextNormalizer.capitalize(display))
                        .amount(amount)
                        .transactionDate(date)
                        .transactionTime(time)
                        .confidence(confidence)
                        .readyToConfirm(false)
                        .commitSupported(false)
                        .missingFields(new ArrayList<>(missing))
                        .warnings(new ArrayList<>(warnings))
                        .unsupportedReason(reason)
                        .suggestedManualRoute(route)
                        .targetModule(targetModule(intentType))
                        .suggestedManualActionLabel(label)
                        .build()))
                .build();
    }

    private QuickEntryPreviewResponse walletSnapshotDraft(
            String rawInput,
            String display,
            BigDecimal amount,
            LocalDate date,
            LocalTime time,
            WalletMatch walletMatch) {
        String candidateId = candidateId(display, 0, display, amount);
        boolean ambiguous = walletMatch.ambiguous();
        Wallet wallet = ambiguous ? null : walletMatch.wallet();
        List<String> missing = ambiguous ? List.of("walletId") : List.of();
        List<String> warnings = ambiguous
                ? List.of("WALLET_MATCH_AMBIGUOUS")
                : List.of("WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED");
        return QuickEntryPreviewResponse.builder()
                .candidateId(candidateId)
                .intentType(VoiceIntentType.WALLET_BALANCE_SNAPSHOT)
                .candidateStatus(VoiceCandidateStatus.NEEDS_REVIEW)
                .ledgerEffect(VoiceLedgerEffect.DOES_NOT_AFFECT_WALLET)
                .rawInput(rawInput)
                .normalizedInput(display)
                .amount(amount)
                .walletId(wallet == null ? null : wallet.getId())
                .walletName(wallet == null ? null : wallet.getName())
                .transactionDate(date)
                .transactionTime(time)
                .description(VietnameseTextNormalizer.capitalize(display))
                .confidence(wallet == null ? 0.65 : 0.85)
                .readyToConfirm(false)
                .commitSupported(false)
                .affectsWalletBalance(false)
                .missingFields(new ArrayList<>(missing))
                .warnings(new ArrayList<>(warnings))
                .unsupportedReason("WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED")
                .suggestedManualRoute("/wallets")
                .targetModule("WALLET_SNAPSHOT")
                .suggestedManualActionLabel("Mở ví")
                .candidates(List.of(walletSnapshotCandidate(display, display, 0, amount, date, time, walletMatch)))
                .build();
    }

    private VoiceIntentType detectNonTransactionIntent(String normalized) {
        if ((hasAny(normalized, "vi", "wallet") && hasAny(normalized, "con", "con lai", "dang co"))
                || hasAny(normalized, "cuoi ngay", "so du", "cap nhat so du", "chot so du", "balance snapshot")) return VoiceIntentType.WALLET_BALANCE_SNAPSHOT;
        if (hasAny(normalized, "nhan lai ngan hang", "lai ngan hang", "lai cake", "tien lai hom nay")) return VoiceIntentType.INTEREST_INCOME;
        if (hasAny(normalized, "tra tien lai", "tra lai vay", "tra lai")) return VoiceIntentType.INTEREST_EXPENSE;
        if (hasAny(normalized, "toi muon", "minh muon", "toi vay", "minh vay", "cho toi muon", "cho minh muon", "cho toi vay", "cho minh vay")) return VoiceIntentType.BORROWING_RECEIPT;
        if (hasAny(normalized, "bao tra toi", "tra no toi", "tra no minh", "thu no", "tra toi", "tra minh", "chuyen lai", "gui lai")) return VoiceIntentType.LOAN_COLLECTION;
        if (hasAny(normalized, "tra no", "thanh toan no", "dong no", "tra cho", "tra chi", "tra anh", "tra em")) return VoiceIntentType.PAYABLE_REPAYMENT;
        if (hasAny(normalized, "cho vay", "cho muon", "dua vay", "dua muon")
                || (hasAny(normalized, "cho") && hasAny(normalized, "muon", "vay"))) return VoiceIntentType.LOAN_DISBURSEMENT;
        if (hasAny(normalized, "toi no", "minh no")) return VoiceIntentType.DEBT_CREATE_PAYABLE;
        if (hasAny(normalized, "no toi", "no minh", "thieu toi", "tao no", "them no")) return VoiceIntentType.DEBT_CREATE_RECEIVABLE;
        if (hasAny(normalized, "gui tiet kiem", "muc tieu tiet kiem", "tiet kiem cho", "gop tiet kiem", "vao muc tieu", "de danh")) return VoiceIntentType.SAVINGS_GOAL_CONTRIBUTION;
        if (hasAny(normalized, "quy khan cap", "emergency fund", "khan cap")) return VoiceIntentType.EMERGENCY_FUND_CONTRIBUTION;
        if (hasAny(normalized, "quy chim", "quy chi truoc", "sinking fund", "gop quy", "bo vao quy", "vao quy")) return VoiceIntentType.SINKING_FUND_CONTRIBUTION;
        if (hasAny(normalized, "hoa don dinh ky", "nghia vu dinh ky", "dong tien nha", "tra tien nha", "tien dien", "tien wifi")) return VoiceIntentType.RECURRING_OBLIGATION_PAYMENT;
        if (hasAny(normalized, "xem bao cao", "bao cao", "dashboard", "phan tich", "analytics")) return VoiceIntentType.ANALYTICS_QUERY;
        if ((hasAny(normalized, "bao nhieu", "may tien", "tong") && hasAny(normalized, "thang nay", "tuan nay", "tieu", "chi", "thu", "kiem", "con lai"))
                || hasAny(normalized, "thong ke", "xem thong ke", "report", "tien di dau", "no con bao nhieu", "du chua")) return VoiceIntentType.STAT_QUERY;
        return null;
    }

    private String suggestedManualRoute(VoiceIntentType intentType) {
        return switch (intentType) {
            case DEBT_CREATE, DEBT_CREATE_RECEIVABLE, DEBT_CREATE_PAYABLE, DEBT_PAYMENT, LOAN_DISBURSEMENT, LOAN_COLLECTION, BORROWING_RECEIPT, PAYABLE_REPAYMENT, INTEREST_EXPENSE -> "/debts";
            case INTEREST_INCOME -> "/income-sources";
            case SAVINGS_GOAL_CONTRIBUTION -> "/savings-goals";
            case SINKING_FUND_CONTRIBUTION -> "/sinking-funds";
            case EMERGENCY_FUND_CONTRIBUTION -> "/emergency-fund";
            case WALLET_BALANCE_SNAPSHOT, DAILY_CLOSING -> "/wallets";
            case RECURRING_OBLIGATION_PAYMENT -> "/recurring-obligations";
            case STAT_QUERY, ANALYTICS_QUERY -> "/reports";
            default -> null;
        };
    }

    private String suggestedManualActionLabel(VoiceIntentType intentType) {
        if (intentType == VoiceIntentType.BORROWING_RECEIPT) {
            return "Open debts";
        }
        return switch (intentType) {
            case DEBT_CREATE, DEBT_CREATE_RECEIVABLE, DEBT_CREATE_PAYABLE, DEBT_PAYMENT, LOAN_DISBURSEMENT, LOAN_COLLECTION, PAYABLE_REPAYMENT, INTEREST_EXPENSE -> "Mở trang nợ";
            case INTEREST_INCOME -> "Mở nguồn thu";
            case SAVINGS_GOAL_CONTRIBUTION -> "Mở mục tiêu tiết kiệm";
            case SINKING_FUND_CONTRIBUTION -> "Mở quỹ";
            case EMERGENCY_FUND_CONTRIBUTION -> "Mở quỹ dự phòng";
            case WALLET_BALANCE_SNAPSHOT, DAILY_CLOSING -> "Mở ví";
            case RECURRING_OBLIGATION_PAYMENT -> "Mở khoản định kỳ";
            case STAT_QUERY, ANALYTICS_QUERY -> "Mở báo cáo";
            default -> null;
        };
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
        return intentType != VoiceIntentType.UNKNOWN_UNSUPPORTED
                && intentType != VoiceIntentType.STAT_QUERY
                && intentType != VoiceIntentType.ANALYTICS_QUERY;
    }

    private List<String> requiredReferenceFields(VoiceIntentType intentType) {
        return switch (intentType) {
            case WALLET_BALANCE_SNAPSHOT, DAILY_CLOSING -> List.of("walletId");
            case DEBT_PAYMENT, LOAN_COLLECTION, PAYABLE_REPAYMENT, INTEREST_EXPENSE -> List.of("debtId");
            case DEBT_CREATE, DEBT_CREATE_RECEIVABLE, DEBT_CREATE_PAYABLE, LOAN_DISBURSEMENT, BORROWING_RECEIPT -> List.of("counterpartyId");
            case INTEREST_INCOME -> List.of("incomeSourceId");
            case SAVINGS_GOAL_CONTRIBUTION -> List.of("savingsGoalId");
            case SINKING_FUND_CONTRIBUTION -> List.of("sinkingFundId");
            case RECURRING_OBLIGATION_PAYMENT -> List.of("recurringObligationId");
            default -> List.of();
        };
    }

    private String unsupportedReason(VoiceIntentType intentType) {
        return ledgerEffect(intentType) == VoiceLedgerEffect.READ_ONLY
                ? "VOICE_INTENT_READ_ONLY"
                : "VOICE_COMMIT_NOT_SUPPORTED";
    }

    private VoiceCandidateStatus candidateStatus(VoiceIntentType intentType) {
        return ledgerEffect(intentType) == VoiceLedgerEffect.READ_ONLY
                ? VoiceCandidateStatus.READ_ONLY
                : VoiceCandidateStatus.MANUAL;
    }

    private VoiceLedgerEffect ledgerEffect(VoiceIntentType intentType) {
        return switch (intentType) {
            case STAT_QUERY, ANALYTICS_QUERY -> VoiceLedgerEffect.READ_ONLY;
            case WALLET_BALANCE_SNAPSHOT, DAILY_CLOSING -> VoiceLedgerEffect.DOES_NOT_AFFECT_WALLET;
            case UNKNOWN_UNSUPPORTED -> VoiceLedgerEffect.MANUAL_UNSUPPORTED;
            default -> VoiceLedgerEffect.MANUAL_UNSUPPORTED;
        };
    }

    private VoiceLedgerEffect ledgerEffect(TransactionType type, Wallet wallet, Wallet sourceWallet, Wallet destinationWallet) {
        if (type == TransactionType.EXPENSE || type == TransactionType.INCOME) {
            if (type == TransactionType.INCOME && wallet == null) {
                return VoiceLedgerEffect.DOES_NOT_AFFECT_WALLET;
            }
            return wallet == null ? VoiceLedgerEffect.NEEDS_WALLET_REVIEW : VoiceLedgerEffect.AFFECTS_WALLET_NOW;
        }
        if (type == TransactionType.TRANSFER) {
            return sourceWallet == null || destinationWallet == null
                    ? VoiceLedgerEffect.NEEDS_WALLET_REVIEW
                    : VoiceLedgerEffect.AFFECTS_WALLET_NOW;
        }
        return VoiceLedgerEffect.MANUAL_UNSUPPORTED;
    }

    private Optional<WalletMatch> detectWalletSnapshot(String normalized, String display, List<Wallet> wallets) {
        if (normalized == null || !hasAny(normalized, "con", "hien con", "dang co", "so du")) {
            return Optional.empty();
        }
        if (hasAny(normalized, "con no", "con thieu", "toi con no", "minh con no")) {
            return Optional.empty();
        }
        return matchWallet(normalized, display, wallets);
    }

    private String targetModule(VoiceIntentType intentType) {
        return switch (intentType) {
            case STAT_QUERY, ANALYTICS_QUERY -> "REPORTS";
            case WALLET_BALANCE_SNAPSHOT, DAILY_CLOSING -> "WALLET_SNAPSHOT";
            case DEBT_CREATE, DEBT_CREATE_RECEIVABLE, DEBT_CREATE_PAYABLE, DEBT_PAYMENT, LOAN_DISBURSEMENT, LOAN_COLLECTION, BORROWING_RECEIPT, PAYABLE_REPAYMENT, INTEREST_EXPENSE -> "DEBT";
            case INTEREST_INCOME -> "INCOME";
            case SAVINGS_GOAL_CONTRIBUTION -> "SAVINGS_GOAL";
            case SINKING_FUND_CONTRIBUTION -> "SINKING_FUND";
            case EMERGENCY_FUND_CONTRIBUTION -> "EMERGENCY_FUND";
            case RECURRING_OBLIGATION_PAYMENT -> "RECURRING_OBLIGATION";
            default -> null;
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
                    .filter(category -> VietnameseTextNormalizer.comparable(category.getName()).equals(comparableName)
                            || VietnameseTextNormalizer.comparable(category.getIcon()).equals(comparableName))
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
            List<Wallet> wallets,
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
        addLeadingMissingAmountIncomeCandidate(display, amountCandidates, transactionDate, transactionTime, candidates);
        for (int i = 0; i < amountCandidates.size(); i++) {
            QuickAmountParser.AmountCandidate amountCandidate = amountCandidates.get(i);
            String segment = amountSegment(display, amountCandidates, i);
            String normalizedSegment = VietnameseTextNormalizer.comparable(segment);
            BigDecimal segmentAmount = contextualAmount(amountCandidate, normalizedSegment);
            VoiceIntentType segmentIntent = detectNonTransactionIntent(normalizedSegment);
            WalletMatch segmentSnapshotWallet = detectWalletSnapshot(normalizedSegment, segment, wallets).orElse(null);
            if (segmentSnapshotWallet != null) {
                candidates.add(walletSnapshotCandidate(display, segment, i, segmentAmount, transactionDate, transactionTime, segmentSnapshotWallet));
                continue;
            }
            if (segmentIntent == null && !hasTransactionWord(normalizedSegment)) {
                segmentIntent = detectPostAmountIntent(trailingContext(display, amountCandidates, i));
            }
            if (segmentIntent != null) {
                List<String> missingFields = new ArrayList<>(requiredReferenceFields(segmentIntent));
                List<String> warnings = new ArrayList<>(List.of("VOICE_INTENT_NOT_COMMITTABLE"));
                VoiceCandidateStatus candidateStatus = candidateStatus(segmentIntent);
                candidates.add(QuickEntryPreviewResponse.Candidate.builder()
                        .candidateId(candidateId(display, i, segment, segmentAmount))
                        .clientCandidateId(candidateId(display, i, segment, segmentAmount))
                        .intentType(segmentIntent)
                        .candidateStatus(candidateStatus)
                        .ledgerEffect(ledgerEffect(segmentIntent))
                        .originalText(segment)
                        .description(candidateDescription(segment, amountCandidate))
                        .amount(segmentAmount)
                        .transactionDate(transactionDate)
                        .transactionTime(transactionTime)
                        .confidence(0.65)
                        .readyToConfirm(false)
                        .commitSupported(false)
                        .validationStatus("UNSUPPORTED")
                        .missingFields(missingFields)
                        .warnings(warnings)
                        .unsupportedReason(unsupportedReason(segmentIntent))
                        .suggestedManualRoute(suggestedManualRoute(segmentIntent))
                        .targetModule(targetModule(segmentIntent))
                        .suggestedManualActionLabel(suggestedManualActionLabel(segmentIntent))
                        .build());
                continue;
            }
            CategoryMatch segmentCategory = matchCategory(normalizedSegment, segment, keywords, categories).orElse(null);
            boolean segmentTransfer = TRANSFER_PATTERN.matcher(normalizedSegment).find()
                    || looksLikeBareWalletTransfer(normalizedSegment, segment, wallets);
            TransferWallets segmentTransferWallets = segmentTransfer
                    ? matchTransferWallets(normalizedSegment, segment, List.of(), wallets)
                    : new TransferWallets(sourceWallet, destinationWallet, null, null);
            TransactionType segmentType = inferType(normalizedSegment, segmentTransfer, segmentCategory);
            if (segmentType == null) {
                segmentType = fallbackType == TransactionType.INCOME || fallbackType == TransactionType.EXPENSE
                        ? fallbackType
                        : TransactionType.EXPENSE;
            }
            Category category = segmentType == TransactionType.INCOME || segmentCategory == null || segmentCategory.ambiguous() || !categoryMatchesType(segmentCategory.category(), segmentType)
                    ? null
                    : segmentCategory.category();
            String description = candidateDescription(segment, amountCandidate);
            Wallet segmentWallet = matchWallet(normalizedSegment, segment, wallets)
                    .filter(match -> !match.ambiguous())
                    .map(WalletMatch::wallet)
                    .orElse(null);
            List<String> missingFields = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            if (category == null && segmentType == TransactionType.INCOME) {
                missingFields.add("incomeSourceId");
            } else if (category == null && segmentType == TransactionType.EXPENSE) {
                missingFields.add("categoryId");
                warnings.add("UNKNOWN_CATEGORY");
            }
            if (segmentType == TransactionType.INCOME || segmentType == TransactionType.EXPENSE) {
                if (segmentWallet == null && segmentType == TransactionType.EXPENSE) {
                    missingFields.add("walletId");
                }
            } else if (segmentType == TransactionType.TRANSFER) {
                if (segmentTransferWallets.source() == null) {
                    missingFields.add("sourceWalletId");
                }
                if (segmentTransferWallets.destination() == null) {
                    missingFields.add("destinationWalletId");
                }
            }
            if (transactionDate == null) {
                missingFields.add("DATE");
            }
            boolean ready = missingFields.isEmpty();
            boolean affectsWalletBalance = segmentType != TransactionType.INCOME || segmentWallet != null;
            candidates.add(QuickEntryPreviewResponse.Candidate.builder()
                    .candidateId(candidateId(display, i, segment, segmentAmount))
                    .clientCandidateId(candidateId(display, i, segment, segmentAmount))
                    .intentType(intentType(segmentType))
                    .candidateStatus(ready ? VoiceCandidateStatus.READY : VoiceCandidateStatus.NEEDS_REVIEW)
                    .ledgerEffect(ledgerEffect(segmentType, segmentWallet, segmentTransferWallets.source(), segmentTransferWallets.destination()))
                    .originalText(segment)
                    .description(description)
                    .amount(segmentAmount)
                    .type(segmentType)
                    .status(status)
                    .walletId(segmentWallet == null ? null : segmentWallet.getId())
                    .walletName(segmentWallet == null ? null : segmentWallet.getName())
                    .categoryId(category == null ? null : category.getId())
                    .categoryName(category == null ? null : category.getName())
                    .sourceWalletId(segmentTransferWallets.source() == null ? null : segmentTransferWallets.source().getId())
                    .sourceWalletName(segmentTransferWallets.source() == null ? null : segmentTransferWallets.source().getName())
                    .destinationWalletId(segmentTransferWallets.destination() == null ? null : segmentTransferWallets.destination().getId())
                    .destinationWalletName(segmentTransferWallets.destination() == null ? null : segmentTransferWallets.destination().getName())
                    .transactionDate(transactionDate)
                    .transactionTime(transactionTime)
                    .spendingScope(defaultExpenseScope(segmentType, category))
                    .confidence(ready ? 0.95 : 0.65)
                    .readyToConfirm(ready)
                    .commitSupported(segmentType != null)
                    .affectsWalletBalance(affectsWalletBalance)
                    .validationStatus(ready ? "READY" : "NEEDS_REVIEW")
                    .missingFields(missingFields)
                    .warnings(warnings)
                    .build());
        }
        addTrailingIntentCandidate(display, amountCandidates, transactionDate, transactionTime, candidates);
        return candidates;
    }

    private QuickEntryPreviewResponse.Candidate walletSnapshotCandidate(
            String display,
            String segment,
            int index,
            BigDecimal amount,
            LocalDate transactionDate,
            LocalTime transactionTime,
            WalletMatch walletMatch) {
        String candidateId = candidateId(display, index, segment, amount);
        boolean ambiguous = walletMatch.ambiguous();
        Wallet wallet = ambiguous ? null : walletMatch.wallet();
        List<String> missing = ambiguous ? List.of("walletId") : List.of();
        List<String> warnings = ambiguous
                ? List.of("WALLET_MATCH_AMBIGUOUS")
                : List.of("WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED");
        return QuickEntryPreviewResponse.Candidate.builder()
                .candidateId(candidateId)
                .clientCandidateId(candidateId)
                .intentType(VoiceIntentType.WALLET_BALANCE_SNAPSHOT)
                .candidateStatus(VoiceCandidateStatus.NEEDS_REVIEW)
                .ledgerEffect(VoiceLedgerEffect.DOES_NOT_AFFECT_WALLET)
                .originalText(segment)
                .description(candidateDescription(segment, null))
                .amount(amount)
                .walletId(wallet == null ? null : wallet.getId())
                .walletName(wallet == null ? null : wallet.getName())
                .transactionDate(transactionDate)
                .transactionTime(transactionTime)
                .confidence(wallet == null ? 0.65 : 0.85)
                .readyToConfirm(false)
                .commitSupported(false)
                .affectsWalletBalance(false)
                .validationStatus("NEEDS_REVIEW")
                .missingFields(new ArrayList<>(missing))
                .warnings(new ArrayList<>(warnings))
                .unsupportedReason("WALLET_SNAPSHOT_CONFIRM_NOT_SUPPORTED")
                .suggestedManualRoute("/wallets")
                .targetModule("WALLET_SNAPSHOT")
                .suggestedManualActionLabel("Mở ví")
                .build();
    }

    private void addTrailingIntentCandidate(
            String display,
            List<QuickAmountParser.AmountCandidate> amountCandidates,
            LocalDate transactionDate,
            LocalTime transactionTime,
            List<QuickEntryPreviewResponse.Candidate> candidates) {
        QuickAmountParser.AmountCandidate lastAmount = amountCandidates.get(amountCandidates.size() - 1);
        String segment = displayText(display, lastAmount.end(), display.length());
        VoiceIntentType intentType = detectNonTransactionIntent(VietnameseTextNormalizer.comparable(segment));
        if (intentType == null || amountIntent(intentType)) {
            return;
        }
        String candidateId = candidateId(display, amountCandidates.size(), segment, null);
        candidates.add(QuickEntryPreviewResponse.Candidate.builder()
                .candidateId(candidateId)
                .clientCandidateId(candidateId)
                .intentType(intentType)
                .candidateStatus(candidateStatus(intentType))
                .ledgerEffect(ledgerEffect(intentType))
                .originalText(segment)
                .description(VietnameseTextNormalizer.capitalize(segment))
                .transactionDate(transactionDate)
                .transactionTime(transactionTime)
                .confidence(0.65)
                .readyToConfirm(false)
                .commitSupported(false)
                .validationStatus("READ_ONLY")
                .missingFields(new ArrayList<>(requiredReferenceFields(intentType)))
                .warnings(new ArrayList<>(List.of("VOICE_INTENT_NOT_COMMITTABLE")))
                .unsupportedReason(unsupportedReason(intentType))
                .suggestedManualRoute(suggestedManualRoute(intentType))
                .targetModule(targetModule(intentType))
                .suggestedManualActionLabel(suggestedManualActionLabel(intentType))
                .build());
    }

    private void addLeadingMissingAmountIncomeCandidate(
            String display,
            List<QuickAmountParser.AmountCandidate> amountCandidates,
            LocalDate transactionDate,
            LocalTime transactionTime,
            List<QuickEntryPreviewResponse.Candidate> candidates) {
        if (amountCandidates.isEmpty()) {
            return;
        }
        QuickAmountParser.AmountCandidate firstAmount = amountCandidates.get(0);
        int separator = lastSeparator(display, firstAmount.start());
        if (separator < 0) {
            return;
        }
        String segment = displayText(display, 0, separator);
        String normalizedSegment = VietnameseTextNormalizer.comparable(segment);
        if (segment.isBlank() || !INCOME_WORDS.stream().anyMatch(word -> containsWordOrPhrase(normalizedSegment, word))) {
            return;
        }
        String candidateId = candidateId(display, -1, segment, null);
        candidates.add(QuickEntryPreviewResponse.Candidate.builder()
                .candidateId(candidateId)
                .clientCandidateId(candidateId)
                .intentType(VoiceIntentType.TRANSACTION_INCOME)
                .candidateStatus(VoiceCandidateStatus.NEEDS_REVIEW)
                .ledgerEffect(VoiceLedgerEffect.DOES_NOT_AFFECT_WALLET)
                .originalText(segment)
                .description(VietnameseTextNormalizer.capitalize(segment))
                .type(TransactionType.INCOME)
                .transactionDate(transactionDate)
                .transactionTime(transactionTime)
                .confidence(0.45)
                .readyToConfirm(false)
                .commitSupported(false)
                .affectsWalletBalance(false)
                .validationStatus("NEEDS_REVIEW")
                .missingFields(new ArrayList<>(List.of("AMOUNT")))
                .warnings(new ArrayList<>(List.of("INCOME_AMOUNT_MISSING")))
                .build());
    }

    private VoiceIntentType detectPostAmountIntent(String normalizedTail) {
        if (hasAny(normalizedTail, "quy khan cap", "emergency fund", "khan cap")) return VoiceIntentType.EMERGENCY_FUND_CONTRIBUTION;
        if (hasAny(normalizedTail, "gui tiet kiem", "muc tieu tiet kiem", "tiet kiem cho", "vao muc tieu", "de danh")) return VoiceIntentType.SAVINGS_GOAL_CONTRIBUTION;
        if (hasAny(normalizedTail, "quy chim", "quy chi truoc", "sinking fund", "gop quy", "bo vao quy", "vao quy")) return VoiceIntentType.SINKING_FUND_CONTRIBUTION;
        return null;
    }

    private boolean hasTransactionWord(String normalized) {
        return INCOME_WORDS.stream().anyMatch(word -> containsWordOrPhrase(normalized, word))
                || EXPENSE_WORDS.stream().anyMatch(word -> containsWordOrPhrase(normalized, word));
    }

    private String trailingContext(String display, List<QuickAmountParser.AmountCandidate> amountCandidates, int index) {
        QuickAmountParser.AmountCandidate current = amountCandidates.get(index);
        int nextStart = index + 1 < amountCandidates.size() ? amountCandidates.get(index + 1).start() : display.length();
        return VietnameseTextNormalizer.comparable(displayText(display, current.end(), nextStart));
    }

    private SpendingScope defaultExpenseScope(TransactionType type, Category category) {
        if (type != TransactionType.EXPENSE) {
            return null;
        }
        return category == null || category.getDefaultSpendingScope() == null
                ? SpendingScope.PERSONAL
                : category.getDefaultSpendingScope();
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
        int previousEnd = index == 0 ? 0 : amountCandidates.get(index - 1).end();
        int nextStart = index + 1 < amountCandidates.size() ? amountCandidates.get(index + 1).start() : display.length();
        int commaStart = lastSeparator(display, current.start());
        int commaEnd = nextSeparator(display, current.end());
        int start = commaStart >= previousEnd ? commaStart + 1 : previousEnd;
        int end = commaEnd >= 0 && commaEnd <= nextStart ? commaEnd : current.end();
        String segment = displayText(display, start, end);
        return segment.isBlank() ? display : segment;
    }

    private BigDecimal contextualAmount(QuickAmountParser.AmountCandidate amountCandidate, String normalizedSegment) {
        BigDecimal amount = amountCandidate.amount();
        if (amountCandidate.unitlessPlain()
                && amount.compareTo(new BigDecimal("1000")) < 0
                && hasTransactionWord(normalizedSegment)) {
            return amount.multiply(new BigDecimal("1000"));
        }
        return amount;
    }

    private int nextSeparator(String display, int start) {
        for (int i = Math.max(0, start); i < display.length(); i++) {
            if (isSeparator(display, i)) return i;
        }
        return -1;
    }

    private int lastSeparator(String display, int start) {
        for (int i = Math.min(start - 1, display.length() - 1); i >= 0; i--) {
            if (isSeparator(display, i)) return i;
        }
        return -1;
    }

    private boolean isSeparator(String display, int index) {
        char ch = display.charAt(index);
        if (ch == ',' || ch == ';' || ch == '!' || ch == '?') return true;
        return ch == '.'
                && (index == 0 || !Character.isDigit(display.charAt(index - 1)))
                && (index + 1 >= display.length() || !Character.isDigit(display.charAt(index + 1)));
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
        if (type == TransactionType.INCOME) {
            return amount != null && transactionDate != null;
        }
        if (type == TransactionType.EXPENSE) {
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
            return destinationWallet == null ? "Chuyển tiền" : "Chuyển sang " + destinationWallet.getName();
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
