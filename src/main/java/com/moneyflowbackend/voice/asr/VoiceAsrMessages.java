package com.moneyflowbackend.voice.asr;

import java.util.Map;

public final class VoiceAsrMessages {
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("ASR_NOT_CONFIGURED", "Nhận diện giọng nói chưa được bật."),
            Map.entry("ASR_AUDIO_REQUIRED", "Cần gửi file âm thanh."),
            Map.entry("ASR_FILE_TOO_LARGE", "File âm thanh quá lớn."),
            Map.entry("ASR_UNSUPPORTED_FORMAT", "Định dạng âm thanh chưa được hỗ trợ."),
            Map.entry("ASR_UNSUPPORTED_AUDIO_FORMAT", "Định dạng ghi âm hiện chưa hỗ trợ nhận diện."),
            Map.entry("ASR_AUDIO_TOO_SHORT", "Đoạn ghi âm quá ngắn. Hãy nói ít nhất một khoản hoặc một câu hỏi."),
            Map.entry("ASR_AUDIO_TOO_LONG", "Đoạn ghi âm quá dài. Hãy ghi âm ngắn hơn."),
            Map.entry("ASR_EMPTY_TRANSCRIPT", "MoneyFlow chưa nghe rõ nội dung."),
            Map.entry("ASR_NO_SPEECH_DETECTED", "MoneyFlow chưa nghe rõ nội dung."),
            Map.entry("ASR_AUTH_FAILED", "Azure Speech chưa xác thực được."),
            Map.entry("ASR_QUOTA_EXCEEDED", "Azure Speech đang hết hạn mức hoặc bị giới hạn tạm thời."),
            Map.entry("ASR_RATE_LIMITED", "Azure Speech đang hết hạn mức hoặc bị giới hạn tạm thời."),
            Map.entry("ASR_SERVICE_UNAVAILABLE", "Dịch vụ nhận diện giọng nói hiện chưa sẵn sàng."),
            Map.entry("ASR_SERVICE_TIMEOUT", "Dịch vụ nhận diện giọng nói phản hồi quá lâu."),
            Map.entry("ASR_TRANSCRIBE_FAILED", "Không thể chuyển giọng nói thành văn bản."),
            Map.entry("ASR_MOCK_TRANSCRIPT", "Mock ASR transcript was returned.")
    );

    private VoiceAsrMessages() {
    }

    public static String message(String code) {
        return MESSAGES.getOrDefault(code, "Không thể chuyển giọng nói thành văn bản.");
    }
}
