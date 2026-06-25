package com.moneyflowbackend.quickentry.parser;

import java.text.Normalizer;
import java.util.Locale;

public final class VietnameseTextNormalizer {
    private VietnameseTextNormalizer() {
    }

    public static String compact(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    public static String comparable(String value) {
        String compacted = compact(value).toLowerCase(Locale.ROOT)
                .replace('\u0111', 'd')
                .replace('\u0110', 'd');
        String normalized = Normalizer.normalize(compacted, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    public static String capitalize(String value) {
        String compacted = compact(value);
        if (compacted.isBlank()) {
            return null;
        }
        return compacted.substring(0, 1).toUpperCase(Locale.ROOT) + compacted.substring(1);
    }

    public static boolean containsPhrase(String text, String phrase) {
        String comparableText = " " + comparable(text) + " ";
        String comparablePhrase = comparable(phrase);
        if (comparablePhrase.isBlank()) {
            return false;
        }
        return comparableText.contains(" " + comparablePhrase + " ");
    }
}
