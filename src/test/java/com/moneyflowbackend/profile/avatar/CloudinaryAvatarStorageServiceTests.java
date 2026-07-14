package com.moneyflowbackend.profile.avatar;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryAvatarStorageServiceTests {
    @Test
    void uploadUsesConfiguredBaseFolderInPublicId() {
        CapturingHttpClient client = new CapturingHttpClient();
        CloudinaryAvatarStorageService service = new CloudinaryAvatarStorageService(
                client,
                Clock.fixed(Instant.ofEpochSecond(123), ZoneOffset.UTC),
                "demo-cloud",
                "api-key",
                "api-secret",
                "moneyflow/dev");

        String url = service.upload(
                "avatars/11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222",
                new MockMultipartFile("file", "avatar.png", "image/png", new byte[] {1, 2, 3}));

        assertThat(url).isEqualTo("https://cdn.example/avatar.png");
        assertThat(client.uri).isEqualTo(URI.create("https://api.cloudinary.com/v1_1/demo-cloud/image/upload"));
        assertThat(client.body).contains("name=\"public_id\"");
        assertThat(client.body).contains("moneyflow/dev/avatars/11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222");
        assertThat(client.body).doesNotContain("api-secret");
    }

    @Test
    void resolvesProfileDefaultsAndOverride() {
        assertThat(AvatarStorageConfig.resolveBaseFolder("", env("local")))
                .isEqualTo("moneyflow/dev");
        assertThat(AvatarStorageConfig.resolveBaseFolder("", env("production")))
                .isEqualTo("moneyflow/prod");
        assertThat(AvatarStorageConfig.resolveBaseFolder("", env("test")))
                .isEqualTo("moneyflow/test");
        assertThat(AvatarStorageConfig.resolveBaseFolder("custom/folder", env("production")))
                .isEqualTo("custom/folder");
    }

    private MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    static class CapturingHttpClient extends HttpClient {
        private URI uri;
        private String body;

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            uri = request.uri();
            body = readBody(request);
            return new FixedResponse<>(request, 200, (T) "{\"secure_url\":\"https:\\/\\/cdn.example\\/avatar.png\"}");
        }

        private String readBody(HttpRequest request) {
            BodyCollector collector = new BodyCollector();
            request.bodyPublisher().orElseThrow().subscribe(collector);
            return collector.body();
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
        @Override public HttpClient.Redirect followRedirects() { return HttpClient.Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public WebSocket.Builder newWebSocketBuilder() { throw new UnsupportedOperationException(); }
    }

    record FixedResponse<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    static class BodyCollector implements Flow.Subscriber<ByteBuffer> {
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
