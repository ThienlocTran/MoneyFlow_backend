package com.moneyflowbackend.receipt.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReceiptAmountRanker {
    private static final Pattern AMOUNT = Pattern.compile("(?<![\\d/:-])(\\d{1,3}(?:[., ]\\d{3})+|\\d{4,9}|\\d{1,3})\\s*(k|nghin|ngan)?(?![\\d/:-])", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal MIN_AMOUNT = BigDecimal.valueOf(1000);
    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(100_000_000);

    public RankedReceiptAmount rank(String rawText) {
        return rank(rawText, null, null);
    }

    public RankedReceiptAmount rank(String rawText, BigDecimal structuredTotal, Double structuredTotalConfidence) {
        List<ReceiptAmountCandidate> candidates = new ArrayList<>();
        if (structuredTotal != null && structuredTotal.signum() > 0 && highConfidence(structuredTotalConfidence)) {
            candidates.add(new ReceiptAmountCandidate(
                    structuredTotal,
                    structuredTotal.toPlainString(),
                    structuredTotal.toPlainString(),
                    null,
                    "Azure Total",
                    ReceiptAmountCandidate.Source.AZURE_STRUCTURED_TOTAL,
                    120,
                    false,
                    false,
                    null,
                    ReceiptAmountCandidate.Confidence.HIGH,
                    "Azure structured Total"));
        }
        candidates.addAll(textCandidates(rawText));

        List<ReceiptAmountCandidate> selectable = candidates.stream()
                .filter(candidate -> !candidate.excluded())
                .filter(candidate -> candidate.score() >= 40)
                .sorted(Comparator.comparingInt(ReceiptAmountCandidate::score).reversed()
                        .thenComparing(ReceiptAmountCandidate::value))
                .toList();
        ReceiptAmountCandidate primary = selectable.isEmpty() ? null : selectable.getFirst().markSelected();

        List<String> warnings = warnings(primary, candidates, selectable);
        List<ReceiptAmountCandidate> marked = candidates.stream()
                .map(candidate -> primary != null && candidate.value().compareTo(primary.value()) == 0
                        && candidate.source() == primary.source()
                        && Objects.equals(candidate.lineText(), primary.lineText())
                        ? primary
                        : candidate)
                .toList();
        return new RankedReceiptAmount(primary, marked, warnings);
    }

    private List<ReceiptAmountCandidate> textCandidates(String rawText) {
        String text = rawText == null ? "" : rawText;
        List<ReceiptAmountCandidate> hits = new ArrayList<>();
        for (String rawLine : text.lines().map(String::strip).filter(line -> !line.isBlank()).toList()) {
            String line = VietnameseReceiptLexicon.comparable(rawLine);
            Matcher matcher = AMOUNT.matcher(rawLine);
            while (matcher.find()) {
                String token = matcher.group(1);
                BigDecimal amount = parseAmount(token, matcher.group(2));
                if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) continue;
                hits.add(candidate(rawLine, line, token, amount));
            }
        }
        return hits;
    }

    private ReceiptAmountCandidate candidate(String rawLine, String line, String token, BigDecimal amount) {
        String digits = digits(token);
        String excludedReason = excludedReason(line, rawLine, digits);
        if (excludedReason != null) {
            return build(amount, token, rawLine, excludedReason, ReceiptAmountCandidate.Source.EXCLUDED_CONTEXT,
                    -100, true, excludedReason, ReceiptAmountCandidate.Confidence.LOW, "Excluded by receipt context");
        }
        if (VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.HIGH_PRIORITY_TOTAL_LABELS)) {
            String label = VietnameseReceiptLexicon.firstMatch(line, VietnameseReceiptLexicon.HIGH_PRIORITY_TOTAL_LABELS);
            return build(amount, token, rawLine, label, ReceiptAmountCandidate.Source.TEXT_LABEL_HIGH,
                    100, false, null, ReceiptAmountCandidate.Confidence.HIGH, "High priority total label");
        }
        if (VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.MEDIUM_PRIORITY_PAYMENT_LABELS)) {
            String label = VietnameseReceiptLexicon.firstMatch(line, VietnameseReceiptLexicon.MEDIUM_PRIORITY_PAYMENT_LABELS);
            return build(amount, token, rawLine, label, ReceiptAmountCandidate.Source.TEXT_LABEL_MEDIUM,
                    60, false, null, ReceiptAmountCandidate.Confidence.MEDIUM, "Payment label");
        }
        return build(amount, token, rawLine, null, ReceiptAmountCandidate.Source.TEXT_UNLABELED,
                10, false, null, ReceiptAmountCandidate.Confidence.LOW, "Unlabeled amount");
    }

    private String excludedReason(String line, String rawLine, String digits) {
        if (VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.CUSTOMER_TENDERED_LABELS)) return "CUSTOMER_TENDERED";
        if (VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.CHANGE_RETURNED_LABELS)) return "CHANGE_RETURNED";
        if (VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.LOYALTY_POINTS_LABELS)) return "LOYALTY_POINTS";
        if (VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.RECEIPT_CODE_LABELS)) return "RECEIPT_CODE";
        if (VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.PHONE_LABELS)) return "PHONE_OR_HOTLINE";
        if (looksLikeDateOrTime(rawLine) || digits.length() > 9) return "DATE_TIME_OR_ID";
        if (digits.length() >= 7 && !VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.HIGH_PRIORITY_TOTAL_LABELS)
                && !VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.MEDIUM_PRIORITY_PAYMENT_LABELS)) {
            return "RECEIPT_CODE";
        }
        if (VietnameseReceiptLexicon.hasAny(line, VietnameseReceiptLexicon.EXCLUDE_CONTEXT)) return "EXCLUDED_CONTEXT";
        return null;
    }

    private boolean looksLikeDateOrTime(String rawLine) {
        return rawLine.matches(".*\\b\\d{1,2}[/-]\\d{1,2}([/-]\\d{2,4})?\\b.*")
                || rawLine.matches(".*\\b\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}\\b.*")
                || rawLine.matches(".*\\b\\d{1,2}:\\d{2}\\b.*");
    }

    private ReceiptAmountCandidate build(BigDecimal amount, String token, String rawLine, String label,
                                         ReceiptAmountCandidate.Source source, int score, boolean excluded,
                                         String excludedReason, ReceiptAmountCandidate.Confidence confidence,
                                         String evidence) {
        return new ReceiptAmountCandidate(amount, token, amount.toPlainString(), rawLine, label, source, score,
                false, excluded, excludedReason, confidence, evidence);
    }

    private BigDecimal parseAmount(String token, String unit) {
        try {
            BigDecimal amount = new BigDecimal(digits(token));
            return unit == null || unit.isBlank() ? amount : amount.multiply(BigDecimal.valueOf(1000));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String digits(String token) {
        return token == null ? "" : token.replace(".", "").replace(",", "").replace(" ", "");
    }

    private boolean highConfidence(Double confidence) {
        return confidence != null && confidence >= 0.75;
    }

    private List<String> warnings(ReceiptAmountCandidate primary, List<ReceiptAmountCandidate> candidates, List<ReceiptAmountCandidate> selectable) {
        List<String> warnings = new ArrayList<>();
        if (primary == null) {
            warnings.add("RECEIPT_TOTAL_NOT_FOUND");
        } else if (primary.confidence() == ReceiptAmountCandidate.Confidence.LOW) {
            warnings.add("RECEIPT_TOTAL_LOW_CONFIDENCE");
        }
        if (selectable.size() > 1) warnings.add("RECEIPT_AMOUNT_CANDIDATES_AVAILABLE");
        if (selectable.stream().map(ReceiptAmountCandidate::value).distinct().count() > 1
                && selectable.stream().filter(candidate -> candidate.score() == selectable.getFirst().score()).count() > 1) {
            warnings.add("RECEIPT_TOTAL_AMBIGUOUS");
        }
        if (primary != null && primary.source() == ReceiptAmountCandidate.Source.TEXT_LABEL_MEDIUM) {
            warnings.add("RECEIPT_AMOUNT_FROM_ROUNDED_CASH");
        }
        excludedWarnings(candidates, warnings);
        return warnings.stream().distinct().toList();
    }

    private void excludedWarnings(List<ReceiptAmountCandidate> candidates, List<String> warnings) {
        for (ReceiptAmountCandidate candidate : candidates) {
            if (!candidate.excluded()) continue;
            switch (candidate.excludedReason()) {
                case "CUSTOMER_TENDERED" -> warnings.add("RECEIPT_EXCLUDED_CUSTOMER_TENDERED");
                case "CHANGE_RETURNED" -> warnings.add("RECEIPT_EXCLUDED_CHANGE_RETURNED");
                case "LOYALTY_POINTS" -> warnings.add("RECEIPT_EXCLUDED_LOYALTY_POINTS");
                case "RECEIPT_CODE" -> warnings.add("RECEIPT_EXCLUDED_RECEIPT_CODE");
                case "PHONE_OR_HOTLINE" -> warnings.add("RECEIPT_EXCLUDED_PHONE_OR_HOTLINE");
                default -> {
                }
            }
        }
    }

    public record RankedReceiptAmount(
            ReceiptAmountCandidate primary,
            List<ReceiptAmountCandidate> candidates,
            List<String> warnings) {
    }
}
