package com.moneyflowbackend;

import com.moneyflowbackend.voice.asr.ExternalHttpVoiceAsrClient;
import com.moneyflowbackend.voice.asr.AzureSpeechVoiceAsrClient;
import com.moneyflowbackend.voice.asr.VoiceAsrProperties;
import com.moneyflowbackend.voice.session.VoiceSessionAsrStatus;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceAsrClientTests {
    @Test
    void externalHttpBuildsMultipartAndMapsSuccess() {
        CapturingHttpClient client = new CapturingHttpClient(200, """
                {"status":"SUCCEEDED","provider":"PHOWHISPER","model":"vinai/PhoWhisper-small","language":"vi","durationMs":4200,"transcript":"Hôm nay tôi ăn sáng hết 35 nghìn","normalizedTranscript":"Hôm nay tôi ăn sáng hết 35 nghìn","confidence":null,"segments":[],"warnings":[]}
                """);
        ExternalHttpVoiceAsrClient provider = new ExternalHttpVoiceAsrClient(properties("https://asr.example"), client);

        var result = provider.transcribe(request());

        assertThat(result.status()).isEqualTo(VoiceSessionAsrStatus.SUCCEEDED);
        assertThat(result.transcript()).isEqualTo("Hôm nay tôi ăn sáng hết 35 nghìn");
        assertThat(result.model()).isEqualTo("vinai/PhoWhisper-small");
        assertThat(client.uri).isEqualTo(URI.create("https://asr.example/asr/transcribe"));
        assertThat(client.version).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.body).contains(
                "name=\"audio\"; filename=\"clip.webm\"",
                "Content-Type: audio/webm",
                "name=\"language\"",
                "vi",
                "name=\"sessionId\"",
                "name=\"returnSegments\"",
                "false",
                "name=\"normalize\"",
                "true");
    }

    @Test
    void externalHttpMapsTimeout() {
        ExternalHttpVoiceAsrClient provider = new ExternalHttpVoiceAsrClient(
                properties("https://asr.example"),
                new ThrowingHttpClient(new java.net.http.HttpTimeoutException("timeout")));

        var result = provider.transcribe(request());

        assertThat(result.status()).isEqualTo(VoiceSessionAsrStatus.TIMEOUT);
        assertThat(result.warnings()).extracting("code").containsExactly("ASR_SERVICE_TIMEOUT");
    }

    @Test
    void externalHttpMissingUrlReturnsNotConfigured() {
        ExternalHttpVoiceAsrClient provider = new ExternalHttpVoiceAsrClient(properties(""), new CapturingHttpClient(200, "{}"));

        var result = provider.transcribe(request());

        assertThat(result.status()).isEqualTo(VoiceSessionAsrStatus.NOT_REQUESTED);
        assertThat(result.warnings()).extracting("code").containsExactly("ASR_NOT_CONFIGURED");
    }

    @Test
    void azureSpeechMapsSuccess() {
        CapturingHttpClient client = new CapturingHttpClient(200, """
                {"RecognitionStatus":"Success","DisplayText":"Ăn sáng hết ba mươi lăm nghìn","NBest":[{"Confidence":0.91}]}
                """);
        AzureSpeechVoiceAsrClient provider = new AzureSpeechVoiceAsrClient(azureProperties("fake-key", "japaneast"), client);

        var result = provider.transcribe(wavRequest());

        assertThat(result.status()).isEqualTo(VoiceSessionAsrStatus.SUCCEEDED);
        assertThat(result.transcript()).isEqualTo("Ăn sáng hết ba mươi lăm nghìn");
        assertThat(client.uri.toString()).contains("https://japaneast.stt.speech.microsoft.com/");
        assertThat(client.uri.toString()).contains("language=vi-VN");
    }

    @Test
    void azureSpeechMissingKeyReturnsNotConfigured() {
        AzureSpeechVoiceAsrClient provider = new AzureSpeechVoiceAsrClient(azureProperties("", "japaneast"), new CapturingHttpClient(200, "{}"));

        var result = provider.transcribe(wavRequest());

        assertThat(result.status()).isEqualTo(VoiceSessionAsrStatus.NOT_REQUESTED);
        assertThat(result.warnings()).extracting("code").containsExactly("ASR_NOT_CONFIGURED");
    }

    @Test
    void azureSpeechMapsAuthAndRateLimit() {
        AzureSpeechVoiceAsrClient authProvider = new AzureSpeechVoiceAsrClient(azureProperties("fake-key", "japaneast"), new CapturingHttpClient(401, "{}"));
        AzureSpeechVoiceAsrClient rateProvider = new AzureSpeechVoiceAsrClient(azureProperties("fake-key", "japaneast"), new CapturingHttpClient(429, "{}"));

        assertThat(authProvider.transcribe(wavRequest()).warnings()).extracting("code").containsExactly("ASR_AUTH_FAILED");
        assertThat(rateProvider.transcribe(wavRequest()).warnings()).extracting("code").containsExactly("ASR_RATE_LIMITED");
    }

    @Test
    void azureSpeechRejectsUnsupportedAudio() {
        AzureSpeechVoiceAsrClient provider = new AzureSpeechVoiceAsrClient(azureProperties("fake-key", "japaneast"), new CapturingHttpClient(200, "{}"));

        var result = provider.transcribe(request());

        assertThat(result.status()).isEqualTo(VoiceSessionAsrStatus.FAILED);
        assertThat(result.warnings()).extracting("code").containsExactly("ASR_UNSUPPORTED_AUDIO_FORMAT");
    }

    private VoiceAsrProperties properties(String url) {
        return new VoiceAsrProperties("external_http", url, "", "", 60, 60, 0.8, 26214400, "vi", false,
                "audio/webm,audio/ogg,audio/wav,audio/mpeg,audio/mp4,audio/x-m4a");
    }

    private VoiceAsrProperties azureProperties(String key, String region) {
        return new VoiceAsrProperties("azure_speech", "", key, region, 30, 15, 0.8, 26214400, "vi-VN", false,
                "audio/webm,audio/ogg,audio/wav,audio/mpeg,audio/mp4,audio/x-m4a");
    }

    private com.moneyflowbackend.voice.asr.VoiceAsrRequest request() {
        return new com.moneyflowbackend.voice.asr.VoiceAsrRequest(
                UUID.randomUUID(), "audio".getBytes(StandardCharsets.UTF_8), "clip.webm", "audio/webm", "vi", false, true);
    }

    private com.moneyflowbackend.voice.asr.VoiceAsrRequest wavRequest() {
        return new com.moneyflowbackend.voice.asr.VoiceAsrRequest(
                UUID.randomUUID(), "audio".getBytes(StandardCharsets.UTF_8), "clip.wav", "audio/wav", "vi-VN", false, true);
    }

    private static class CapturingHttpClient extends BaseHttpClient {
        private final int status;
        private final String responseBody;
        private URI uri;
        private HttpClient.Version version;
        private String body;

        private CapturingHttpClient(int status, String responseBody) {
            this.status = status;
            this.responseBody = responseBody;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            uri = request.uri();
            version = request.version().orElse(null);
            body = readBody(request);
            return new FixedResponse<>(request, status, (T) responseBody);
        }
    }

    private static class ThrowingHttpClient extends BaseHttpClient {
        private final IOException exception;

        private ThrowingHttpClient(IOException exception) {
            this.exception = exception;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            throw exception;
        }
    }

    private abstract static class BaseHttpClient extends HttpClient {
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return null; }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException(); }

        protected String readBody(HttpRequest request) {
            BodyCollector collector = new BodyCollector();
            request.bodyPublisher().orElseThrow().subscribe(collector);
            return collector.body();
        }
    }

    private record FixedResponse<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private static class BodyCollector implements Flow.Subscriber<ByteBuffer> {
        private final CountDownLatch done = new CountDownLatch(1);
        private final StringBuilder body = new StringBuilder();

        @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
        @Override public void onNext(ByteBuffer item) { body.append(StandardCharsets.UTF_8.decode(item)); }
        @Override public void onError(Throwable throwable) { done.countDown(); }
        @Override public void onComplete() { done.countDown(); }

        String body() {
            try {
                done.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return body.toString();
        }
    }
}
