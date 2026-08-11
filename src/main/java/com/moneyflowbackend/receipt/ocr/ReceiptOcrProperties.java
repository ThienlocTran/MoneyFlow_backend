package com.moneyflowbackend.receipt.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReceiptOcrProperties {
    private final ReceiptOcrProviderType provider;
    private final int maxImages;
    private final long maxImageBytes;
    private final int timeoutSeconds;
    private final String serviceUrl;
    private final String language;
    private final String azureEndpoint;
    private final String azureKey;
    private final String azureModelId;
    private final String azureApiVersion;
    private final long pollIntervalMs;
    private final int maxPollAttempts;

    @Autowired
    public ReceiptOcrProperties(
            @Value("${MONEYFLOW_RECEIPT_OCR_PROVIDER:none}") String provider,
            @Value("${MONEYFLOW_RECEIPT_OCR_MAX_IMAGES:5}") int maxImages,
            @Value("${MONEYFLOW_RECEIPT_OCR_MAX_IMAGE_BYTES:5242880}") long maxImageBytes,
            @Value("${MONEYFLOW_RECEIPT_OCR_TIMEOUT_SECONDS:45}") int timeoutSeconds,
            @Value("${MONEYFLOW_RECEIPT_OCR_SERVICE_URL:}") String serviceUrl,
            @Value("${MONEYFLOW_RECEIPT_OCR_LANGUAGE:vi}") String language,
            @Value("${AZURE_DOCUMENT_INTELLIGENCE_ENDPOINT:}") String azureEndpoint,
            @Value("${AZURE_DOCUMENT_INTELLIGENCE_KEY:}") String azureKey,
            @Value("${AZURE_DOCUMENT_INTELLIGENCE_MODEL_ID:prebuilt-receipt}") String azureModelId,
            @Value("${AZURE_DOCUMENT_INTELLIGENCE_API_VERSION:2024-11-30}") String azureApiVersion,
            @Value("${MONEYFLOW_RECEIPT_OCR_POLL_INTERVAL_MS:1500}") long pollIntervalMs,
            @Value("${MONEYFLOW_RECEIPT_OCR_MAX_POLL_ATTEMPTS:20}") int maxPollAttempts) {
        this.provider = parseProvider(provider);
        this.maxImages = Math.max(1, maxImages);
        this.maxImageBytes = Math.max(1, maxImageBytes);
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim();
        this.language = language == null || language.isBlank() ? "vi" : language.trim();
        this.azureEndpoint = azureEndpoint == null ? "" : azureEndpoint.trim();
        this.azureKey = azureKey == null ? "" : azureKey.trim();
        this.azureModelId = azureModelId == null || azureModelId.isBlank() ? "prebuilt-receipt" : azureModelId.trim();
        this.azureApiVersion = azureApiVersion == null || azureApiVersion.isBlank() ? "2024-11-30" : azureApiVersion.trim();
        this.pollIntervalMs = Math.max(1, pollIntervalMs);
        this.maxPollAttempts = Math.max(1, maxPollAttempts);
    }

    public ReceiptOcrProperties(String provider, int maxImages, long maxImageBytes, int timeoutSeconds, String serviceUrl) {
        this(provider, maxImages, maxImageBytes, timeoutSeconds, serviceUrl, "vi", "", "", "prebuilt-receipt", "2024-11-30", 1500, 20);
    }

    public ReceiptOcrProperties(String provider, int maxImages, long maxImageBytes, int timeoutSeconds, String serviceUrl, String language) {
        this(provider, maxImages, maxImageBytes, timeoutSeconds, serviceUrl, language, "", "", "prebuilt-receipt", "2024-11-30", 1500, 20);
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

    public String serviceUrl() {
        return serviceUrl;
    }

    public String language() {
        return language;
    }

    public boolean azureConfigured() {
        return !azureEndpoint.isBlank() && !azureKey.isBlank();
    }

    public String azureEndpoint() {
        return azureEndpoint;
    }

    public String azureKey() {
        return azureKey;
    }

    public String azureModelId() {
        return azureModelId;
    }

    public String azureApiVersion() {
        return azureApiVersion;
    }

    public long pollIntervalMs() {
        return pollIntervalMs;
    }

    public int maxPollAttempts() {
        return maxPollAttempts;
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
