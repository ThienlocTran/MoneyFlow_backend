package com.moneyflowbackend.receipt.ocr;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReceiptOcrService {
    private final ReceiptOcrProperties properties;
    private final Map<ReceiptOcrProviderType, ReceiptOcrProvider> providers;

    public ReceiptOcrService(ReceiptOcrProperties properties, List<ReceiptOcrProvider> providers) {
        this.properties = properties;
        this.providers = providers.stream().collect(Collectors.toMap(ReceiptOcrProvider::type, Function.identity()));
    }

    public ReceiptOcrResult extractText(List<ReceiptImageInput> images) {
        return providers.getOrDefault(properties.provider(), providers.get(ReceiptOcrProviderType.NONE)).extractText(images);
    }

    public ReceiptOcrProperties properties() {
        return properties;
    }
}
