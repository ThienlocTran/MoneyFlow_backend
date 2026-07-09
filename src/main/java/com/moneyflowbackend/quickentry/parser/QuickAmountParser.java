package com.moneyflowbackend.quickentry.parser;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QuickAmountParser {
    private static final Pattern COMPOSITE_MILLION = Pattern.compile("(?<![\\d])(-?\\d+(?:[.,]\\d+)?)\\s*(?:tr|trieu)\\s*(\\d{1,3})(?:\\s*(?:k|nghin|ngan))?\\b");
    private static final Pattern MILLION = Pattern.compile("(?<![\\d])(-?\\d+(?:[.,]\\d+)?)\\s*(?:tr|trieu)\\b");
    private static final Pattern THOUSAND = Pattern.compile("(?<![\\d])(-?\\d+(?:[.,]\\d+)?)\\s*(?:k|nghin|ngan)\\b");
    private static final Pattern GROUPED = Pattern.compile("(?<![\\d/:.-])-?\\d{1,3}(?:[.,\\s]\\d{3})+(?:\\s*(?:d|vnd))?\\b");
    private static final Pattern VND = Pattern.compile("(?<![\\d/:.-])-?\\d+(?:\\s*(?:d|vnd))\\b");
    private static final Pattern BARE = Pattern.compile("(?<![\\d/:.-])-?\\d+(?![\\d/:.-])");
    private static final Pattern NEGATIVE = Pattern.compile("(^|\\s)-\\s*\\d");
    private static final Pattern DATE_OR_TIME = Pattern.compile("\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b|\\b\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b|\\b\\d{1,2}:\\d{2}\\b|\\b\\d{1,2}h\\d{0,2}\\b|\\b\\d{1,2}\\s*(?:gio|h|phut)\\b(?:\\s*(?:sang|trua|chieu|toi))?");

    public AmountParseResult parse(String input, String quickAmountUnit) {
        String normalized = VietnameseTextNormalizer.comparable(input);
        String display = VietnameseTextNormalizer.compact(input);
        boolean negative = NEGATIVE.matcher(normalized).find();
        List<Span> blocked = dateTimeSpans(normalized);
        List<AmountCandidate> candidates = new ArrayList<>();
        MutableFlag zero = new MutableFlag();

        addCompositeMatches(COMPOSITE_MILLION.matcher(normalized), display, blocked, candidates, zero);
        addUnitMatches(MILLION.matcher(normalized), display, blocked, candidates, zero, new BigDecimal("1000000"));
        addUnitMatches(THOUSAND.matcher(normalized), display, blocked, candidates, zero, new BigDecimal("1000"));
        addPlainMatches(GROUPED.matcher(normalized), display, blocked, candidates, zero, false, false);
        addPlainMatches(VND.matcher(normalized), display, blocked, candidates, zero, false, false);
        addPlainMatches(BARE.matcher(normalized), display, blocked, candidates, zero, true, true);

        candidates.sort(Comparator.comparingInt(AmountCandidate::start));
        if (candidates.size() == 1 && candidates.get(0).unitlessPlain()
                && candidates.get(0).amount().compareTo(new BigDecimal("1000")) < 0
                && "THOUSAND".equalsIgnoreCase(quickAmountUnit == null ? "" : quickAmountUnit.trim())) {
            AmountCandidate current = candidates.get(0);
            candidates = List.of(new AmountCandidate(
                    current.amount().multiply(new BigDecimal("1000")),
                    current.start(),
                    current.end(),
                    current.text(),
                    false,
                    true,
                    current.unitlessPlain()));
        }
        return new AmountParseResult(candidates, negative, zero.value, candidates.size() > 1);
    }

    private void addCompositeMatches(
            Matcher matcher,
            String display,
            List<Span> blocked,
            List<AmountCandidate> candidates,
            MutableFlag zero) {
        while (matcher.find()) {
            if (shouldSkip(matcher.toMatchResult(), blocked, candidates)) {
                continue;
            }
            BigDecimal millions = decimalNumber(matcher.group(1)).multiply(new BigDecimal("1000000"));
            BigDecimal thousands = new BigDecimal(matcher.group(2)).multiply(new BigDecimal("1000"));
            addCandidate(millions.add(thousands), matcher.start(), matcher.end(), display, candidates, zero, false, false);
        }
    }

    private void addUnitMatches(
            Matcher matcher,
            String display,
            List<Span> blocked,
            List<AmountCandidate> candidates,
            MutableFlag zero,
            BigDecimal multiplier) {
        while (matcher.find()) {
            if (shouldSkip(matcher.toMatchResult(), blocked, candidates)) {
                continue;
            }
            BigDecimal amount = decimalNumber(matcher.group(1)).multiply(multiplier);
            addCandidate(amount, matcher.start(), matcher.end(), display, candidates, zero, false, false);
        }
    }

    private void addPlainMatches(
            Matcher matcher,
            String display,
            List<Span> blocked,
            List<AmountCandidate> candidates,
            MutableFlag zero,
            boolean unitlessPlain,
            boolean rejectYearLike) {
        while (matcher.find()) {
            if (shouldSkip(matcher.toMatchResult(), blocked, candidates)) {
                continue;
            }
            String token = matcher.group().replaceAll("\\s*(?:d|vnd)$", "");
            if (rejectYearLike && token.length() == 4 && token.startsWith("20")) {
                continue;
            }
            BigDecimal amount = groupedOrInteger(token);
            addCandidate(amount, matcher.start(), matcher.end(), display, candidates, zero, false, unitlessPlain);
        }
    }

    private void addCandidate(
            BigDecimal amount,
            int start,
            int end,
            String display,
            List<AmountCandidate> candidates,
            MutableFlag zero,
            boolean assumedThousand,
            boolean unitlessPlain) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            zero.value = true;
            return;
        }
        candidates.add(new AmountCandidate(normalizeAmount(amount), start, end, displayText(display, start, end), !unitlessPlain, assumedThousand, unitlessPlain));
    }


    private BigDecimal normalizeAmount(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }
    private boolean shouldSkip(MatchResult match, List<Span> blocked, List<AmountCandidate> candidates) {
        if (match.group().startsWith("-")) {
            return true;
        }
        Span span = new Span(match.start(), match.end());
        return blocked.stream().anyMatch(span::overlaps)
                || candidates.stream().anyMatch(candidate -> span.overlaps(new Span(candidate.start(), candidate.end())));
    }

    private List<Span> dateTimeSpans(String normalized) {
        Matcher matcher = DATE_OR_TIME.matcher(normalized);
        List<Span> spans = new ArrayList<>();
        while (matcher.find()) {
            spans.add(new Span(matcher.start(), matcher.end()));
        }
        return spans;
    }

    private BigDecimal decimalNumber(String value) {
        return new BigDecimal(value.replace(',', '.'));
    }

    private BigDecimal groupedOrInteger(String value) {
        String compacted = value.trim();
        if (compacted.matches("\\d{1,3}([.,\\s]\\d{3})+")) {
            return new BigDecimal(compacted.replace(".", "").replace(",", "").replaceAll("\\s+", ""));
        }
        return new BigDecimal(compacted);
    }

    private String displayText(String display, int start, int end) {
        if (start >= 0 && end <= display.length() && start < end) {
            return display.substring(start, end);
        }
        return "";
    }

    private record Span(int start, int end) {
        boolean overlaps(Span other) {
            return start < other.end && other.start < end;
        }
    }

    private static class MutableFlag {
        private boolean value;
    }

    public record AmountCandidate(
            BigDecimal amount,
            int start,
            int end,
            String text,
            boolean explicitUnit,
            boolean assumedThousand,
            boolean unitlessPlain) {
    }

    public record AmountParseResult(
            List<AmountCandidate> candidates,
            boolean negativeAmount,
            boolean zeroAmount,
            boolean ambiguous) {
        public Optional<AmountCandidate> single() {
            return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
        }
    }
}
