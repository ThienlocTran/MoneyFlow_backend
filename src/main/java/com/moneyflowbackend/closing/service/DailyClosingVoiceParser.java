package com.moneyflowbackend.closing.service;

import com.moneyflowbackend.closing.dto.DailyClosingVoicePreviewResponse;
import com.moneyflowbackend.quickentry.dto.VoiceIntentType;
import com.moneyflowbackend.quickentry.dto.VoiceLedgerEffect;
import com.moneyflowbackend.quickentry.parser.QuickAmountParser;
import com.moneyflowbackend.quickentry.parser.VietnameseTextNormalizer;
import com.moneyflowbackend.wallet.model.Wallet;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DailyClosingVoiceParser {
    private static final Pattern SPLIT = Pattern.compile("\\s*(?:[,;\\n.]|\\s+va\\s+(?=.+\\b(?:con|bo qua|de sau|khong nhap)\\b))\\s*");
    private static final Pattern SKIP = Pattern.compile("\\b(bo qua|de sau|khong nhap|khong can nhap)\\b");
    private static final Pattern AMOUNT_MARKER = Pattern.compile("\\b(?:con|hien con|dang co|so du)\\b");
    private static final Pattern LEADING_WORDS = Pattern.compile("^(?:vi|tai khoan|tk|ngan hang|bank)\\s+");
    private static final Set<String> NOISE = Set.of("vi", "tai", "khoan", "tk", "ngan", "hang", "bank", "wallet", "con", "hien", "dang", "co", "so", "du");

    private final QuickAmountParser amountParser;

    public DailyClosingVoiceParser(QuickAmountParser amountParser) {
        this.amountParser = amountParser;
    }

    public ParsedPreview parse(String transcript, List<Wallet> wallets) {
        List<DailyClosingVoicePreviewResponse.WalletCandidate> candidates = new ArrayList<>();
        List<DailyClosingVoicePreviewResponse.SkippedWallet> skipped = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (String segment : segments(transcript)) {
            String normalized = VietnameseTextNormalizer.comparable(segment);
            if (SKIP.matcher(normalized).find()) {
                skipped.add(skipped(segment, normalized, wallets));
                continue;
            }
            ParsedAmount amount = parseAmount(segment);
            String spokenWallet = spokenWallet(segment, amount);
            WalletMatch walletMatch = matchWallet(spokenWallet, wallets);
            if (walletMatch.matches().isEmpty() && amount.amount() == null) {
                unmatched.add(segment);
                continue;
            }
            candidates.add(candidate(segment, spokenWallet, amount, walletMatch));
        }
        return new ParsedPreview(candidates, skipped, unmatched);
    }

    private List<String> segments(String transcript) {
        String compact = VietnameseTextNormalizer.compact(transcript);
        if (compact.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : SPLIT.split(compact)) {
            String segment = VietnameseTextNormalizer.compact(part);
            if (!segment.isBlank()) {
                result.add(segment);
            }
        }
        return result;
    }

    private DailyClosingVoicePreviewResponse.WalletCandidate candidate(String segment, String spokenWallet, ParsedAmount amount, WalletMatch walletMatch) {
        List<String> missing = new ArrayList<>();
        List<String> ambiguities = new ArrayList<>();
        String status;
        UUID walletId = null;
        String walletName = null;
        List<DailyClosingVoicePreviewResponse.WalletOption> walletOptions = options(walletMatch.matches());
        if (walletMatch.matches().isEmpty()) {
            status = "UNKNOWN_WALLET";
            missing.add("walletId");
        } else if (walletMatch.ambiguous()) {
            status = "AMBIGUOUS_WALLET";
            ambiguities.add("walletId");
        } else if (amount.amount() == null) {
            status = "INVALID_AMOUNT";
            missing.add("actualBalance");
            Wallet wallet = walletMatch.matches().getFirst();
            walletId = wallet.getId();
            walletName = wallet.getName();
        } else {
            Wallet wallet = walletMatch.matches().getFirst();
            walletId = wallet.getId();
            walletName = wallet.getName();
            status = amount.needsReview() ? "NEEDS_REVIEW" : "READY";
        }
        return DailyClosingVoicePreviewResponse.WalletCandidate.builder()
                .candidateId(candidateId(segment, amount.amount()))
                .originalText(segment)
                .spokenWalletName(spokenWallet)
                .walletId(walletId)
                .walletName(walletName)
                .actualBalance(amount.amount())
                .currencyCode("VND")
                .status(status)
                .confidence(confidence(status, amount))
                .intentType(VoiceIntentType.WALLET_BALANCE_SNAPSHOT)
                .ledgerEffect(VoiceLedgerEffect.DOES_NOT_AFFECT_WALLET)
                .targetModule("DAILY_CLOSING")
                .inferenceNotes(amount.notes())
                .missingFields(missing)
                .ambiguities(ambiguities)
                .walletOptions(walletOptions)
                .build();
    }

    private DailyClosingVoicePreviewResponse.SkippedWallet skipped(String segment, String normalized, List<Wallet> wallets) {
        String spoken = VietnameseTextNormalizer.compact(SKIP.matcher(normalized).replaceAll(""));
        spoken = cleanupWalletText(spoken);
        WalletMatch walletMatch = matchWallet(spoken, wallets);
        Wallet wallet = walletMatch.ambiguous() || walletMatch.matches().isEmpty() ? null : walletMatch.matches().getFirst();
        return DailyClosingVoicePreviewResponse.SkippedWallet.builder()
                .originalText(segment)
                .spokenWalletName(spoken)
                .walletId(wallet == null ? null : wallet.getId())
                .walletName(wallet == null ? null : wallet.getName())
                .reason("Người dùng nói bỏ qua")
                .walletOptions(options(walletMatch.matches()))
                .build();
    }

    private ParsedAmount parseAmount(String segment) {
        String normalized = VietnameseTextNormalizer.comparable(segment);
        if (normalized.matches(".*(^|\\s)-\\s*\\d.*")) {
            return new ParsedAmount(null, List.of("Số dư âm cần kiểm tra lại"), true);
        }
        ParsedAmount composite = parseCompositeMillion(normalized);
        if (composite.amount() != null) {
            return composite;
        }
        QuickAmountParser.AmountParseResult parsed = amountParser.parse(segment, "THOUSAND");
        if (parsed.single().isPresent()) {
            QuickAmountParser.AmountCandidate amount = parsed.single().orElseThrow();
            List<String> notes = amount.assumedThousand() || amount.unitlessPlain()
                    ? List.of("Hiểu '" + amount.text() + "' là " + formatVnd(amount.amount()))
                    : List.of();
            return new ParsedAmount(amount.amount(), notes, amount.assumedThousand() || amount.unitlessPlain());
        }
        if (parsed.zeroAmount() || normalized.matches(".*\\b0\\b.*")) {
            return new ParsedAmount(BigDecimal.ZERO, List.of(), false);
        }
        return parseWordAmount(normalized);
    }

    private ParsedAmount parseCompositeMillion(String normalized) {
        Matcher digits = Pattern.compile("\\b(\\d+(?:[.,]\\d+)?)\\s+trieu\\s+(\\d)\\b").matcher(normalized);
        if (digits.find()) {
            BigDecimal whole = new BigDecimal(digits.group(1).replace(',', '.')).multiply(BigDecimal.valueOf(1_000_000L));
            BigDecimal tail = BigDecimal.valueOf(Long.parseLong(digits.group(2)) * 100_000L);
            BigDecimal amount = whole.add(tail);
            return new ParsedAmount(amount, List.of(), false);
        }
        Matcher words = Pattern.compile("\\b(\\w+)\\s+trieu\\s+(\\w)\\b").matcher(normalized);
        if (words.find()) {
            Integer whole = wordNumber(words.group(1));
            Integer tail = wordNumber(words.group(2));
            if (whole != null && tail != null) {
                BigDecimal amount = BigDecimal.valueOf(whole * 1_000_000L + tail * 100_000L);
                return new ParsedAmount(amount, List.of("Hiểu '" + words.group() + "' là " + formatVnd(amount)), true);
            }
        }
        return new ParsedAmount(null, List.of(), false);
    }

    private ParsedAmount parseWordAmount(String normalized) {
        Matcher million = Pattern.compile("\\b(\\w+)\\s+trieu(?:\\s+(\\w+))?\\b").matcher(normalized);
        if (million.find()) {
            Integer whole = wordNumber(million.group(1));
            Integer tenth = wordNumber(million.group(2));
            if (whole != null) {
                BigDecimal amount = BigDecimal.valueOf(whole * 1_000_000L + (tenth == null ? 0 : tenth * 100_000L));
                return new ParsedAmount(amount, List.of("Hiểu '" + million.group() + "' là " + formatVnd(amount)), tenth != null);
            }
        }
        Matcher hundred = Pattern.compile("\\b(\\w+)\\s+tram\\b").matcher(normalized);
        if (hundred.find()) {
            Integer value = wordNumber(hundred.group(1));
            if (value != null) {
                BigDecimal amount = BigDecimal.valueOf(value * 100_000L);
                return new ParsedAmount(amount, List.of("Hiểu '" + hundred.group() + "' là " + formatVnd(amount)), true);
            }
        }
        return new ParsedAmount(null, List.of(), true);
    }

    private Integer wordNumber(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw) {
            case "mot", "mots" -> 1;
            case "hai" -> 2;
            case "ba" -> 3;
            case "bon", "tu" -> 4;
            case "nam", "lam" -> 5;
            case "sau" -> 6;
            case "bay" -> 7;
            case "tam" -> 8;
            case "chin" -> 9;
            default -> null;
        };
    }

    private String spokenWallet(String segment, ParsedAmount amount) {
        String text = segment;
        if (amount.amount() != null) {
            QuickAmountParser.AmountParseResult parsed = amountParser.parse(segment, "THOUSAND");
            if (parsed.single().isPresent()) {
                QuickAmountParser.AmountCandidate candidate = parsed.single().orElseThrow();
                text = segment.substring(0, Math.min(candidate.start(), segment.length()));
            } else {
                Matcher marker = AMOUNT_MARKER.matcher(VietnameseTextNormalizer.comparable(segment));
                if (marker.find()) {
                    text = segment.substring(0, marker.start());
                }
            }
        }
        Matcher marker = AMOUNT_MARKER.matcher(VietnameseTextNormalizer.comparable(text));
        if (marker.find()) {
            text = text.substring(0, marker.start());
        }
        return cleanupWalletText(text);
    }

    private String cleanupWalletText(String text) {
        String value = VietnameseTextNormalizer.comparable(text);
        value = LEADING_WORDS.matcher(value).replaceAll("");
        return VietnameseTextNormalizer.compact(value);
    }

    private WalletMatch matchWallet(String spoken, List<Wallet> wallets) {
        String normalized = VietnameseTextNormalizer.comparable(spoken);
        String stripped = stripNoise(normalized);
        List<Wallet> exact = wallets.stream()
                .filter(wallet -> VietnameseTextNormalizer.comparable(wallet.getName()).equals(normalized))
                .toList();
        if (!exact.isEmpty()) {
            return new WalletMatch(exact);
        }
        List<Wallet> matches = wallets.stream()
                .filter(wallet -> {
                    String walletName = VietnameseTextNormalizer.comparable(wallet.getName());
                    String walletStripped = stripNoise(walletName);
                    return !stripped.isBlank() && (walletStripped.equals(stripped)
                            || walletStripped.contains(stripped)
                            || stripped.contains(walletStripped)
                            || initials(walletName).equals(stripped));
                })
                .sorted(Comparator.comparing(Wallet::getName))
                .toList();
        return new WalletMatch(matches);
    }

    private String stripNoise(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        List<String> words = new ArrayList<>();
        for (String word : normalized.replaceAll("[^a-z0-9 ]", " ").split("\\s+")) {
            if (!word.isBlank() && !NOISE.contains(word)) {
                words.add(word);
            }
        }
        return String.join(" ", words);
    }

    private String initials(String value) {
        StringBuilder sb = new StringBuilder();
        for (String word : stripNoise(value).split("\\s+")) {
            if (!word.isBlank()) {
                sb.append(word.charAt(0));
            }
        }
        return sb.toString();
    }

    private List<DailyClosingVoicePreviewResponse.WalletOption> options(List<Wallet> wallets) {
        return wallets.stream()
                .map(wallet -> DailyClosingVoicePreviewResponse.WalletOption.builder()
                        .walletId(wallet.getId())
                        .walletName(wallet.getName())
                        .build())
                .toList();
    }

    private double confidence(String status, ParsedAmount amount) {
        if ("READY".equals(status)) {
            return 0.92;
        }
        if ("NEEDS_REVIEW".equals(status)) {
            return 0.78;
        }
        if (amount.amount() != null) {
            return 0.55;
        }
        return 0.35;
    }

    private String candidateId(String segment, BigDecimal amount) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((segment + "|" + amount).getBytes(StandardCharsets.UTF_8));
            return "dcv_" + HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            return "dcv_" + UUID.nameUUIDFromBytes(segment.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, 16);
        }
    }

    private String formatVnd(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString() + "đ";
    }

    private record WalletMatch(List<Wallet> matches) {
        boolean ambiguous() {
            return matches.size() > 1;
        }
    }

    private record ParsedAmount(BigDecimal amount, List<String> notes, boolean needsReview) {
    }

    public record ParsedPreview(
            List<DailyClosingVoicePreviewResponse.WalletCandidate> candidates,
            List<DailyClosingVoicePreviewResponse.SkippedWallet> skippedWallets,
            List<String> unmatchedSegments) {
    }
}
