package com.moneyflowbackend.suggestion.service;

import com.moneyflowbackend.quickentry.parser.VietnameseTextNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic Vietnamese text matching used by the suggestion engine:
 * lowercase, remove diacritics, drop money tokens and filler words.
 */
@Component
public class SuggestionTextMatcher {
    private static final Pattern MONEY = Pattern.compile(
            "\\b\\d+(?:[.,]\\d+)?\\s*(?:k|nghin|ngan|trieu|tr|tram|ty|dong|d|vnd)?\\b");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> FILLER = Set.of(
            "hom", "nay", "qua", "mai", "toi", "minh", "tui", "da", "dang", "se", "vua", "moi",
            "cho", "vao", "tu", "o", "la", "cai", "het", "con", "va", "voi", "cua", "mot", "ban",
            "roi", "them", "bang", "tien", "so", "du", "khoang", "chi", "thu");

    public String normalize(String value) {
        return VietnameseTextNormalizer.comparable(value == null ? "" : value);
    }

    /**
     * Normalized text with money expressions removed, so "đổ xăng 50" becomes "do xang".
     */
    public String normalizeWithoutAmounts(String value) {
        String normalized = normalize(value);
        String stripped = MONEY.matcher(normalized).replaceAll(" ");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    public List<String> tokens(String value) {
        String normalized = normalizeWithoutAmounts(value);
        List<String> tokens = new ArrayList<>();
        for (String token : NON_WORD.split(normalized)) {
            if (token.isBlank() || token.length() < 2 || FILLER.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    public Set<String> tokenSet(String value) {
        return new LinkedHashSet<>(tokens(value));
    }

    /**
     * Word-boundary contains on the normalized forms, so "xang" matches "do xang 50"
     * but not "xangxe".
     */
    public boolean containsPhrase(String haystack, String needle) {
        String normalizedNeedle = normalizeWithoutAmounts(needle);
        if (normalizedNeedle.isBlank()) {
            return false;
        }
        String padded = " " + normalizeWithoutAmounts(haystack) + " ";
        return padded.contains(" " + normalizedNeedle + " ")
                || padded.startsWith(normalizedNeedle + " ")
                || padded.endsWith(" " + normalizedNeedle);
    }

    /**
     * Shared-token ratio relative to the shorter side, in [0,1].
     */
    public double tokenOverlapRatio(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }
        long shared = left.stream().filter(right::contains).count();
        if (shared == 0) {
            return 0d;
        }
        return (double) shared / Math.min(left.size(), right.size());
    }

    public String initials(String value) {
        StringBuilder sb = new StringBuilder();
        for (String token : tokens(value)) {
            sb.append(token.charAt(0));
        }
        return sb.toString();
    }
}
