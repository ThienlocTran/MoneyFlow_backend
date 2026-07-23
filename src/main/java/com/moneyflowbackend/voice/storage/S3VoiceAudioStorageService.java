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
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class S3VoiceAudioStorageService implements VoiceAudioStorageService {
    private static final String PROVIDER = "s3";
    private static final String SERVICE = "s3";
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final HttpClient httpClient;
    private final Clock clock;
    private final String bucket;
    private final String region;
    private final String endpoint;
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
        this.region = region == null || region.isBlank() ? "auto" : region;
        this.endpoint = endpoint == null || endpoint.isBlank()
                ? "https://s3.%s.amazonaws.com".formatted(this.region)
                : trimTrailingSlash(endpoint);
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.pathStyleAccess = pathStyleAccess;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public StoredVoiceAudio upload(String objectKey, MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            send("PUT", objectKey, file.getContentType(), bytes);
            return new StoredVoiceAudio(PROVIDER, objectKey, "private");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw storageFailed("S3 voice audio upload failed");
        }
    }

    @Override
    public StoredVoiceAudioStream open(String storageKey, String mimeType) {
        try {
            HttpResponse<byte[]> response = send("GET", storageKey, mimeType, null);
            return new StoredVoiceAudioStream(response.body(), mimeType, response.body().length);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw storageFailed("S3 voice audio playback failed");
        }
    }

    @Override
    public VoiceAudioPlayback playbackUrl(String storagePublicId, String mimeType) {
        throw new BusinessException("AUDIO_PLAYBACK_REQUIRES_BACKEND_STREAM", "Use backend audio streaming", HttpStatus.GONE);
    }

    @Override
    public void delete(String storagePublicId) {
        try {
            send("DELETE", storagePublicId, null, null);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw storageFailed("S3 voice audio delete failed");
        }
    }

    private HttpResponse<byte[]> send(String method, String key, String contentType, byte[] body) throws Exception {
        URI uri = uri(key);
        byte[] payload = body == null ? new byte[0] : body;
        String payloadHash = sha256Hex(payload);
        Instant now = Instant.now(clock);
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String host = uri.getHost();
        Map<String, String> headers = new TreeMap<>();
        headers.put("host", host);
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", amzDate);
        if (contentType != null && !contentType.isBlank()) {
            headers.put("content-type", contentType);
        }
        String signedHeaders = String.join(";", headers.keySet());
        String canonicalHeaders = headers.entrySet().stream()
                .map(e -> e.getKey().toLowerCase(Locale.ROOT) + ":" + e.getValue().trim() + "\n")
                .reduce("", String::concat);
        String canonicalRequest = method + "\n" + uri.getRawPath() + "\n\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String scope = dateStamp + "/" + region + "/" + SERVICE + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String signature = HexFormat.of().formatHex(hmac(signingKey(dateStamp), stringToSign));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("Authorization", "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                        + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature)
                .header("x-amz-content-sha256", payloadHash)
                .header("x-amz-date", amzDate)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentType != null && !contentType.isBlank()) {
            request.header("Content-Type", contentType);
        }
        HttpResponse<byte[]> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw storageFailed("S3 voice audio request failed");
        }
        return response;
    }

    private URI uri(String key) {
        String encodedKey = encodeKey(key);
        URI base = URI.create(endpoint);
        if (pathStyleAccess) {
            return URI.create(endpoint + "/" + bucket + "/" + encodedKey);
        }
        return URI.create(base.getScheme() + "://" + bucket + "." + base.getAuthority() + "/" + encodedKey);
    }

    private byte[] signingKey(String dateStamp) throws Exception {
        byte[] dateKey = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] dateRegionKey = hmac(dateKey, region);
        byte[] dateRegionServiceKey = hmac(dateRegionKey, SERVICE);
        return hmac(dateRegionServiceKey, "aws4_request");
    }

    private byte[] hmac(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String encodeKey(String key) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20").replace("%2F", "/");
    }

    private String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private BusinessException storageFailed(String message) {
        return new BusinessException("AUDIO_STORAGE_FAILED", message, HttpStatus.BAD_GATEWAY);
    }
}
