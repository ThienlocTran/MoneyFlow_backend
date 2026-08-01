package com.moneyflowbackend;

import com.moneyflowbackend.receipt.ocr.ExternalHttpReceiptOcrProvider;
import com.moneyflowbackend.receipt.ocr.ReceiptImageInput;
import com.moneyflowbackend.receipt.ocr.ReceiptOcrProperties;
import com.moneyflowbackend.receipt.ocr.ReceiptOcrStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptOcrExternalHttpProviderTests {
    @Test
    void externalHttpBuildsMultipartInImageOrderAndMapsSuccess() {
        CapturingHttpClient client = new CapturingHttpClient(200, """
                {"status":"SUCCEEDED","text":"Shop\\nTotal 40.000","pages":[{"index":0,"text":"first","confidence":0.9}],"warnings":[]}
                """);
        var provider = provider(client, "https://ocr.example");

        var result = provider.extractText(List.of(
                image(0, "a.jpg", "image/jpeg", "first-image"),
                image(1, "b.png", "image/png", "second-image")));

        assertThat(result.status()).isEqualTo(ReceiptOcrStatus.SUCCEEDED);
        assertThat(result.text()).isEqualTo("Shop\nTotal 40.000");
        assertThat(result.pages()).hasSize(1);
        assertThat(client.uri).isEqualTo(URI.create("https://ocr.example/ocr/receipts"));
        assertThat(client.body).contains("name=\"language\"", "vi", "name=\"mode\"", "receipt");
        assertThat(client.body.indexOf("first-image")).isLessThan(client.body.indexOf("second-image"));
    }

    @Test
    void externalHttpMapsFailedResponse() {
        CapturingHttpClient client = new CapturingHttpClient(200, """
                {"status":"FAILED","warnings":[{"code":"OCR_NO_TEXT_FOUND","message":"No readable text found."}]}
                """);

        var result = provider(client, "https://ocr.example").extractText(List.of(image(0, "a.jpg", "image/jpeg", "x")));

        assertThat(result.status()).isEqualTo(ReceiptOcrStatus.FAILED);
        assertThat(result.warnings()).extracting("code").containsExactly("RECEIPT_OCR_FAILED");
    }

    @Test
    void externalHttpMapsTimeout() {
        ThrowingHttpClient client = new ThrowingHttpClient(new java.net.http.HttpTimeoutException("timeout"));

        var result = provider(client, "https://ocr.example").extractText(List.of(image(0, "a.jpg", "image/jpeg", "x")));

        assertThat(result.status()).isEqualTo(ReceiptOcrStatus.TIMEOUT);
        assertThat(result.warnings()).extracting("code").containsExactly("RECEIPT_OCR_TIMEOUT");
    }

    @Test
    void externalHttpMissingUrlReturnsNotConfigured() {
        var result = provider(new CapturingHttpClient(200, "{}"), "").extractText(List.of(image(0, "a.jpg", "image/jpeg", "x")));

        assertThat(result.status()).isEqualTo(ReceiptOcrStatus.UNSUPPORTED);
        assertThat(result.warnings()).extracting("code").containsExactly("RECEIPT_OCR_NOT_CONFIGURED");
    }

    private ExternalHttpReceiptOcrProvider provider(HttpClient client, String serviceUrl) {
        return new ExternalHttpReceiptOcrProvider(
                new ReceiptOcrProperties("external_http", 5, 5242880, 30, serviceUrl, "vi"),
                client);
    }

    private ReceiptImageInput image(int index, String filename, String contentType, String text) {
        return new ReceiptImageInput(index, filename, contentType, text.length(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static class CapturingHttpClient extends BaseHttpClient {
        private final int status;
        private final String responseBody;
        private URI uri;
        private String body;

        private CapturingHttpClient(int status, String responseBody) {
            this.status = status;
            this.responseBody = responseBody;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            uri = request.uri();
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
