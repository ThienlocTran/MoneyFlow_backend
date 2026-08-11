package com.moneyflowbackend.receipt.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class ReceiptMerchantExtractor {
    private static final Pattern TIME = Pattern.compile(".*\\b\\d{1,2}:\\d{2}\\b.*");
    private static final Pattern DATE = Pattern.compile(".*\\b(\\d{1,2}[/-]\\d{1,2}([/-]\\d{2,4})?|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})\\b.*");
    private static final Pattern AMOUNT_OR_CODE = Pattern.compile(".*\\b\\d{4,}\\b.*");

    public RankedReceiptMerchant rank(String rawText) {
        return rank(rawText, null, null);
    }

    public RankedReceiptMerchant rank(String rawText, String structuredMerchant, Double structuredConfidence) {
        List<ReceiptMerchantCandidate> candidates = new ArrayList<>();
        if (usable(structuredMerchant) && highConfidence(structuredConfidence)) {
            candidates.add(candidate(normalizedMerchant(structuredMerchant), structuredMerchant,
                    ReceiptMerchantCandidate.Source.AZURE_STRUCTURED_MERCHANT, 120,
                    ReceiptMerchantCandidate.Confidence.HIGH, false, null, "Azure structured MerchantName"));
        }
        String text = rawText == null ? "" : rawText;
        List<String> lines = text.lines().map(String::strip).filter(line -> !line.isBlank()).limit(8).toList();
        for (int i = 0; i < lines.size(); i++) {
            candidates.add(lineCandidate(lines.get(i), i));
        }
        List<ReceiptMerchantCandidate> selectable = candidates.stream()
                .filter(candidate -> !candidate.rejected())
                .filter(candidate -> candidate.score() >= 45)
                .sorted(Comparator.comparingInt(ReceiptMerchantCandidate::score).reversed())
                .toList();
        ReceiptMerchantCandidate primary = selectable.isEmpty() ? null : selectable.getFirst().markSelected();
        List<ReceiptMerchantCandidate> marked = candidates.stream()
                .map(candidate -> primary != null && Objects.equals(candidate.value(), primary.value())
                        && candidate.source() == primary.source() ? primary : candidate)
                .toList();
        return new RankedReceiptMerchant(primary, marked, warnings(primary, candidates));
    }

    private ReceiptMerchantCandidate lineCandidate(String rawLine, int index) {
        String comparable = VietnameseReceiptLexicon.comparable(rawLine);
        if (rejected(comparable, rawLine)) {
            return candidate(null, rawLine, ReceiptMerchantCandidate.Source.REJECTED_CONTEXT, -100,
                    ReceiptMerchantCandidate.Confidence.LOW, true, "REJECTED_CONTEXT", "Rejected non-merchant context");
        }
        String known = knownMerchant(comparable);
        if (known != null) {
            return candidate(known, rawLine, ReceiptMerchantCandidate.Source.TEXT_KNOWN_MERCHANT, 100 - index,
                    ReceiptMerchantCandidate.Confidence.HIGH, false, null, "Known merchant keyword");
        }
        if (index <= 2 && comparable.length() >= 3 && !VietnameseReceiptLexicon.hasAny(comparable, VietnameseReceiptLexicon.MERCHANT_PREFIX_NOISE)) {
            return candidate(rawLine, rawLine, ReceiptMerchantCandidate.Source.TEXT_HEADER, 55 - index,
                    ReceiptMerchantCandidate.Confidence.MEDIUM, false, null, "Top receipt heading line");
        }
        return candidate(rawLine, rawLine, ReceiptMerchantCandidate.Source.TEXT_UNLABELED, 10,
                ReceiptMerchantCandidate.Confidence.LOW, false, null, "Unlabeled text");
    }

    private boolean rejected(String comparable, String rawLine) {
        return comparable.isBlank()
                || TIME.matcher(rawLine).matches()
                || DATE.matcher(rawLine).matches()
                || AMOUNT_OR_CODE.matcher(rawLine).matches()
                || VietnameseReceiptLexicon.hasAny(comparable, VietnameseReceiptLexicon.REJECT_MERCHANT_CONTEXT);
    }

    private String knownMerchant(String comparable) {
        if (comparable.contains("bach hoa xanh")) return "Bách Hóa Xanh";
        if (comparable.contains("co opmart") || comparable.contains("coopmart")) return "Co.opmart";
        if (comparable.contains("winmart") || comparable.contains("vinmart")) return "WinMart";
        if (comparable.contains("big c")) return "Big C";
        if (VietnameseReceiptLexicon.contains(comparable, "go")) return "GO!";
        if (comparable.contains("lotte mart")) return "Lotte Mart";
        if (comparable.contains("circle k")) return "Circle K";
        if (comparable.contains("gs25")) return "GS25";
        if (comparable.contains("ministop")) return "Ministop";
        if (comparable.contains("family mart")) return "Family Mart";
        if (comparable.contains("highlands")) return "Highlands Coffee";
        if (comparable.contains("phuc long")) return "Phúc Long";
        if (comparable.contains("the coffee house")) return "The Coffee House";
        return null;
    }

    private String normalizedMerchant(String value) {
        String known = knownMerchant(VietnameseReceiptLexicon.comparable(value));
        return known == null ? value.strip() : known;
    }

    private ReceiptMerchantCandidate candidate(String value, String rawText, ReceiptMerchantCandidate.Source source,
                                               int score, ReceiptMerchantCandidate.Confidence confidence,
                                               boolean rejected, String rejectedReason, String evidence) {
        return new ReceiptMerchantCandidate(value, rawText, VietnameseReceiptLexicon.comparable(value), source, score,
                confidence, false, rejected, rejectedReason, evidence);
    }

    private boolean usable(String value) {
        return value != null && !value.isBlank() && !rejected(VietnameseReceiptLexicon.comparable(value), value);
    }

    private boolean highConfidence(Double confidence) {
        return confidence != null && confidence >= 0.75;
    }

    private List<String> warnings(ReceiptMerchantCandidate primary, List<ReceiptMerchantCandidate> candidates) {
        List<String> warnings = new ArrayList<>();
        if (primary == null) warnings.add("RECEIPT_MERCHANT_NOT_FOUND");
        else if (primary.confidence() == ReceiptMerchantCandidate.Confidence.LOW) warnings.add("RECEIPT_MERCHANT_LOW_CONFIDENCE");
        if (candidates.stream().anyMatch(ReceiptMerchantCandidate::rejected)) warnings.add("RECEIPT_MERCHANT_REJECTED_CONTEXT");
        return warnings.stream().distinct().toList();
    }

    public record RankedReceiptMerchant(
            ReceiptMerchantCandidate primary,
            List<ReceiptMerchantCandidate> candidates,
            List<String> warnings) {
    }
}
