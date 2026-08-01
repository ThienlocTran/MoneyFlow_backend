package com.moneyflowbackend.receipt.ocr;

import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NoopReceiptOcrProvider implements ReceiptOcrProvider {
    @Override
    public ReceiptOcrProviderType type() {
        return ReceiptOcrProviderType.NONE;
    }

    @Override
    public ReceiptOcrResult extractText(List<ReceiptImageInput> images) {
        return new ReceiptOcrResult(type(), ReceiptOcrStatus.DISABLED, null, List.of(warning(
                "RECEIPT_OCR_NOT_CONFIGURED",
                "OCR hóa đơn chưa được bật. Hãy dán nội dung hóa đơn để tạo bản nháp.")));
    }

    private ReceiptReviewParseResponse.Warning warning(String code, String message) {
        return ReceiptReviewParseResponse.Warning.builder()
                .code(code)
                .field("ocr")
                .message(message)
                .build();
    }
}
