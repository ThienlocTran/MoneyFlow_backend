package com.moneyflowbackend.receipt.service;

import com.moneyflowbackend.quickentry.parser.VietnameseTextNormalizer;

import java.util.List;

final class VietnameseReceiptLexicon {
    static final List<String> HIGH_PRIORITY_TOTAL_LABELS = comparable(List.of(
            "phai thanh toan", "tong thanh toan", "tong cong", "can thanh toan", "thanh tien", "tong tien",
            "total", "grand total", "amount due"));

    static final List<String> MEDIUM_PRIORITY_PAYMENT_LABELS = comparable(List.of(
            "tien mat", "da lam tron", "thanh toan", "khach thanh toan"));

    static final List<String> CUSTOMER_TENDERED_LABELS = comparable(List.of("tien khach dua", "khach dua"));
    static final List<String> CHANGE_RETURNED_LABELS = comparable(List.of("tien thoi lai", "tien tra lai", "tra lai"));
    static final List<String> LOYALTY_POINTS_LABELS = comparable(List.of("diem su dung", "diem tich luy"));
    static final List<String> RECEIPT_CODE_LABELS = comparable(List.of(
            "so ct", "so chung tu", "ma tra cuu", "ma don hang", "ma hoa don", "nv", "nhan vien"));
    static final List<String> PHONE_LABELS = comparable(List.of("sdt", "dien thoai", "hotline", "gop y"));
    static final List<String> EXCLUDE_CONTEXT = comparable(List.of(
            "kg", "g", "vat", "%", "so luong", "sl", "ma", "code", "order", "phone", "tel", "hotline", "qr"));

    static final List<String> KNOWN_MERCHANT_KEYWORDS = comparable(List.of(
            "bach hoa xanh", "co opmart", "coopmart", "winmart", "vinmart", "big c", "go", "lotte mart",
            "circle k", "gs25", "ministop", "family mart", "highlands", "phuc long", "the coffee house"));
    static final List<String> MERCHANT_PREFIX_NOISE = comparable(List.of(
            "phieu thanh toan", "hoa don", "bien lai", "phieu tinh tien", "cua hang", "sieu thi"));
    static final List<String> REJECT_MERCHANT_CONTEXT = comparable(List.of(
            "ma tra cuu", "so ct", "hotline", "gop y", "nhan vien", "nv", "vat", "sl", "kg"));

    static final List<String> CATEGORY_HINTS_GROCERY = comparable(List.of(
            "bach hoa xanh", "winmart", "vinmart", "coopmart", "co opmart", "big c", "go", "lotte mart",
            "sieu thi", "tap hoa", "thuc pham", "rau", "thit", "ca", "sua", "gao", "mi", "trung", "trai cay"));
    static final List<String> CATEGORY_HINTS_DELIVERY = comparable(List.of(
            "shipper", "giao hang", "phi ship", "van chuyen", "delivery", "grab delivery", "be delivery"));
    static final List<String> CATEGORY_NAMES_GROCERY = comparable(List.of(
            "di cho", "an uong", "thuc pham", "sieu thi", "tap hoa", "do an", "gia dinh", "grocery", "groceries", "food"));
    static final List<String> CATEGORY_NAMES_DELIVERY = comparable(List.of(
            "shipper", "giao hang", "van chuyen", "delivery", "phi ship"));

    private VietnameseReceiptLexicon() {
    }

    static boolean hasAny(String text, List<String> labels) {
        return labels.stream().anyMatch(label -> contains(text, label));
    }

    static long countAny(String text, List<String> labels) {
        return labels.stream().filter(label -> contains(text, label)).count();
    }

    static String firstMatch(String text, List<String> labels) {
        return labels.stream().filter(label -> contains(text, label)).findFirst().orElse(null);
    }

    static String comparable(String value) {
        return VietnameseTextNormalizer.comparable(value == null ? "" : value)
                .replaceAll("[^a-z0-9%]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static boolean contains(String text, String label) {
        return !label.isBlank() && (" " + text + " ").contains(" " + label + " ");
    }

    private static List<String> comparable(List<String> values) {
        return values.stream().map(VietnameseReceiptLexicon::comparable).toList();
    }
}
