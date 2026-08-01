package com.moneyflowbackend.receipt.service;

import com.moneyflowbackend.quickentry.parser.VietnameseTextNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReceiptTextParser {
    private static final Pattern DATE = Pattern.compile("\\b(?:ngay\\s*)?(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}-\\d{1,2}-\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile("(?<!\\d)(\\d{1,3}(?:[.,]\\d{3})+|\\d{4,9}|\\d{1,3})\\s*(k|nghin|ngan)?(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final List<String> TOTAL_KEYWORDS = List.of(
            "tong cong", "tong tien", "thanh toan", "phai tra", "total", "grand total");
    private static final List<String> SKIP_MERCHANT = List.of(
            "ngay", "date", "time", "gio", "mst", "tax", "vat", "hoa don", "invoice", "order", "ma", "so hd", "tel", "phone", "total", "tong");

    public ParsedReceipt parse(String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        List<String> lines = text.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        List<AmountHit> amounts = amounts(text);
        AmountHit total = totalAmount(text, amounts).orElse(null);
        boolean totalInferred = total != null && !total.totalKeyword();
        return new ParsedReceipt(
                merchant(lines).orElse(null),
                date(text).orElse(null),
                total == null ? null : total.amount(),
                amounts.stream().map(AmountHit::amount).toList(),
                totalInferred);
    }

    private Optional<AmountHit> totalAmount(String text, List<AmountHit> amounts) {
        if (amounts.isEmpty()) return Optional.empty();
        String normalized = VietnameseTextNormalizer.comparable(text);
        for (String keyword : TOTAL_KEYWORDS) {
            int keywordIndex = normalized.lastIndexOf(keyword);
            if (keywordIndex < 0) continue;
            Optional<AmountHit> nearby = amounts.stream()
                    .filter(hit -> hit.start() >= keywordIndex || Math.abs(hit.start() - keywordIndex) <= 40)
                    .min(Comparator.comparingInt(hit -> Math.abs(hit.start() - keywordIndex)));
            if (nearby.isPresent()) {
                AmountHit hit = nearby.get();
                return Optional.of(new AmountHit(hit.amount(), hit.start(), true));
            }
        }
        return amounts.stream().max(Comparator.comparing(AmountHit::amount));
    }

    private List<AmountHit> amounts(String text) {
        Matcher matcher = AMOUNT.matcher(VietnameseTextNormalizer.comparable(text));
        List<AmountHit> hits = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group(1);
            if (looksLikeIdOrDate(text, matcher.start(), matcher.end(), token)) continue;
            BigDecimal amount = parseAmount(token, matcher.group(2));
            if (amount.compareTo(BigDecimal.valueOf(1000)) >= 0 && amount.compareTo(BigDecimal.valueOf(100_000_000)) <= 0) {
                hits.add(new AmountHit(amount, matcher.start(), false));
            }
        }
        return hits;
    }

    private BigDecimal parseAmount(String token, String unit) {
        String digits = token.replace(".", "").replace(",", "");
        BigDecimal amount = new BigDecimal(digits);
        if (unit != null && !unit.isBlank()) {
            amount = amount.multiply(BigDecimal.valueOf(1000));
        }
        return amount;
    }

    private boolean looksLikeIdOrDate(String text, int start, int end, String token) {
        int left = Math.max(0, start - 12);
        int right = Math.min(text.length(), end + 12);
        String context = VietnameseTextNormalizer.comparable(text.substring(left, right));
        String digits = token.replace(".", "").replace(",", "");
        return context.matches(".*\\d{1,2}[/-]\\d{1,2}.*")
                || context.contains("tel") || context.contains("phone") || context.contains("mst")
                || context.contains("ma ") || context.contains("order")
                || digits.length() > 9;
    }

    private Optional<String> merchant(List<String> lines) {
        return lines.stream()
                .filter(line -> line.length() >= 2)
                .filter(line -> SKIP_MERCHANT.stream().noneMatch(skip -> VietnameseTextNormalizer.comparable(line).contains(skip)))
                .filter(line -> !line.matches(".*\\d{4,}.*"))
                .findFirst();
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

    public record ParsedReceipt(String merchantName, LocalDate receiptDate, BigDecimal totalAmount, List<BigDecimal> lineAmounts, boolean totalInferred) {
    }

    private record AmountHit(BigDecimal amount, int start, boolean totalKeyword) {
    }
}
