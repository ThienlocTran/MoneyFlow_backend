package com.moneyflowbackend.receipt.ocr;

import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExternalHttpReceiptOcrProvider implements ReceiptOcrProvider {
    private final ReceiptOcrProperties properties;

    public ExternalHttpReceiptOcrProvider(ReceiptOcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public ReceiptOcrProviderType type() {
        return ReceiptOcrProviderType.EXTERNAL_HTTP;
    }

    @Override
    public ReceiptOcrResult extractText(List<ReceiptImageInput> images) {
        if (!properties.externalServiceConfigured()) {
            return new ReceiptOcrResult(type(), ReceiptOcrStatus.UNSUPPORTED, null, List.of(warning(
                    "RECEIPT_OCR_NOT_CONFIGURED",
                    "OCR hóa đơn chưa được cấu hình.")));
        }
        return new ReceiptOcrResult(type(), ReceiptOcrStatus.UNSUPPORTED, null, List.of(warning(
                "RECEIPT_OCR_FAILED",
                "OCR hóa đơn bên ngoài chưa được bật trong phiên bản này.")));
    }

    private ReceiptReviewParseResponse.Warning warning(String code, String message) {
        return ReceiptReviewParseResponse.Warning.builder()
                .code(code)
                .field("ocr")
                .message(message)
                .build();
    }
}
