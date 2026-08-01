package com.moneyflowbackend.voice.command;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class VoiceCommandClassifier {
    enum Route {
        QUERY,
        REVIEW,
        MIXED,
        NEEDS_CLARIFICATION
    }

    Route route(String text) {
        String value = ascii(text);
        if (value.isBlank()) {
            return Route.NEEDS_CLARIFICATION;
        }
        boolean query = isQuery(value);
        boolean draft = isDraft(value);
        if (query && draft) {
            return Route.MIXED;
        }
        if (query) {
            return Route.QUERY;
        }
        if (draft) {
            return Route.REVIEW;
        }
        return Route.REVIEW;
    }

    private boolean isQuery(String value) {
        return containsAny(value,
                "bao nhieu",
                "tieu bao nhieu",
                "chi bao nhieu",
                "kiem duoc bao nhieu",
                "thu bao nhieu",
                "tien di dau",
                "chi nhieu nhat",
                "ai con no toi",
                "toi dang no ai",
                "con that su tieu duoc bao nhieu",
                "con co the chi",
                "con co the tieu");
    }

    private boolean isDraft(String value) {
        return hasAmount(value) && containsAny(value,
                "an het",
                "toi an",
                " an ",
                "mua",
                "do xang",
                "xang",
                "kiem duoc",
                "doanh thu",
                "chay be",
                "lam duoc",
                "con",
                "so du",
                "hien con",
                "gui tiet kiem",
                "bo vao quy",
                "cho muon",
                "tra no");
    }

    private boolean hasAmount(String value) {
        return value.matches(".*\\b\\d+([.,]\\d+)?\\s*(k|d|dong|vnd|nghin|ngan|trieu|tr|cu)\\b.*")
                || value.replaceAll("\\b202[0-9]\\b", "").matches(".*\\b\\d{4,}\\b.*");
    }

    private String ascii(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replaceAll("[?.,!]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
