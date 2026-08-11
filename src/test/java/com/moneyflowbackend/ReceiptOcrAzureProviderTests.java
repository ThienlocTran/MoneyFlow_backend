package com.moneyflowbackend;

import com.moneyflowbackend.receipt.ocr.AzureDocumentIntelligenceReceiptOcrProvider;
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
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptOcrAzureProviderTests {
    @Test
    void mapsSuccessFromUrlRequest() {
        CapturingHttpClient client = new CapturingHttpClient(
                response(202, "", Map.of("Operation-Location", List.of("https://azure.example/ops/1"))),
                response(200, succeeded("""
                        HOA DON
                        QUAN CA PHE
                        Total 25000
                        """, "\"MerchantName\":{\"valueString\":\"QUAN CA PHE\",\"confidence\":0.91},\"TransactionDate\":{\"valueDate\":\"2026-08-01\",\"confidence\":0.9},\"Total\":{\"valueCurrency\":{\"amount\":25000,\"currencyCode\":\"VND\"},\"confidence\":0.88}"), Map.of()));
        AzureDocumentIntelligenceReceiptOcrProvider provider = provider(client, props("fake-key", "https://azure.example"));

        var result = provider.extractText(List.of(urlImage("https://cdn.example/receipt.jpg")));

        assertThat(result.status()).isEqualTo(ReceiptOcrStatus.SUCCEEDED);
        assertThat(result.text()).contains("HOA DON");
        assertThat(result.merchantName()).isEqualTo("QUAN CA PHE");
        assertThat(result.receiptDate()).hasToString("2026-08-01");
        assertThat(result.totalAmount()).isEqualByComparingTo("25000");
        assertThat(result.currency()).isEqualTo("VND");
        assertThat(result.warnings()).isEmpty();
        assertThat(client.requests.getFirst().uri().toString()).doesNotContain("fake-key");
        assertThat(client.requests.getFirst().headers().firstValue("Ocp-Apim-Subscription-Key")).contains("fake-key");
        assertThat(client.bodies.getFirst()).contains("\"urlSource\":\"https://cdn.example/receipt.jpg\"");
    }

    @Test
    void mapsMissingConfigWithoutHttpCall() {
        CapturingHttpClient client = new CapturingHttpClient();
        var result = provider(client, props("", "")).extractText(List.of(urlImage("https://cdn.example/receipt.jpg")));

        assertThat(result.status()).isEqualTo(ReceiptOcrStatus.DISABLED);
        assertThat(result.warnings()).extracting("code").containsExactly("OCR_NOT_CONFIGURED");
        assertThat(client.requests).isEmpty();
    }

    @Test
    void mapsMissingImageAndUnsupportedFormatWithoutHttpCall() {
        CapturingHttpClient client = new CapturingHttpClient();
        AzureDocumentIntelligenceReceiptOcrProvider provider = provider(client, props("fake-key", "https://azure.example"));

        assertThat(provider.extractText(List.of()).warnings()).extracting("code").containsExactly("RECEIPT_IMAGE_REQUIRED");
        assertThat(provider.extractText(List.of(new ReceiptImageInput(0, "a.txt", "text/plain", 1, new byte[] {1}))).warnings())
                .extracting("code").containsExactly("OCR_UNSUPPORTED_IMAGE_FORMAT");
        assertThat(client.requests).isEmpty();
    }

    @Test
    void mapsMissingOperationLocationAuthRateLimitAndTimeout() {
        AzureDocumentIntelligenceReceiptOcrProvider noOp = provider(new CapturingHttpClient(response(202, "", Map.of())), props("fake-key", "https://azure.example"));
        AzureDocumentIntelligenceReceiptOcrProvider auth = provider(new CapturingHttpClient(response(401, "{}", Map.of())), props("fake-key", "https://azure.example"));
        AzureDocumentIntelligenceReceiptOcrProvider rate = provider(new CapturingHttpClient(response(429, "{}", Map.of())), props("fake-key", "https://azure.example"));
        AzureDocumentIntelligenceReceiptOcrProvider timeout = provider(new CapturingHttpClient(
                response(202, "", Map.of("Operation-Location", List.of("https://azure.example/ops/1"))),
                response(200, "{\"status\":\"running\"}", Map.of())), props("fake-key", "https://azure.example", 1));

        assertThat(noOp.extractText(List.of(urlImage("https://cdn.example/receipt.jpg"))).warnings()).extracting("code").containsExactly("OCR_PROVIDER_FAILED");
        assertThat(auth.extractText(List.of(urlImage("https://cdn.example/receipt.jpg"))).warnings()).extracting("code").containsExactly("OCR_PROVIDER_AUTH_FAILED");
        assertThat(rate.extractText(List.of(urlImage("https://cdn.example/receipt.jpg"))).warnings()).extracting("code").containsExactly("OCR_PROVIDER_RATE_LIMITED");
        assertThat(timeout.extractText(List.of(urlImage("https://cdn.example/receipt.jpg"))).warnings()).extracting("code").containsExactly("OCR_PROVIDER_TIMEOUT");
    }

    @Test
    void mapsEmptyTextMissingTotalAndUnicodeNormalizationInput() {
        AzureDocumentIntelligenceReceiptOcrProvider empty = provider(new CapturingHttpClient(
                response(202, "", Map.of("Operation-Location", List.of("https://azure.example/ops/1"))),
                response(200, succeeded("", ""), Map.of())), props("fake-key", "https://azure.example"));
        AzureDocumentIntelligenceReceiptOcrProvider missingTotal = provider(new CapturingHttpClient(
                response(202, "", Map.of("Operation-Location", List.of("https://azure.example/ops/1"))),
                response(200, succeeded("HÓA DON\nNgay 2026-08-01", "\"TransactionDate\":{\"valueDate\":\"2026-08-01\",\"confidence\":0.9}"), Map.of())), props("fake-key", "https://azure.example"));

        var emptyResult = empty.extractText(List.of(urlImage("https://cdn.example/receipt.jpg")));
        var missingTotalResult = missingTotal.extractText(List.of(urlImage("https://cdn.example/receipt.jpg")));

        assertThat(emptyResult.status()).isEqualTo(ReceiptOcrStatus.TEXT_EMPTY);
        assertThat(emptyResult.warnings()).extracting("code").containsExactly("OCR_EMPTY_TEXT");
        assertThat(missingTotalResult.status()).isEqualTo(ReceiptOcrStatus.SUCCEEDED);
        assertThat(missingTotalResult.warnings()).extracting("code").containsExactly("OCR_TOTAL_NOT_FOUND");
        assertThat(Normalizer.isNormalized(Normalizer.normalize(missingTotalResult.text(), Normalizer.Form.NFC), Normalizer.Form.NFC)).isTrue();
    }

    private AzureDocumentIntelligenceReceiptOcrProvider provider(HttpClient client, ReceiptOcrProperties properties) {
        return new AzureDocumentIntelligenceReceiptOcrProvider(properties, client);
    }

    private ReceiptOcrProperties props(String key, String endpoint) {
        return props(key, endpoint, 30);
    }

    private ReceiptOcrProperties props(String key, String endpoint, int maxPollAttempts) {
        return new ReceiptOcrProperties("azure_document_intelligence", 5, 5242880, 1, "", "vi", endpoint, key,
                "prebuilt-receipt", "2024-11-30", 1, maxPollAttempts);
    }

    private ReceiptImageInput urlImage(String url) {
        return new ReceiptImageInput(0, "receipt.jpg", "image/jpeg", 10, new byte[0], url);
    }

    private String succeeded(String content, String fields) {
        return "{\"status\":\"succeeded\",\"analyzeResult\":{\"content\":\"" + escape(content) + "\",\"documents\":[{\"fields\":{" + fields + "}}]}}";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static FixedResponse<String> response(int status, String body, Map<String, List<String>> headers) {
        return new FixedResponse<>(status, body, headers);
    }

    private static class CapturingHttpClient extends BaseHttpClient {
        private final ArrayDeque<FixedResponse<String>> responses = new ArrayDeque<>();
        private final ArrayDeque<HttpRequest> requests = new ArrayDeque<>();
        private final ArrayDeque<String> bodies = new ArrayDeque<>();

        private CapturingHttpClient(FixedResponse<String>... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.add(request);
            bodies.add(readBody(request));
            FixedResponse<String> response = responses.removeFirst();
            return (HttpResponse<T>) new FixedResponse<>(response.statusCode(), response.body(), response.headerMap());
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
            request.bodyPublisher().ifPresent(publisher -> publisher.subscribe(collector));
            return collector.body();
        }
    }

    private record FixedResponse<T>(int statusCode, T body, Map<String, List<String>> headerMap) implements HttpResponse<T> {
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(headerMap, (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://azure.example"); }
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
