package com.moneyflowbackend.receipt.ocr;

import com.moneyflowbackend.receipt.dto.ReceiptReviewParseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExternalHttpReceiptOcrProvider implements ReceiptOcrProvider {
    private final ReceiptOcrProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public ExternalHttpReceiptOcrProvider(ReceiptOcrProperties properties) {
        this(properties, HttpClient.newHttpClient());
    }

    public ExternalHttpReceiptOcrProvider(ReceiptOcrProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
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
                    "OCR hóa đơn chưa được bật. Hãy dán nội dung hóa đơn để tạo bản nháp.")));
        }
        try {
            String boundary = "MoneyFlowReceiptOcr" + UUID.randomUUID();
            HttpRequest request = HttpRequest.newBuilder(ocrUri())
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, images)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return failed("RECEIPT_OCR_SERVICE_UNAVAILABLE", "Dịch vụ OCR hóa đơn hiện chưa sẵn sàng.");
            }
            return parseResponse(response.body());
        } catch (HttpTimeoutException ex) {
            return new ReceiptOcrResult(type(), ReceiptOcrStatus.TIMEOUT, null, List.of(warning(
                    "RECEIPT_OCR_TIMEOUT",
                    "Đọc hóa đơn mất quá lâu. Hãy thử lại hoặc dán nội dung hóa đơn.")));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed("RECEIPT_OCR_FAILED", "MoneyFlow chưa đọc được ảnh hóa đơn. Hãy thử ảnh rõ hơn hoặc dán nội dung hóa đơn.");
        } catch (Exception ex) {
            return failed("RECEIPT_OCR_FAILED", "MoneyFlow chưa đọc được ảnh hóa đơn. Hãy thử ảnh rõ hơn hoặc dán nội dung hóa đơn.");
        }
    }

    private ReceiptOcrResult parseResponse(String body) throws IOException {
        String json = body == null ? "{}" : body;
        String status = value(json, "status", "FAILED").trim().toUpperCase();
        String text = clean(value(json, "text", null));
        List<ReceiptReviewParseResponse.Warning> warnings = warnings(json);
        List<ReceiptOcrResult.Page> pages = pages(json);
        if ("SUCCEEDED".equals(status)) {
            if (text == null) {
                return new ReceiptOcrResult(type(), ReceiptOcrStatus.TEXT_EMPTY, null, pages, List.of(warning(
                        "RECEIPT_OCR_TEXT_EMPTY",
                        "OCR không tìm thấy nội dung rõ ràng trong ảnh.")));
            }
            return new ReceiptOcrResult(type(), ReceiptOcrStatus.SUCCEEDED, text, pages, warnings);
        }
        return new ReceiptOcrResult(type(), ReceiptOcrStatus.FAILED, null, pages,
                warnings.isEmpty() ? List.of(warning("RECEIPT_OCR_FAILED", "MoneyFlow chưa đọc được ảnh hóa đơn. Hãy thử ảnh rõ hơn hoặc dán nội dung hóa đơn.")) : warnings);
    }

    private URI ocrUri() {
        String base = properties.serviceUrl().replaceAll("/+$", "");
        return URI.create(base.endsWith("/ocr/receipts") ? base : base + "/ocr/receipts");
    }

    private byte[] multipart(String boundary, List<ReceiptImageInput> images) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field(out, boundary, "language", properties.language());
        field(out, boundary, "mode", "receipt");
        for (ReceiptImageInput image : images == null ? List.<ReceiptImageInput>of() : images) {
            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"images\"; filename=\"" + filename(image) + "\"\r\n");
            write(out, "Content-Type: " + contentType(image) + "\r\n\r\n");
            out.write(image.bytes() == null ? new byte[0] : image.bytes());
            write(out, "\r\n");
        }
        write(out, "--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(out, value + "\r\n");
    }

    private List<ReceiptReviewParseResponse.Warning> warnings(String json) {
        List<ReceiptReviewParseResponse.Warning> warnings = new ArrayList<>();
        for (String object : objectsInArray(json, "warnings")) {
            String code = value(object, "code", "RECEIPT_OCR_FAILED");
            warnings.add(warning(code.startsWith("RECEIPT_") ? code : "RECEIPT_OCR_FAILED",
                    clean(value(object, "message", null))));
        }
        return warnings;
    }

    private List<ReceiptOcrResult.Page> pages(String json) {
        List<ReceiptOcrResult.Page> pages = new ArrayList<>();
        for (String object : objectsInArray(json, "pages")) {
            pages.add(new ReceiptOcrResult.Page(
                    integer(object, "index", pages.size()),
                    clean(value(object, "text", null)),
                    decimal(object, "confidence")));
        }
        return pages;
    }

    private String value(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")").matcher(json);
        if (!matcher.find() || "null".equals(matcher.group(1))) return fallback;
        return unescape(matcher.group(2));
    }

    private int integer(String json, String key, int fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private Double decimal(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    private List<String> objectsInArray(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        int start = keyIndex < 0 ? -1 : json.indexOf('[', keyIndex);
        int end = start < 0 ? -1 : json.indexOf(']', start);
        if (end < 0) return List.of();
        List<String> objects = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{[^{}]*}").matcher(json.substring(start + 1, end));
        while (matcher.find()) {
            objects.add(matcher.group());
        }
        return objects;
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

    private ReceiptOcrResult failed(String code, String message) {
        return new ReceiptOcrResult(type(), ReceiptOcrStatus.FAILED, null, List.of(warning(code, message)));
    }

    private ReceiptReviewParseResponse.Warning warning(String code, String message) {
        return ReceiptReviewParseResponse.Warning.builder()
                .code(code)
                .field("ocr")
                .message(message == null || message.isBlank() ? "MoneyFlow chưa đọc được ảnh hóa đơn. Hãy thử ảnh rõ hơn hoặc dán nội dung hóa đơn." : message)
                .build();
    }

    private String filename(ReceiptImageInput image) {
        String name = image == null ? null : image.filename();
        return name == null || name.isBlank() ? "receipt" : name.replace("\"", "");
    }

    private String contentType(ReceiptImageInput image) {
        String type = image == null ? null : image.contentType();
        return type == null || type.isBlank() ? "application/octet-stream" : type;
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }
}
