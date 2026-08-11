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
            "phai thanh toan", "tong thanh toan", "tong cong", "can thanh toan", "thanh tien", "tong tien", "phai tra", "grand total", "total");
    private static final List<String> MEDIUM_TOTAL_KEYWORDS = List.of(
            "tien mat", "da lam tron", "khach thanh toan");
    private static final List<String> EXCLUDED_AMOUNT_KEYWORDS = List.of(
            "tien khach dua", "tien thoi lai", "tien tra lai", "diem su dung", "vat", "so ct", "ma tra cuu", "ma hoa don",
            "receipt", "lookup", "order", "tel", "phone", "mst");
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
        Optional<AmountHit> labelled = amounts.stream()
                .filter(hit -> hit.score() > 0)
                .max(Comparator.comparingInt(AmountHit::score).thenComparing(AmountHit::amount));
        if (labelled.isPresent()) return labelled;
        return amounts.stream().max(Comparator.comparing(AmountHit::amount));
    }

    private List<AmountHit> amounts(String text) {
        List<AmountHit> hits = new ArrayList<>();
        int offset = 0;
        for (String rawLine : text.lines().toList()) {
            String line = VietnameseTextNormalizer.comparable(rawLine);
            Matcher matcher = AMOUNT.matcher(line);
            while (matcher.find()) {
                String token = matcher.group(1);
                if (looksLikeIdOrDate(line, matcher.start(), matcher.end(), token)) continue;
                if (containsAny(line, EXCLUDED_AMOUNT_KEYWORDS)) continue;
                BigDecimal amount = parseAmount(token, matcher.group(2));
                if (amount.compareTo(BigDecimal.valueOf(1000)) >= 0 && amount.compareTo(BigDecimal.valueOf(100_000_000)) <= 0) {
                    int score = containsAny(line, TOTAL_KEYWORDS) ? 100 : containsAny(line, MEDIUM_TOTAL_KEYWORDS) ? 50 : 0;
                    hits.add(new AmountHit(amount, offset + matcher.start(), score > 0, score));
                }
            }
            offset += rawLine.length() + 1;
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
        String context = text.substring(left, right);
        String digits = token.replace(".", "").replace(",", "");
        return context.matches(".*\\d{1,2}[/-]\\d{1,2}.*")
                || context.contains("tel") || context.contains("phone") || context.contains("mst")
                || context.contains("ma ") || context.contains("ma:") || context.contains("ma tra cuu") || context.contains("so ct") || context.contains("order")
                || digits.length() > 9;
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

    private boolean containsAny(String text, List<String> needles) {
        return needles.stream().anyMatch(text::contains);
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

    public record ParsedReceipt(String merchantName, LocalDate receiptDate, BigDecimal totalAmount, List<BigDecimal> lineAmounts, boolean totalInferred) {
    }

    private record AmountHit(BigDecimal amount, int start, boolean totalKeyword, int score) {
    }
}
