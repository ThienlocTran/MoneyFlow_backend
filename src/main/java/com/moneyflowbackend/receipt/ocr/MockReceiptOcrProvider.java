package com.moneyflowbackend.receipt.ocr;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class MockReceiptOcrProvider implements ReceiptOcrProvider {
    @Override
    public ReceiptOcrProviderType type() {
        return ReceiptOcrProviderType.MOCK;
    }

    @Override
    public ReceiptOcrResult extractText(List<ReceiptImageInput> images) {
        String filename = images == null || images.isEmpty() || images.getFirst().filename() == null
                ? ""
                : images.getFirst().filename().toLowerCase(Locale.ROOT);
        if (filename.contains("empty")) {
            return new ReceiptOcrResult(type(), ReceiptOcrStatus.TEXT_EMPTY, null, List.of());
        }
        if (filename.contains("unicode")) {
            return new ReceiptOcrResult(type(), ReceiptOcrStatus.SUCCEEDED, "HO\u0301A DON DEMO\r\nCa phe sua da 25.000\r\nTong cong 25.000", List.of());
        }
        if (filename.contains("coffee") || filename.contains("cafe")) {
            return new ReceiptOcrResult(type(), ReceiptOcrStatus.SUCCEEDED, """
                    QUAN CA PHE DEMO
                    Date 2026-08-01
                    Ca phe sua da 25.000
                    Tong cong 25.000
                    """, List.of());
        }
        if (filename.contains("fuel") || filename.contains("xang")) {
            return new ReceiptOcrResult(type(), ReceiptOcrStatus.SUCCEEDED, """
                    CUA HANG XANG DAU DEMO
                    Date 2026-08-01
                    Do xang 60.000
                    Tong cong 60.000
                    """, List.of());
        }
        return new ReceiptOcrResult(type(), ReceiptOcrStatus.SUCCEEDED, """
                Mock Mart
                Date 2026-08-01
                Item 25.000
                Total 40.000
                """, List.of());
    }
}
