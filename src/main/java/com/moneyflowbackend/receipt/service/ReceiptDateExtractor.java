package com.moneyflowbackend.receipt.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReceiptDateExtractor {
    private static final Pattern DATE = Pattern.compile("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2})\\b");

    public RankedReceiptDate rank(String rawText, Clock clock) {
        return rank(rawText, null, null, clock);
    }

    public RankedReceiptDate rank(String rawText, LocalDate structuredDate, Double structuredConfidence, Clock clock) {
        List<ReceiptDateCandidate> candidates = new ArrayList<>();
        if (structuredDate != null && highConfidence(structuredConfidence)) {
            candidates.add(candidate(structuredDate, structuredDate.toString(), null, ReceiptDateCandidate.Source.AZURE_STRUCTURED_DATE,
                    120, ReceiptDateCandidate.Confidence.HIGH, false, null, "Azure structured TransactionDate"));
        }
        String text = rawText == null ? "" : rawText;
        for (String rawLine : text.lines().map(String::strip).filter(line -> !line.isBlank()).toList()) {
            String comparable = VietnameseReceiptLexicon.comparable(rawLine);
            Matcher matcher = DATE.matcher(rawLine);
            while (matcher.find()) {
                String token = matcher.group(1);
                LocalDate parsed = parseDate(token);
                if (parsed == null) {
                    candidates.add(candidate(null, token, rawLine, ReceiptDateCandidate.Source.REJECTED_CONTEXT, -100,
                            ReceiptDateCandidate.Confidence.LOW, true, "IMPOSSIBLE_DATE", "Invalid date"));
                    continue;
                }
                if (rejectContext(comparable)) {
                    candidates.add(candidate(parsed, token, rawLine, ReceiptDateCandidate.Source.REJECTED_CONTEXT, -100,
                            ReceiptDateCandidate.Confidence.LOW, true, "REJECTED_CONTEXT", "Rejected code/phone context"));
                    continue;
                }
                int score = headerDate(comparable) ? 80 : 55;
                candidates.add(candidate(parsed, token, rawLine,
                        headerDate(comparable) ? ReceiptDateCandidate.Source.TEXT_HEADER_DATE : ReceiptDateCandidate.Source.TEXT_DATE_PATTERN,
                        score, score >= 80 ? ReceiptDateCandidate.Confidence.HIGH : ReceiptDateCandidate.Confidence.MEDIUM,
                        false, null, "Receipt date pattern"));
            }
        }
        List<ReceiptDateCandidate> selectable = candidates.stream()
                .filter(candidate -> !candidate.rejected())
                .sorted(Comparator.comparingInt(ReceiptDateCandidate::score).reversed())
                .toList();
        ReceiptDateCandidate primary = selectable.isEmpty() ? null : selectable.getFirst().markSelected();
        List<ReceiptDateCandidate> marked = candidates.stream()
                .map(candidate -> primary != null && Objects.equals(candidate.value(), primary.value())
                        && candidate.source() == primary.source() ? primary : candidate)
                .toList();
        return new RankedReceiptDate(primary, marked, warnings(primary, candidates, clock));
    }

    private boolean headerDate(String comparable) {
        return VietnameseReceiptLexicon.hasAny(comparable, VietnameseReceiptLexicon.RECEIPT_CODE_LABELS)
                || comparable.contains("ngay") || comparable.contains("date");
    }

    private boolean rejectContext(String comparable) {
        return VietnameseReceiptLexicon.hasAny(comparable, VietnameseReceiptLexicon.PHONE_LABELS)
                || comparable.contains("hotline") || comparable.contains("gop y");
    }

    private LocalDate parseDate(String token) {
        for (DateTimeFormatter formatter : formatters(token)) {
            try {
                return LocalDate.parse(token, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private List<DateTimeFormatter> formatters(String value) {
        if (value.matches("\\d{4}-.*")) return List.of(DateTimeFormatter.ofPattern("yyyy-M-d", Locale.ROOT));
        if (value.contains(".")) return List.of(DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ROOT), DateTimeFormatter.ofPattern("d.M.yy", Locale.ROOT));
        return List.of(
                DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ROOT),
                DateTimeFormatter.ofPattern("d/M/yy", Locale.ROOT),
                DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ROOT),
                DateTimeFormatter.ofPattern("d-M-yy", Locale.ROOT));
    }

    private ReceiptDateCandidate candidate(LocalDate value, String rawText, String lineText, ReceiptDateCandidate.Source source,
                                           int score, ReceiptDateCandidate.Confidence confidence, boolean rejected,
                                           String rejectedReason, String evidence) {
        return new ReceiptDateCandidate(value, rawText, lineText, source, score, confidence, false, rejected, rejectedReason, evidence);
    }

    private boolean highConfidence(Double confidence) {
        return confidence != null && confidence >= 0.75;
    }

    private List<String> warnings(ReceiptDateCandidate primary, List<ReceiptDateCandidate> candidates, Clock clock) {
        List<String> warnings = new ArrayList<>();
        if (primary == null) warnings.add("RECEIPT_DATE_NOT_FOUND");
        else if (primary.confidence() == ReceiptDateCandidate.Confidence.LOW) warnings.add("RECEIPT_DATE_LOW_CONFIDENCE");
        if (candidates.stream().anyMatch(candidate -> "IMPOSSIBLE_DATE".equals(candidate.rejectedReason()))) warnings.add("RECEIPT_DATE_IMPOSSIBLE");
        if (primary != null && primary.value().isAfter(LocalDate.now(clock).plusDays(30))) warnings.add("RECEIPT_DATE_FUTURE_SUSPICIOUS");
        return warnings.stream().distinct().toList();
    }

    public record RankedReceiptDate(
            ReceiptDateCandidate primary,
            List<ReceiptDateCandidate> candidates,
            List<String> warnings) {
    }
}
