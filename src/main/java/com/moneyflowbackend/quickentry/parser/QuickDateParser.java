package com.moneyflowbackend.quickentry.parser;

import com.moneyflowbackend.transaction.model.TransactionStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QuickDateParser {
    private static final String FALLBACK_ZONE = "Asia/Ho_Chi_Minh";
    private static final Pattern WORD_DATE = Pattern.compile("\\b(hom nay|nay|hom qua|qua|ngay mai|mai)\\b");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    private static final Pattern SLASH_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b");
    private static final Pattern TIME = Pattern.compile("\\b(?:(\\d{1,2}):(\\d{2})|(\\d{1,2})h(\\d{0,2}))\\b");

    private final Clock clock;

    public QuickDateParser() {
        this(Clock.systemUTC());
    }

    public QuickDateParser(Clock clock) {
        this.clock = clock;
    }

    public DateParseResult parse(String input, String timezone) {
        String normalized = VietnameseTextNormalizer.comparable(input);
        ZoneId zone = zone(timezone);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<TextSpan> spans = new ArrayList<>();
        ParsedDate parsedDate = parseExplicitDate(normalized, today).orElseGet(() -> parseWordDate(normalized, today).orElse(null));
        boolean invalidDate = parsedDate != null && parsedDate.invalid();
        LocalDate date = parsedDate == null ? today : parsedDate.date();
        if (parsedDate != null) {
            spans.add(parsedDate.span());
        }

        ParsedTime parsedTime = parseTime(normalized).orElse(null);
        boolean invalidTime = parsedTime != null && parsedTime.invalid();
        LocalTime time = parsedTime == null ? null : parsedTime.time();
        if (parsedTime != null) {
            spans.add(parsedTime.span());
        }

        boolean future = date != null && date.isAfter(today);
        TransactionStatus status = future ? TransactionStatus.PLANNED : TransactionStatus.POSTED;
        return new DateParseResult(invalidDate ? null : date, invalidTime ? null : time, status, spans, invalidDate, invalidTime, future);
    }

    private Optional<ParsedDate> parseExplicitDate(String normalized, LocalDate today) {
        Matcher iso = ISO_DATE.matcher(normalized);
        if (iso.find()) {
            return Optional.of(date(iso.start(), iso.end(), parseInt(iso.group(1)), parseInt(iso.group(2)), parseInt(iso.group(3))));
        }
        Matcher slash = SLASH_DATE.matcher(normalized);
        if (slash.find()) {
            int year = slash.group(3) == null ? today.getYear() : parseYear(slash.group(3));
            return Optional.of(date(slash.start(), slash.end(), year, parseInt(slash.group(2)), parseInt(slash.group(1))));
        }
        return Optional.empty();
    }

    private Optional<ParsedDate> parseWordDate(String normalized, LocalDate today) {
        Matcher matcher = WORD_DATE.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String word = matcher.group(1);
        LocalDate date = switch (word) {
            case "hom qua", "qua" -> today.minusDays(1);
            case "ngay mai", "mai" -> today.plusDays(1);
            default -> today;
        };
        return Optional.of(new ParsedDate(date, new TextSpan(matcher.start(), matcher.end()), false));
    }

    private Optional<ParsedTime> parseTime(String normalized) {
        Matcher matcher = TIME.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int hour = matcher.group(1) != null ? parseInt(matcher.group(1)) : parseInt(matcher.group(3));
        int minute = matcher.group(2) != null ? parseInt(matcher.group(2)) : parseMinute(matcher.group(4));
        try {
            return Optional.of(new ParsedTime(LocalTime.of(hour, minute), new TextSpan(matcher.start(), matcher.end()), false));
        } catch (DateTimeException ex) {
            return Optional.of(new ParsedTime(null, new TextSpan(matcher.start(), matcher.end()), true));
        }
    }

    private ParsedDate date(int start, int end, int year, int month, int day) {
        try {
            return new ParsedDate(LocalDate.of(year, month, day), new TextSpan(start, end), false);
        } catch (DateTimeException ex) {
            return new ParsedDate(null, new TextSpan(start, end), true);
        }
    }

    private ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? FALLBACK_ZONE : timezone.trim());
        } catch (DateTimeException ex) {
            return ZoneId.of(FALLBACK_ZONE);
        }
    }

    private int parseYear(String value) {
        int year = parseInt(value);
        return year < 100 ? 2000 + year : year;
    }

    private int parseMinute(String value) {
        return value == null || value.isBlank() ? 0 : parseInt(value);
    }

    private int parseInt(String value) {
        return Integer.parseInt(value);
    }

    private record ParsedDate(LocalDate date, TextSpan span, boolean invalid) {
    }

    private record ParsedTime(LocalTime time, TextSpan span, boolean invalid) {
    }

    public record TextSpan(int start, int end) {
    }

    public record DateParseResult(
            LocalDate date,
            LocalTime time,
            TransactionStatus status,
            List<TextSpan> spans,
            boolean invalidDate,
            boolean invalidTime,
            boolean future) {
    }
}
