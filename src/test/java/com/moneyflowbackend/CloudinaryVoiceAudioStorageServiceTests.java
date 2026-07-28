package com.moneyflowbackend;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.voice.storage.CloudinaryVoiceAudioStorageService;
import com.moneyflowbackend.voice.storage.VoiceAudioStorageConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.env.MockEnvironment;

import javax.net.ssl.SSLSession;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryVoiceAudioStorageServiceTests {
    @Test
    void uploadUsesEnvironmentVoiceAssetFolderAndReturnedPublicId() {
        CapturingHttpClient client = new CapturingHttpClient();
        CloudinaryVoiceAudioStorageService service = new CloudinaryVoiceAudioStorageService(
                client,
                Clock.fixed(Instant.parse("2026-07-28T01:00:00Z"), ZoneOffset.UTC),
                "demo-cloud",
                "api-key",
                "api-secret",
                "dev/voice");

        var stored = service.upload(
                "07-2026/28-07-2026/11111111-1111-1111-1111-111111111111.webm",
                new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[] {1, 2, 3}));

        assertThat(stored.storageKey()).isEqualTo("returned/dev/voice/07-2026/28-07-2026/audio-id");
        assertThat(client.uri).isEqualTo(URI.create("https://api.cloudinary.com/v1_1/demo-cloud/video/upload"));
        assertThat(client.body).contains("name=\"public_id\"");
        assertThat(client.body).contains("11111111-1111-1111-1111-111111111111");
        assertThat(client.body).doesNotContain("11111111-1111-1111-1111-111111111111.webm");
        assertThat(client.body).contains("name=\"asset_folder\"");
        assertThat(client.body).contains("dev/voice/07-2026/28-07-2026");
        assertThat(client.body).contains("name=\"use_asset_folder_as_public_id_prefix\"");
        assertThat(client.body).contains("true");
        assertThat(client.body).contains("name=\"resource_type\"");
        assertThat(client.body).contains("video");
        assertThat(client.body).contains("name=\"type\"");
        assertThat(client.body).contains("authenticated");
        assertThat(client.body).doesNotContain("api-secret");
    }

    @Test
    void playbackUrlUsesShortLivedAuthenticatedDownloadUrl() {
        CloudinaryVoiceAudioStorageService service = new CloudinaryVoiceAudioStorageService(
                null,
                Clock.fixed(Instant.parse("2026-06-15T01:00:00Z"), ZoneOffset.UTC),
                "demo-cloud",
                "api-key",
                "api-secret",
                "moneyflow/voice");

        var playback = service.playbackUrl("moneyflow/voice/workspaces/ws/voice/record", "audio/webm");

        assertThat(playback.playbackUrl()).startsWith("https://api.cloudinary.com/v1_1/demo-cloud/video/download?");
        assertThat(playback.playbackUrl()).contains("type=authenticated");
        assertThat(playback.playbackUrl()).contains("format=webm");
        assertThat(playback.playbackUrl()).contains("timestamp=1781485200");
        assertThat(playback.playbackUrl()).contains("expires_at=1781485500");
        assertThat(playback.playbackUrl()).contains("signature=");
        assertThat(playback.playbackUrl()).doesNotContain("api-secret");
        assertThat(playback.expiresAt()).isEqualTo(Instant.parse("2026-06-15T01:05:00Z"));
    }

    @Test
    void openMapsMissingCloudinaryObjectToObjectMissing() {
        CloudinaryVoiceAudioStorageService service = serviceWithStatus(404);

        assertBusinessCode(
                () -> service.open("moneyflow/voice/missing", "audio/webm"),
                "AUDIO_OBJECT_MISSING");
    }

    @Test
    void openMapsCloudinaryAuthFailureToStorageUnavailable() {
        CloudinaryVoiceAudioStorageService service = serviceWithStatus(401);

        assertBusinessCode(
                () -> service.open("moneyflow/voice/audio", "audio/webm"),
                "AUDIO_STORAGE_UNAVAILABLE");
    }

    @Test
    void resolvesVoiceFolderByEnvironment() {
        assertThat(VoiceAudioStorageConfig.resolveCloudinaryFolder("", env("local"))).isEqualTo("dev/voice");
        assertThat(VoiceAudioStorageConfig.resolveCloudinaryFolder("", env("production"))).isEqualTo("production/voice");
        assertThat(VoiceAudioStorageConfig.resolveCloudinaryFolder("", env("prod"))).isEqualTo("production/voice");
        assertThat(VoiceAudioStorageConfig.resolveCloudinaryFolder("custom", env("production"))).isEqualTo("custom/voice");
    }

    private MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    private CloudinaryVoiceAudioStorageService serviceWithStatus(int status) {
        return new CloudinaryVoiceAudioStorageService(
                new FixedStatusHttpClient(status),
                Clock.fixed(Instant.parse("2026-06-15T01:00:00Z"), ZoneOffset.UTC),
                "demo-cloud",
                "api-key",
                "api-secret",
                "moneyflow/voice");
    }

    private void assertBusinessCode(ThrowingRunnable runnable, String code) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class FixedStatusHttpClient extends HttpClient {
        private final int status;

        private FixedStatusHttpClient(int status) {
            this.status = status;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return new FixedResponse<>(request, status, null);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

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
    }

    private static class CapturingHttpClient extends FixedStatusHttpClient {
        private URI uri;
        private String body;

        private CapturingHttpClient() {
            super(200);
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            uri = request.uri();
            body = readBody(request);
            return new FixedResponse<>(request, 200, (T) "{\"public_id\":\"returned\\/dev\\/voice\\/07-2026\\/28-07-2026\\/audio-id\"}");
        }

        private String readBody(HttpRequest request) {
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

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            body.append(StandardCharsets.UTF_8.decode(item));
        }

        @Override
        public void onError(Throwable throwable) {
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

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
