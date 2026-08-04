package com.moneyflowbackend.voice.session;

import java.util.Map;

public final class VoiceSessionConfirmMessages {
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("VOICE_DRAFT_NOT_FOUND", "Không tìm thấy khoản nháp."),
            Map.entry("VOICE_SESSION_NOT_FOUND", "Không tìm thấy phiên ghi âm."),
            Map.entry("VOICE_SESSION_ALREADY_HAS_CONFIRMED_DRAFTS", "Phiên này đã có khoản được lưu. Hãy tạo phiên mới nếu muốn phân tích lại."),
            Map.entry("VOICE_DRAFT_ALREADY_CONFIRMED", "Khoản này đã được lưu trước đó."),
            Map.entry("VOICE_DRAFT_CONFIRM_UNSUPPORTED", "Loại lệnh này chưa hỗ trợ lưu tự động. Hãy xử lý thủ công."),
            Map.entry("VOICE_DRAFT_AMOUNT_REQUIRED", "Cần nhập số tiền trước khi lưu."),
            Map.entry("VOICE_DRAFT_WALLET_REQUIRED", "Cần chọn ví trước khi lưu khoản này."),
            Map.entry("VOICE_DRAFT_CATEGORY_REQUIRED", "Cần chọn danh mục trước khi lưu khoản này."),
            Map.entry("VOICE_DRAFT_COUNTERPARTY_REQUIRED", "Cần chọn người liên quan trước khi lưu khoản nợ."),
            Map.entry("VOICE_DRAFT_CONFIRM_FAILED", "Không thể lưu khoản này. Hãy kiểm tra lại thông tin."),
            Map.entry("VOICE_CONFIRM_ELIGIBLE_NONE", "Chưa có khoản nào đủ điều kiện để lưu.")
    );

    private VoiceSessionConfirmMessages() {
    }

    public static String message(String code) {
        return MESSAGES.getOrDefault(code, "Không thể lưu khoản này. Hãy kiểm tra lại thông tin.");
    }
}
