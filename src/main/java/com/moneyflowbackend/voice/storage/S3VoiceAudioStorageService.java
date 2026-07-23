package com.moneyflowbackend.voice.storage;

import com.moneyflowbackend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public class S3VoiceAudioStorageService implements VoiceAudioStorageService {
    private static final String PROVIDER = "s3";
    private static final String SERVICE = "s3";
    private static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final HttpClient httpClient;
    private final Clock clock;
    private final String bucket;
    private final String region;
    private final URI endpoint;
    private final String accessKey;
    private final String secretKey;
    private final boolean pathStyleAccess;

    public S3VoiceAudioStorageService(
            HttpClient httpClient,
            Clock clock,
            String bucket,
            String region,
            String endpoint,
            String accessKey,
            String secretKey,
            boolean pathStyleAccess) {
        this.httpClient = httpClient;
        this.clock = clock;
        this.bucket = bucket;
        this.region = region == null || region.isBlank() ? "auto" : region.trim();
        this.endpoint = URI.create(trimSlash(endpoint));
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.pathStyleAccess = pathStyleAccess;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public StoredVoiceAudio upload(String objectKey, MultipartFile file) {
        try {
            byte[] body = file.getBytes();
            HttpRequest.Builder builder = signed("PUT", objectKey, Map.of(), body)
                    .header("Content-Type", file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw storageFailed("S3 voice audio upload failed");
            }
            return new StoredVoiceAudio(PROVIDER, objectKey, "private");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw storageFailed("S3 voice audio upload failed");
        }
    }

    @Override
    public VoiceAudioPlayback playbackUrl(String storageKey, String mimeType) {
        Instant expiresAt = Instant.now(clock).plusSeconds(300);
        Map<String, String> params = new TreeMap<>();
        params.put("X-Amz-Algorithm", "AWS4-HMAC-SHA256");
        params.put("X-Amz-Credential", accessKey + "/" + scope(expiresAt));
        params.put("X-Amz-Date", AMZ_DATE.format(expiresAt.minusSeconds(300)));
        params.put("X-Amz-Expires", "300");
        params.put("X-Amz-SignedHeaders", "host");
        String signature = signature("GET", storageKey, params, "host:" + uri(storageKey).getHost() + "\n", "host", UNSIGNED_PAYLOAD, expiresAt.minusSeconds(300));
        params.put("X-Amz-Signature", signature);
        return new VoiceAudioPlayback(uri(storageKey) + "?" + query(params), expiresAt, mimeType);
    }

    @Override
    public void delete(String storageKey) {
        try {
            HttpResponse<String> response = httpClient.send(
                    signed("DELETE", storageKey, Map.of(), new byte[0]).DELETE().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw storageFailed("S3 voice audio delete failed");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw storageFailed("S3 voice audio delete failed");
        }
    }

    private HttpRequest.Builder signed(String method, String key, Map<String, String> params, byte[] body) {
        Instant now = Instant.now(clock);
        URI uri = params.isEmpty() ? uri(key) : URI.create(uri(key) + "?" + query(params));
        String payloadHash = sha256(body);
        String headers = "host:" + uri.getHost() + "\n" + "x-amz-content-sha256:" + payloadHash + "\n" + "x-amz-date:" + AMZ_DATE.format(now) + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";
        String auth = "AWS4-HMAC-SHA256 Credential=%s/%s, SignedHeaders=%s, Signature=%s".formatted(
                accessKey, scope(now), signedHeaders, signature(method, key, params, headers, signedHeaders, payloadHash, now));
        return HttpRequest.newBuilder(uri)
                .header("Authorization", auth)
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", AMZ_DATE.format(now));
    }

    private String signature(String method, String key, Map<String, String> params, String headers, String signedHeaders, String payloadHash, Instant instant) {
        String canonical = method + "\n" + canonicalPath(key) + "\n" + query(params) + "\n" + headers + "\n" + signedHeaders + "\n" + payloadHash;
        String stringToSign = "AWS4-HMAC-SHA256\n" + AMZ_DATE.format(instant) + "\n" + scope(instant) + "\n" + sha256(canonical.getBytes(StandardCharsets.UTF_8));
        return hex(hmac(signingKey(instant), stringToSign));
    }

    private byte[] signingKey(Instant instant) {
        byte[] date = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), DATE.format(instant));
        byte[] regional = hmac(date, region);
        byte[] service = hmac(regional, SERVICE);
        return hmac(service, "aws4_request");
    }

    private String scope(Instant instant) {
        return DATE.format(instant) + "/" + region + "/" + SERVICE + "/aws4_request";
    }

    private URI uri(String key) {
        String encodedPath = path(key);
        if (pathStyleAccess) {
            return URI.create(endpoint + "/" + bucket + encodedPath);
        }
        String authority = bucket + "." + endpoint.getHost() + (endpoint.getPort() > 0 ? ":" + endpoint.getPort() : "");
        return URI.create(endpoint.getScheme() + "://" + authority + encodedPath);
    }

    private String path(String key) {
        String[] parts = key.split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            out.append('/').append(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return out.toString();
    }

    private String canonicalPath(String key) {
        return pathStyleAccess ? "/" + bucket + path(key) : path(key);
    }

    private String query(Map<String, String> params) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if (!out.isEmpty()) out.append('&');
            out.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8).replace("+", "%20"));
            out.append('=');
            out.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return out.toString();
    }

    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw storageFailed("S3 voice audio signature failed");
        }
    }

    private String sha256(byte[] value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw storageFailed("S3 voice audio signature failed");
        }
    }

    private String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    private String trimSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private BusinessException storageFailed(String message) {
        return new BusinessException("AUDIO_STORAGE_FAILED", message, HttpStatus.BAD_GATEWAY);
    }
}
