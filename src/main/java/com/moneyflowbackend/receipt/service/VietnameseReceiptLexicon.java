package com.moneyflowbackend.receipt.service;

import com.moneyflowbackend.quickentry.parser.VietnameseTextNormalizer;

import java.util.List;

final class VietnameseReceiptLexicon {
    static final List<String> HIGH_PRIORITY_TOTAL_LABELS = comparable(List.of(
            "Phải thanh toán",
            "Phai thanh toan",
            "Tổng thanh toán",
            "Tong thanh toan",
            "Tổng cộng",
            "Tong cong",
            "Cần thanh toán",
            "Can thanh toan",
            "Thành tiền",
            "Thanh tien",
            "Tổng tiền",
            "Tong tien",
            "Total",
            "Grand total",
            "Amount due"));

    static final List<String> MEDIUM_PRIORITY_PAYMENT_LABELS = comparable(List.of(
            "Tiền mặt",
            "Tien mat",
            "Đã làm tròn",
            "Da lam tron",
            "Thanh toán",
            "Thanh toan",
            "Khách thanh toán"));

    static final List<String> CUSTOMER_TENDERED_LABELS = comparable(List.of(
            "Tiền khách đưa",
            "Tien khach dua",
            "Khách đưa"));

    static final List<String> CHANGE_RETURNED_LABELS = comparable(List.of(
            "Tiền thối lại",
            "Tien thoi lai",
            "Tiền trả lại",
            "Tien tra lai",
            "Trả lại"));

    static final List<String> LOYALTY_POINTS_LABELS = comparable(List.of(
            "Điểm sử dụng",
            "Diem su dung",
            "Điểm tích lũy"));

    static final List<String> RECEIPT_CODE_LABELS = comparable(List.of(
            "Số CT",
            "So CT",
            "Số chứng từ",
            "So chung tu",
            "Mã tra cứu",
            "Ma tra cuu",
            "Mã đơn hàng",
            "Ma don hang",
            "Mã hóa đơn",
            "Ma hoa don",
            "NV",
            "Nhân viên"));

    static final List<String> PHONE_LABELS = comparable(List.of(
            "SĐT",
            "SDT",
            "Điện thoại",
            "Dien thoai",
            "Hotline",
            "Góp ý",
            "Gop y"));

    static final List<String> EXCLUDE_CONTEXT = comparable(List.of(
            "kg",
            "g",
            "VAT",
            "%",
            "số lượng",
            "so luong",
            "SL",
            "mã",
            "code",
            "order",
            "phone",
            "tel",
            "hotline",
            "QR"));

    private VietnameseReceiptLexicon() {
    }

    static boolean hasAny(String text, List<String> labels) {
        return labels.stream().anyMatch(label -> contains(text, label));
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

    private static boolean contains(String text, String label) {
        return !label.isBlank() && (" " + text + " ").contains(" " + label + " ");
    }

    private static List<String> comparable(List<String> values) {
        return values.stream().map(VietnameseReceiptLexicon::comparable).toList();
    }
}
