package com.moneyflowbackend.receipt.ocr;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockReceiptOcrProvider implements ReceiptOcrProvider {
    @Override
    public ReceiptOcrProviderType type() {
        return ReceiptOcrProviderType.MOCK;
    }

    @Override
    public ReceiptOcrResult extractText(List<ReceiptImageInput> images) {
        return new ReceiptOcrResult(type(), ReceiptOcrStatus.EXTRACTED, """
                Mock Mart
                Date 2026-08-01
                Item 25.000
                Total 40.000
                """, List.of());
    }
}
