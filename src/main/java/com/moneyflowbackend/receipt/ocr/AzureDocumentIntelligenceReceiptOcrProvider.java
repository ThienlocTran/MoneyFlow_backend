package com.moneyflowbackend.receipt.ocr;

import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AzureDocumentIntelligenceReceiptOcrProvider implements ReceiptOcrProvider {
    private static final List<String> SUPPORTED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private final ReceiptOcrProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public AzureDocumentIntelligenceReceiptOcrProvider(ReceiptOcrProperties properties) {
        this(properties, HttpClient.newHttpClient());
    }

    public AzureDocumentIntelligenceReceiptOcrProvider(ReceiptOcrProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public ReceiptOcrProviderType type() {
        return ReceiptOcrProviderType.AZURE_DOCUMENT_INTELLIGENCE;
    }

    @Override
    public ReceiptOcrResult extractText(List<ReceiptImageInput> images) {
        if (!properties.azureConfigured()) {
            return failed(ReceiptOcrStatus.DISABLED, "OCR_NOT_CONFIGURED");
        }
        ReceiptImageInput image = firstImage(images);
        if (image == null) {
            return failed(ReceiptOcrStatus.FAILED, "RECEIPT_IMAGE_REQUIRED");
        }
        if (!supported(image.contentType())) {
            return failed(ReceiptOcrStatus.FAILED, "OCR_UNSUPPORTED_IMAGE_FORMAT");
        }
        if ((image.bytes() == null || image.bytes().length == 0) && blank(image.sourceUrl())) {
            return failed(ReceiptOcrStatus.FAILED, "OCR_IMAGE_NOT_ACCESSIBLE");
        }

        try {
            HttpResponse<String> analyze = httpClient.send(analyzeRequest(image), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ReceiptOcrResult mapped = mapAnalyzeResponse(analyze);
            if (mapped != null) return mapped;

            String operationLocation = operationLocation(analyze.headers());
            if (blank(operationLocation)) {
                return failed(ReceiptOcrStatus.FAILED, "OCR_PROVIDER_FAILED");
            }
            return poll(operationLocation);
        } catch (HttpTimeoutException ex) {
            return failed(ReceiptOcrStatus.TIMEOUT, "OCR_PROVIDER_TIMEOUT");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed(ReceiptOcrStatus.FAILED, "OCR_PROVIDER_FAILED");
        } catch (Exception ex) {
            return failed(ReceiptOcrStatus.FAILED, "OCR_PROVIDER_FAILED");
        }
    }

    private ReceiptOcrResult poll(String operationLocation) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < properties.maxPollAttempts(); attempt++) {
            if (attempt > 0) {
                Thread.sleep(properties.pollIntervalMs());
            }
            HttpResponse<String> response = httpClient.send(pollRequest(operationLocation), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ReceiptOcrResult mapped = mapPollResponse(response);
            if (mapped != null) return mapped;
        }
        return failed(ReceiptOcrStatus.TIMEOUT, "OCR_PROVIDER_TIMEOUT");
    }

    private ReceiptOcrResult mapAnalyzeResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 401 || status == 403) return failed(ReceiptOcrStatus.FAILED, "OCR_PROVIDER_AUTH_FAILED");
        if (status == 408) return failed(ReceiptOcrStatus.TIMEOUT, "OCR_PROVIDER_TIMEOUT");
        if (status == 429) return failed(ReceiptOcrStatus.FAILED, "OCR_PROVIDER_RATE_LIMITED");
        if (status == 400 || status == 415) return failed(ReceiptOcrStatus.FAILED, "OCR_PROVIDER_BAD_REQUEST");
        if (status < 200 || status >= 300) return failed(ReceiptOcrStatus.FAILED, "OCR_PROVIDER_FAILED");
        return null;
    }

    private ReceiptOcrResult mapPollResponse(HttpResponse<String> response) throws IOException {
        ReceiptOcrResult mapped = mapAnalyzeResponse(response);
        if (mapped != null) return mapped;
        String body = response.body() == null ? "{}" : response.body();
        String status = value(body, "status");
        if ("notStarted".equalsIgnoreCase(status) || "running".equalsIgnoreCase(status)) {
            return null;
        }
        if ("failed".equalsIgnoreCase(status)) {
            return failed(ReceiptOcrStatus.FAILED, "OCR_PROVIDER_FAILED");
        }
        if (!"succeeded".equalsIgnoreCase(status)) {
            return null;
        }
        return success(object(body, "analyzeResult"));
    }

    private ReceiptOcrResult success(String analyzeResult) {
        String rawText = clean(value(analyzeResult, "content"));
        if (rawText == null) {
            return failed(ReceiptOcrStatus.TEXT_EMPTY, "OCR_EMPTY_TEXT");
        }
        String fields = object(analyzeResult, "fields");
        String merchant = object(fields, "MerchantName");
        String transactionDate = object(fields, "TransactionDate");
        String totalField = object(fields, "Total");
        String merchantName = clean(fieldText(merchant));
        LocalDate receiptDate = date(transactionDate);
        Money total = money(totalField);
        List<ReceiptReviewParseResponse.Warning> warnings = new ArrayList<>();
        Double confidence = confidence(totalField);
        if (confidence != null && confidence < 0.5) warnings.add(warning("OCR_LOW_CONFIDENCE"));
        if (total.amount() == null) warnings.add(warning("OCR_TOTAL_NOT_FOUND"));
        if (receiptDate == null) warnings.add(warning("OCR_DATE_NOT_FOUND"));
        return new ReceiptOcrResult(type(), ReceiptOcrStatus.SUCCEEDED, rawText, merchantName, receiptDate,
                total.amount(), total.currency(), List.of(new ReceiptOcrResult.Page(0, rawText, confidence)), warnings);
    }

    private HttpRequest analyzeRequest(ReceiptImageInput image) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(analyzeUri())
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Ocp-Apim-Subscription-Key", properties.azureKey())
                .header("Accept", "application/json");
        if (image.bytes() != null && image.bytes().length > 0) {
            return builder.header("Content-Type", contentType(image))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(image.bytes()))
                    .build();
        }
        String body = "{\"urlSource\":\"" + escape(image.sourceUrl()) + "\"}";
        return builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest pollRequest(String operationLocation) {
        return HttpRequest.newBuilder(URI.create(operationLocation))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .header("Ocp-Apim-Subscription-Key", properties.azureKey())
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private URI analyzeUri() {
        String endpoint = properties.azureEndpoint().replaceAll("/+$", "");
        String model = encode(properties.azureModelId());
        String version = encode(properties.azureApiVersion());
        return URI.create(endpoint + "/documentintelligence/documentModels/" + model + ":analyze?api-version=" + version);
    }

    private ReceiptImageInput firstImage(List<ReceiptImageInput> images) {
        return images == null || images.isEmpty() ? null : images.getFirst();
    }

    private String operationLocation(HttpHeaders headers) {
        Optional<String> value = headers.firstValue("Operation-Location");
        return value.orElseGet(() -> headers.firstValue("operation-location").orElse(null));
    }

    private boolean supported(String contentType) {
        return SUPPORTED_CONTENT_TYPES.contains(contentType(contentType));
    }

    private String contentType(ReceiptImageInput image) {
        return contentType(image == null ? null : image.contentType());
    }

    private String contentType(String value) {
        if (value == null || value.isBlank()) return "application/octet-stream";
        return value.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private String fieldText(String field) {
        String value = value(field, "valueString");
        return value == null ? value(field, "content") : value;
    }

    private LocalDate date(String field) {
        String value = value(field, "valueDate");
        if (value == null) value = value(field, "content");
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private Money money(String field) {
        String currency = object(field, "valueCurrency");
        BigDecimal amount = decimal(currency, "amount");
        if (amount == null) amount = decimal(field, "valueNumber");
        String code = value(currency, "currencyCode");
        return new Money(amount, code == null ? null : code.toUpperCase(Locale.ROOT));
    }

    private BigDecimal decimal(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?(-?\\d+(?:\\.\\d+)?)\"?").matcher(json == null ? "" : json);
        if (!matcher.find()) return null;
        try {
            return new BigDecimal(matcher.group(1));
        } catch (Exception ex) {
            return null;
        }
    }

    private Double confidence(String field) {
        Matcher matcher = Pattern.compile("\"confidence\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(field == null ? "" : field);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String object(String json, String key) {
        int keyIndex = (json == null ? "" : json).indexOf("\"" + key + "\"");
        int start = keyIndex < 0 ? -1 : json.indexOf('{', keyIndex);
        if (start < 0) return "";
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return json.substring(start, i + 1);
        }
        return "";
    }

    private String value(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")").matcher(json == null ? "" : json);
        if (!matcher.find() || "null".equals(matcher.group(1))) return null;
        return unescape(matcher.group(2));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            if (next == 'n') out.append('\n');
            else if (next == 'r') out.append('\r');
            else if (next == 't') out.append('\t');
            else if (next == '"' || next == '\\' || next == '/') out.append(next);
            else if (next == 'u' && i + 4 < value.length()) {
                out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                i += 4;
            } else {
                out.append(next);
            }
        }
        return out.toString();
    }

    private ReceiptOcrResult failed(ReceiptOcrStatus status, String code) {
        return new ReceiptOcrResult(type(), status, null, List.of(warning(code)));
    }

    private ReceiptReviewParseResponse.Warning warning(String code) {
        return ReceiptReviewParseResponse.Warning.builder()
                .code(code)
                .field("ocr")
                .message(code)
                .build();
    }

    private record Money(BigDecimal amount, String currency) {
    }
}
