package com.moneyflowbackend.receipt.service;

import com.moneyflowbackend.quickentry.parser.VietnameseTextNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReceiptTextParser {
    private static final Pattern DATE = Pattern.compile("\\b(?:ngay\\s*)?(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);
    private static final List<String> SKIP_MERCHANT = List.of(
            "ngay", "date", "time", "gio", "mst", "tax", "vat", "hoa don", "invoice", "order", "ma", "so hd", "tel", "phone", "total", "tong");

    private final ReceiptAmountRanker amountRanker;

    public ReceiptTextParser(ReceiptAmountRanker amountRanker) {
        this.amountRanker = amountRanker;
    }

    public ParsedReceipt parse(String rawText) {
        return parse(rawText, null, null);
    }

    public ParsedReceipt parse(String rawText, BigDecimal structuredTotal, Double structuredTotalConfidence) {
        String text = rawText == null ? "" : rawText.strip();
        List<String> lines = text.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        ReceiptAmountRanker.RankedReceiptAmount rankedAmount = amountRanker.rank(text, structuredTotal, structuredTotalConfidence);
        ReceiptAmountCandidate total = rankedAmount.primary();
        boolean totalInferred = total != null && total.source() == ReceiptAmountCandidate.Source.TEXT_UNLABELED;
        return new ParsedReceipt(
                merchant(lines).orElse(null),
                date(text).orElse(null),
                total == null ? null : total.value(),
                rankedAmount.candidates().stream()
                        .filter(candidate -> !candidate.excluded())
                        .map(ReceiptAmountCandidate::value)
                        .toList(),
                totalInferred,
                rankedAmount.candidates(),
                rankedAmount.warnings());
    }

    private Optional<String> merchant(List<String> lines) {
        Optional<String> bachHoaXanh = lines.stream()
                .filter(line -> VietnameseTextNormalizer.comparable(line).contains("bach hoa xanh"))
                .map(line -> "Bách Hóa Xanh")
                .findFirst();
        if (bachHoaXanh.isPresent()) return bachHoaXanh;
        return lines.stream()
                .filter(line -> line.length() >= 2)
                .filter(line -> !looksLikeTimeOrCode(line))
                .filter(line -> SKIP_MERCHANT.stream().noneMatch(skip -> VietnameseTextNormalizer.comparable(line).contains(skip)))
                .filter(line -> !line.matches(".*\\d{4,}.*"))
                .findFirst();
    }

    private boolean looksLikeTimeOrCode(String line) {
        String normalized = VietnameseTextNormalizer.comparable(line);
        return normalized.matches(".*\\b\\d{1,2}:\\d{2}\\b.*")
                || normalized.matches(".*\\b\\d{1,2}[/-]\\d{1,2}([/-]\\d{2,4})?\\b.*")
                || normalized.matches(".*\\b\\d{5,}\\b.*");
    }

    private Optional<LocalDate> date(String text) {
        Matcher matcher = DATE.matcher(VietnameseTextNormalizer.comparable(text));
        while (matcher.find()) {
            String value = matcher.group(1);
            for (DateTimeFormatter formatter : formatters(value)) {
                try {
                    return Optional.of(LocalDate.parse(value, formatter));
                } catch (DateTimeParseException ignored) {
                }
            }
        }
        return Optional.empty();
    }

    private List<DateTimeFormatter> formatters(String value) {
        if (value.matches("\\d{4}-.*")) return List.of(DateTimeFormatter.ofPattern("yyyy-M-d", Locale.ROOT));
        return List.of(
                DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ROOT),
                DateTimeFormatter.ofPattern("d/M/yy", Locale.ROOT),
                DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ROOT),
                DateTimeFormatter.ofPattern("d-M-yy", Locale.ROOT));
    }

    public record ParsedReceipt(
            String merchantName,
            LocalDate receiptDate,
            BigDecimal totalAmount,
            List<BigDecimal> lineAmounts,
            boolean totalInferred,
            List<ReceiptAmountCandidate> amountCandidates,
            List<String> amountWarnings) {
    }
}
