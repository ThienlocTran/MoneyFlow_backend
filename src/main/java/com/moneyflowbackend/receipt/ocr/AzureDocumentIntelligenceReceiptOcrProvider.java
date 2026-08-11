package com.moneyflowbackend.receipt.ocr;

import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AzureDocumentIntelligenceReceiptOcrProvider implements ReceiptOcrProvider {
    @Override
    public ReceiptOcrProviderType type() {
        return ReceiptOcrProviderType.AZURE_DOCUMENT_INTELLIGENCE;
    }

    @Override
    public ReceiptOcrResult extractText(List<ReceiptImageInput> images) {
        return new ReceiptOcrResult(type(), ReceiptOcrStatus.UNSUPPORTED, null, List.of(warning()));
    }

    private ReceiptReviewParseResponse.Warning warning() {
        return ReceiptReviewParseResponse.Warning.builder()
                .code("OCR_PROVIDER_NOT_IMPLEMENTED")
                .field("ocr")
                .message("Azure Document Intelligence receipt OCR is not implemented yet.")
                .build();
    }
}
