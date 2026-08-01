package com.moneyflowbackend.voice.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class VoiceQueryIntentClassifier {
    public String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[?.,!]", " ")
                .replaceAll("\\s+", " ");
    }

    public boolean isPotentialTransactionCommand(String normalized) {
        String value = ascii(normalized);
        boolean hasQuestion = containsAny(value, "bao nhieu", "di dau", "vao dau", "lon nhat", "no ai");
        boolean hasAmount = value.matches(".*\\b\\d+([.,]\\d+)?\\s*(k|d|dong|vnd|nghin|ngan|trieu|tr|cu)\\b.*")
                || value.replaceAll("\\b202[0-9]\\b", "").matches(".*\\b\\d{4,}\\b.*");
        return hasQuestion && hasAmount;
    }

    public String classifyIntent(String normalized) {
        String value = ascii(normalized);
        if (value.isBlank()) return "NEEDS_CLARIFICATION";
        if (containsAll(value, "hom nay")
                && containsAny(value, "tieu bao nhieu", "xai bao nhieu", "xai het bao nhieu", "chi bao nhieu", "chi het bao nhieu", "tieu het bao nhieu")) {
            return "TODAY_EXPENSE_TOTAL";
        }
        if (containsAny(value, "thuc su tieu", "thuc su chi", "that su tieu", "that su chi", "spendable", "con co the chi", "con co the tieu", "con duoc xai")
                || (containsAll(value, "tieu duoc bao nhieu") && !containsAny(value, "thang nay", "hom nay"))
                || (containsAll(value, "chi duoc bao nhieu") && !containsAny(value, "thang nay", "hom nay"))
                || containsAll(value, "con bao nhieu tien")) {
            return "ACTUALLY_SPENDABLE_SUMMARY";
        }
        if ((containsAny(value, "thang nay", "gan day") || value.contains("chi nhieu nhat") || value.contains("tien di dau"))
                && containsAny(value, "di dau nhieu nhat", "chi nhieu nhat", "tieu nhieu nhat", "ton nhat", "ton tien nhat", "danh muc nao", "khoan nao ton")) {
            return "TOP_EXPENSE_CATEGORIES_THIS_MONTH";
        }
        if ((containsAny(value, "thang nay", "gan day") || value.contains("lon nhat"))
                && containsAny(value, "lon nhat", "to nhat", "nhieu tien nhat", "khung nhat", "cao nhat")
                && containsAny(value, "khoan", "chi", "giao dich", "tieu")) {
            return "LARGEST_EXPENSE_THIS_MONTH";
        }
        if (containsAny(value, "thang nay")
                && containsAny(value, "tieu bao nhieu", "xai bao nhieu", "xai het bao nhieu", "chi bao nhieu", "chi het bao nhieu", "tieu het bao nhieu", "tong chi")) {
            return "MONTH_EXPENSE_TOTAL";
        }
        if (containsAny(value, "thang nay")
                && containsAny(value, "kiem duoc bao nhieu", "thu bao nhieu", "thu nhap", "tong thu", "thu duoc bao nhieu")) {
            return "MONTH_INCOME_TOTAL";
        }
        if (containsAny(value, "ai con no toi", "ai no toi", "nguoi khac con no toi", "con no toi", "no toi bao nhieu", "phai thu", "tong no phai thu", "thu no")) {
            return "RECEIVABLE_SUMMARY";
        }
        if (containsAny(value, "toi dang no ai", "toi no ai", "toi con no", "con phai tra bao nhieu", "tong no phai tra", "no phai tra", "phai tra")) {
            return "PAYABLE_SUMMARY";
        }
        if (containsAny(value, "thang sau", "ngay mai", "nam sau", "du bao", "du tinh", "du tien khong")) {
            return "UNSUPPORTED";
        }
        if (value.contains("tien sao roi") || value.contains("sao")) {
            return "NEEDS_CLARIFICATION";
        }
        if (containsAny(value, "bao nhieu", "ai", "dau", "nao")) {
            return "UNSUPPORTED";
        }
        return "NEEDS_CLARIFICATION";
    }

    private String ascii(String text) {
        return Normalizer.normalize(normalize(text), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd');
    }

    private boolean containsAll(String value, String... keywords) {
        for (String keyword : keywords) {
            if (!value.contains(keyword)) return false;
        }
        return true;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }
}
