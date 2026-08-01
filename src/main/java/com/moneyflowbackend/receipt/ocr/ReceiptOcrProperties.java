package com.moneyflowbackend.receipt.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReceiptOcrProperties {
    private final ReceiptOcrProviderType provider;
    private final int maxImages;
    private final long maxImageBytes;
    private final int timeoutSeconds;
    private final String serviceUrl;

    public ReceiptOcrProperties(
            @Value("${MONEYFLOW_RECEIPT_OCR_PROVIDER:none}") String provider,
            @Value("${MONEYFLOW_RECEIPT_OCR_MAX_IMAGES:5}") int maxImages,
            @Value("${MONEYFLOW_RECEIPT_OCR_MAX_IMAGE_BYTES:5242880}") long maxImageBytes,
            @Value("${MONEYFLOW_RECEIPT_OCR_TIMEOUT_SECONDS:30}") int timeoutSeconds,
            @Value("${MONEYFLOW_RECEIPT_OCR_SERVICE_URL:}") String serviceUrl) {
        this.provider = parseProvider(provider);
        this.maxImages = Math.max(1, maxImages);
        this.maxImageBytes = Math.max(1, maxImageBytes);
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim();
    }

    public ReceiptOcrProviderType provider() {
        return provider;
    }

    public int maxImages() {
        return maxImages;
    }

    public long maxImageBytes() {
        return maxImageBytes;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public boolean externalServiceConfigured() {
        return !serviceUrl.isBlank();
    }

    private ReceiptOcrProviderType parseProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReceiptOcrProviderType.NONE;
        }
        try {
            return ReceiptOcrProviderType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ReceiptOcrProviderType.NONE;
        }
    }
}
