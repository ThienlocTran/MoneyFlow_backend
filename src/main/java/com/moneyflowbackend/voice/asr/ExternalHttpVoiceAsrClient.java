package com.moneyflowbackend.voice.asr;

import com.moneyflowbackend.voice.session.VoiceSessionAsrStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
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

public class ExternalHttpVoiceAsrClient implements VoiceAsrClient {
    private final VoiceAsrProperties properties;
    private final HttpClient httpClient;

    public ExternalHttpVoiceAsrClient(VoiceAsrProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public VoiceAsrProviderType provider() {
        return VoiceAsrProviderType.EXTERNAL_HTTP;
    }

    @Override
    public VoiceAsrTranscribeResult transcribe(VoiceAsrRequest request) {
        if (!properties.externalServiceConfigured()) {
            return failed(VoiceSessionAsrStatus.NOT_REQUESTED, "ASR_NOT_CONFIGURED", request.language());
        }
        try {
            String boundary = "MoneyFlowAsr" + UUID.randomUUID();
            HttpRequest httpRequest = HttpRequest.newBuilder(asrUri())
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                VoiceAsrTranscribeResult parsed = parseResponse(response.body(), request.language());
                return parsed.warnings().isEmpty()
                        ? failed(VoiceSessionAsrStatus.FAILED, response.statusCode() >= 500 ? "ASR_SERVICE_UNAVAILABLE" : "ASR_TRANSCRIBE_FAILED", request.language())
                        : parsed;
            }
            return parseResponse(response.body(), request.language());
        } catch (HttpTimeoutException ex) {
            return failed(VoiceSessionAsrStatus.TIMEOUT, "ASR_SERVICE_TIMEOUT", request.language());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed(VoiceSessionAsrStatus.FAILED, "ASR_TRANSCRIBE_FAILED", request.language());
        } catch (Exception ex) {
            return failed(VoiceSessionAsrStatus.FAILED, "ASR_SERVICE_UNAVAILABLE", request.language());
        }
    }

    private VoiceAsrTranscribeResult parseResponse(String body, String fallbackLanguage) {
        String json = body == null ? "{}" : body;
        String status = value(json, "status", "FAILED").trim().toUpperCase();
        String provider = value(json, "provider", "PHOWHISPER");
        String model = value(json, "model", null);
        String language = value(json, "language", fallbackLanguage);
        Long durationMs = longValue(json, "durationMs");
        String transcript = clean(value(json, "transcript", null));
        String normalizedTranscript = clean(value(json, "normalizedTranscript", null));
        BigDecimal confidence = decimal(json, "confidence");
        List<VoiceAsrWarning> warnings = warnings(json);

        if ("SUCCEEDED".equals(status)) {
            if (transcript == null && normalizedTranscript == null) {
                return failed(providerType(provider), VoiceSessionAsrStatus.NO_SPEECH, model, language, "ASR_EMPTY_TRANSCRIPT");
            }
            return new VoiceAsrTranscribeResult(
                    providerType(provider),
                    VoiceSessionAsrStatus.SUCCEEDED,
                    model,
                    language,
                    durationMs,
                    transcript,
                    normalizedTranscript == null ? transcript : normalizedTranscript,
                    confidence,
                    warnings);
        }

        VoiceSessionAsrStatus mappedStatus = warnings.stream()
                .anyMatch(warning -> "ASR_SERVICE_TIMEOUT".equals(warning.code()))
                ? VoiceSessionAsrStatus.TIMEOUT
                : VoiceSessionAsrStatus.FAILED;
        return new VoiceAsrTranscribeResult(
                providerType(provider),
                mappedStatus,
                model,
                language,
                durationMs,
                null,
                null,
                confidence,
                warnings.isEmpty() ? List.of(warning("ASR_TRANSCRIBE_FAILED")) : warnings);
    }

    private URI asrUri() {
        String base = properties.serviceUrl().replaceAll("/+$", "");
        return URI.create(base.endsWith("/asr/transcribe") ? base : base + "/asr/transcribe");
    }

    private byte[] multipart(String boundary, VoiceAsrRequest request) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field(out, boundary, "language", request.language());
        field(out, boundary, "sessionId", request.sessionId().toString());
        field(out, boundary, "returnSegments", Boolean.toString(request.returnSegments()));
        field(out, boundary, "normalize", Boolean.toString(request.normalize()));
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"audio\"; filename=\"" + filename(request.filename()) + "\"\r\n");
        write(out, "Content-Type: " + contentType(request.contentType()) + "\r\n\r\n");
        out.write(request.audio() == null ? new byte[0] : request.audio());
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(out, (value == null ? "" : value) + "\r\n");
    }

    private List<VoiceAsrWarning> warnings(String json) {
        List<VoiceAsrWarning> warnings = new ArrayList<>();
        for (String object : objectsInArray(json, "warnings")) {
            String code = safeCode(value(object, "code", "ASR_TRANSCRIBE_FAILED"));
            String message = clean(value(object, "message", null));
            warnings.add(new VoiceAsrWarning(code, message == null ? VoiceAsrMessages.message(code) : message));
        }
        return warnings;
    }

    private VoiceAsrProviderType providerType(String raw) {
        if ("MOCK".equalsIgnoreCase(raw)) return VoiceAsrProviderType.MOCK;
        return VoiceAsrProviderType.EXTERNAL_HTTP;
    }

    private VoiceAsrTranscribeResult failed(VoiceSessionAsrStatus status, String code, String language) {
        return failed(provider(), status, null, language, code);
    }

    private VoiceAsrTranscribeResult failed(VoiceAsrProviderType provider, VoiceSessionAsrStatus status, String model, String language, String code) {
        return new VoiceAsrTranscribeResult(provider, status, model, language, null, null, null, null, List.of(warning(code)));
    }

    private VoiceAsrWarning warning(String code) {
        return new VoiceAsrWarning(code, VoiceAsrMessages.message(code));
    }

    private String value(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(null|\"((?:\\\\.|[^\"])*)\")").matcher(json);
        if (!matcher.find() || "null".equals(matcher.group(1))) return fallback;
        return unescape(matcher.group(2));
    }

    private Long longValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private BigDecimal decimal(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
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

    private String safeCode(String code) {
        return code == null || !code.startsWith("ASR_") ? "ASR_TRANSCRIBE_FAILED" : code;
    }

    private String filename(String value) {
        return value == null || value.isBlank() ? "audio" : value.replace("\"", "");
    }

    private String contentType(String value) {
        return value == null || value.isBlank() ? "application/octet-stream" : value;
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
