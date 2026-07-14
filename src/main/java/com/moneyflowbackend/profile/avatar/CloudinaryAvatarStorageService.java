package com.moneyflowbackend.profile.avatar;

import com.moneyflowbackend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
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

public class CloudinaryAvatarStorageService implements AvatarStorageService {
    private final HttpClient httpClient;
    private final Clock clock;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String baseFolder;

    public CloudinaryAvatarStorageService(
            HttpClient httpClient,
            Clock clock,
            String cloudName,
            String apiKey,
            String apiSecret,
            String baseFolder) {
        this.httpClient = httpClient;
        this.clock = clock;
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.baseFolder = trimSlashes(baseFolder == null || baseFolder.isBlank() ? "moneyflow/dev" : baseFolder);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String upload(String objectKey, MultipartFile file) {
        try {
            String publicId = baseFolder + "/" + trimSlashes(objectKey);
            Map<String, String> params = signedParams(Map.of(
                    "public_id", publicId,
                    "timestamp", String.valueOf(Instant.now(clock).getEpochSecond()),
                    "overwrite", "true"));
            String boundary = "MoneyFlowBoundary" + UUID.randomUUID();
            HttpRequest request = HttpRequest.newBuilder(uploadUri())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, params, file)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw storageFailed();
            }
            return jsonString(response.body(), "secure_url", cloudinaryUrl(publicId));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw storageFailed();
        }
    }

    private URI uploadUri() {
        return URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload");
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
        write(out, "--" + boundary + "\r\n");
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"avatar\"\r\n");
        write(out, "Content-Type: " + file.getContentType() + "\r\n\r\n");
        out.write(file.getBytes());
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String sha1(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw storageFailed();
        }
    }

    private String jsonString(String json, String key, String fallback) {
        if (json == null) {
            return fallback;
        }
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        int colon = keyIndex < 0 ? -1 : json.indexOf(':', keyIndex + needle.length());
        int start = colon < 0 ? -1 : json.indexOf('"', colon + 1);
        int end = start < 0 ? -1 : json.indexOf('"', start + 1);
        if (end < 0) {
            return fallback;
        }
        return json.substring(start + 1, end).replace("\\/", "/");
    }

    private String cloudinaryUrl(String publicId) {
        return "https://res.cloudinary.com/" + cloudName + "/image/upload/" + publicId;
    }

    private String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private BusinessException storageFailed() {
        return new BusinessException("AVATAR_STORAGE_FAILED", "Tải ảnh đại diện thất bại. Vui lòng thử lại.", HttpStatus.BAD_GATEWAY);
    }
}
