package com.moneyflowbackend.voice.storage;

import com.moneyflowbackend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class CloudinaryVoiceAudioStorageService implements VoiceAudioStorageService {
    private static final String PROVIDER = "cloudinary";

    private final HttpClient httpClient;
    private final Clock clock;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;

    public CloudinaryVoiceAudioStorageService(
            HttpClient httpClient,
            Clock clock,
            String cloudName,
            String apiKey,
            String apiSecret,
            String folder) {
        this.httpClient = httpClient;
        this.clock = clock;
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.folder = trimSlashes(folder == null || folder.isBlank() ? "moneyflow/voice" : folder);
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
            String publicId = folder + "/" + trimSlashes(objectKey);
            long timestamp = Instant.now(clock).getEpochSecond();
            Map<String, String> params = signedParams(Map.of(
                    "public_id", publicId,
                    "timestamp", String.valueOf(timestamp),
                    "type", "authenticated",
                    "overwrite", "true"));
            String boundary = "MoneyFlowBoundary" + UUID.randomUUID();
            HttpRequest request = HttpRequest.newBuilder(uploadUri())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, params, file)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw storageFailed("Cloudinary upload failed");
            }
            String storedPublicId = publicIdFrom(response.body(), publicId);
            return new StoredVoiceAudio(PROVIDER, storedPublicId, "authenticated");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw storageFailed("Cloudinary upload failed");
        }
    }

    @Override
    public VoiceAudioPlayback playbackUrl(String storagePublicId, String mimeType) {
        Instant expiresAt = Instant.now(clock).plusSeconds(300);
        Map<String, String> params = signedParams(Map.of(
                "public_id", storagePublicId,
                "type", "authenticated",
                "expires_at", String.valueOf(expiresAt.getEpochSecond())));
        String url = "https://api.cloudinary.com/v1_1/%s/video/download?%s".formatted(cloudName, query(params));
        return new VoiceAudioPlayback(url, expiresAt, mimeType);
    }

    @Override
    public StoredVoiceAudioStream open(String storagePublicId, String mimeType) {
        try {
            VoiceAudioPlayback playback = playbackUrl(storagePublicId, mimeType);
            HttpRequest request = HttpRequest.newBuilder(URI.create(playback.playbackUrl())).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw storageFailed("Cloudinary playback failed");
            }
            return new StoredVoiceAudioStream(response.body(), mimeType, response.body().length);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw storageFailed("Cloudinary playback failed");
        }
    }

    @Override
    public void delete(String storagePublicId) {
        try {
            long timestamp = Instant.now(clock).getEpochSecond();
            Map<String, String> params = signedParams(Map.of(
                    "public_id", storagePublicId,
                    "timestamp", String.valueOf(timestamp),
                    "type", "authenticated"));
            String boundary = "MoneyFlowBoundary" + UUID.randomUUID();
            HttpRequest request = HttpRequest.newBuilder(destroyUri())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, params, null)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw storageFailed("Cloudinary delete failed");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw storageFailed("Cloudinary delete failed");
        }
    }

    private URI uploadUri() {
        return URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/video/upload");
    }

    private URI destroyUri() {
        return URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/video/destroy");
    }

    private Map<String, String> signedParams(Map<String, String> params) {
        Map<String, String> signed = new TreeMap<>(params);
        signed.put("api_key", apiKey);
        signed.put("signature", sign(params));
        return signed;
    }

    private String sign(Map<String, String> params) {
        Map<String, String> sorted = new TreeMap<>(params);
        StringBuilder base = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!base.isEmpty()) base.append('&');
            base.append(entry.getKey()).append('=').append(entry.getValue());
        }
        base.append(apiSecret);
        return sha1(base.toString());
    }

    private byte[] multipart(String boundary, Map<String, String> fields, MultipartFile file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n");
            write(out, field.getValue() + "\r\n");
        }
        if (file != null) {
            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"voice\"\r\n");
            write(out, "Content-Type: " + file.getContentType() + "\r\n\r\n");
            out.write(file.getBytes());
            write(out, "\r\n");
        }
        write(out, "--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw storageFailed("Cloudinary signature failed");
        }
    }

    private String query(Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if (!query.isEmpty()) query.append('&');
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            query.append('=');
            query.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private String publicIdFrom(String json, String fallback) {
        if (json == null) {
            return fallback;
        }
        String needle = "\"public_id\"";
        int key = json.indexOf(needle);
        if (key < 0) {
            return fallback;
        }
        int colon = json.indexOf(':', key + needle.length());
        int start = json.indexOf('"', colon + 1);
        int end = json.indexOf('"', start + 1);
        if (colon < 0 || start < 0 || end < 0) {
            return fallback;
        }
        return json.substring(start + 1, end).replace("\\/", "/");
    }

    private String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private BusinessException storageFailed(String message) {
        return new BusinessException("AUDIO_STORAGE_FAILED", message, HttpStatus.BAD_GATEWAY);
    }
}
