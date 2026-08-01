package com.moneyflowbackend.receipt.ocr;

import java.util.List;

public interface ReceiptOcrProvider {
    ReceiptOcrProviderType type();

    ReceiptOcrResult extractText(List<ReceiptImageInput> images);
}
