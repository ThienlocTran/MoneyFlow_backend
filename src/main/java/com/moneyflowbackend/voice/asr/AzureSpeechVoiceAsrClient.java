package com.moneyflowbackend.voice.asr;

import com.moneyflowbackend.voice.session.VoiceSessionAsrStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AzureSpeechVoiceAsrClient implements VoiceAsrClient {
    private final VoiceAsrProperties properties;
    private final HttpClient httpClient;

    public AzureSpeechVoiceAsrClient(VoiceAsrProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Override
    public VoiceAsrProviderType provider() {
        return VoiceAsrProviderType.AZURE_SPEECH;
    }

    @Override
    public VoiceAsrTranscribeResult transcribe(VoiceAsrRequest request) {
        if (!properties.azureSpeechConfigured()) {
            return failed(VoiceSessionAsrStatus.NOT_REQUESTED, "ASR_NOT_CONFIGURED", request.language());
        }
        if (!wav(request.contentType())) {
            return failed(VoiceSessionAsrStatus.FAILED, "ASR_UNSUPPORTED_AUDIO_FORMAT", request.language());
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(azureUri(request.language()))
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .header("Ocp-Apim-Subscription-Key", properties.azureSpeechKey())
                    .header("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(request.audio() == null ? new byte[0] : request.audio()))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return mapResponse(response.statusCode(), response.body(), request.language());
        } catch (HttpTimeoutException ex) {
            return failed(VoiceSessionAsrStatus.TIMEOUT, "ASR_SERVICE_TIMEOUT", request.language());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failed(VoiceSessionAsrStatus.FAILED, "ASR_TRANSCRIBE_FAILED", request.language());
        } catch (Exception ex) {
            return failed(VoiceSessionAsrStatus.FAILED, "ASR_SERVICE_UNAVAILABLE", request.language());
        }
    }

    private VoiceAsrTranscribeResult mapResponse(int statusCode, String body, String language) {
        if (statusCode == 401 || statusCode == 403) {
            return failed(VoiceSessionAsrStatus.FAILED, "ASR_AUTH_FAILED", language);
        }
        if (statusCode == 429) {
            return failed(VoiceSessionAsrStatus.FAILED, "ASR_RATE_LIMITED", language);
        }
        if (statusCode == 400 || statusCode == 415) {
            return failed(VoiceSessionAsrStatus.FAILED, "ASR_UNSUPPORTED_AUDIO_FORMAT", language);
        }
        if (statusCode < 200 || statusCode >= 300) {
            return failed(VoiceSessionAsrStatus.FAILED, statusCode >= 500 ? "ASR_SERVICE_UNAVAILABLE" : "ASR_TRANSCRIBE_FAILED", language);
        }

        String recognitionStatus = value(body, "RecognitionStatus");
        if ("Success".equalsIgnoreCase(recognitionStatus)) {
            String transcript = clean(value(body, "DisplayText"));
            if (transcript == null) {
                return failed(VoiceSessionAsrStatus.NO_SPEECH, "ASR_EMPTY_TRANSCRIPT", language);
            }
            return new VoiceAsrTranscribeResult(
                    provider(),
                    VoiceSessionAsrStatus.SUCCEEDED,
                    "azure-speech",
                    language,
                    null,
                    transcript,
                    transcript,
                    confidence(body),
                    List.of());
        }
        if ("NoMatch".equalsIgnoreCase(recognitionStatus) || "InitialSilenceTimeout".equalsIgnoreCase(recognitionStatus)) {
            return failed(VoiceSessionAsrStatus.NO_SPEECH, "ASR_NO_SPEECH_DETECTED", language);
        }
        return failed(VoiceSessionAsrStatus.FAILED, "ASR_TRANSCRIBE_FAILED", language);
    }

    private URI azureUri(String language) {
        String region = properties.azureSpeechRegion().toLowerCase(Locale.ROOT);
        String lang = URLEncoder.encode(language == null || language.isBlank() ? properties.language() : language, StandardCharsets.UTF_8);
        return URI.create("https://" + region + ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=" + lang + "&format=detailed");
    }

    private BigDecimal confidence(String body) {
        Matcher matcher = Pattern.compile("\"Confidence\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(body == null ? "" : body);
        return matcher.find() ? new BigDecimal(matcher.group(1)) : null;
    }

    private String value(String body, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(body == null ? "" : body);
        return matcher.find() ? unescape(matcher.group(1)) : null;
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

    private VoiceAsrTranscribeResult failed(VoiceSessionAsrStatus status, String code, String language) {
        return new VoiceAsrTranscribeResult(provider(), status, "azure-speech", language, null, null, null, null,
                List.of(new VoiceAsrWarning(code, VoiceAsrMessages.message(code))));
    }

    private boolean wav(String contentType) {
        if (contentType == null) return false;
        String type = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return "audio/wav".equals(type) || "audio/x-wav".equals(type) || "audio/wave".equals(type);
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isBlank() ? null : trimmed;
    }
}
