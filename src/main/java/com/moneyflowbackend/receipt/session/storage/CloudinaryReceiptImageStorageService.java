package com.moneyflowbackend.receipt.session.storage;

import com.moneyflowbackend.common.exception.BusinessException;
import com.moneyflowbackend.common.security.LogRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class CloudinaryReceiptImageStorageService implements ReceiptImageStorageService {
    private static final Logger log = LoggerFactory.getLogger(CloudinaryReceiptImageStorageService.class);
    private static final String PROVIDER = "cloudinary";

    private final HttpClient httpClient;
    private final Clock clock;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String baseFolder;

    public CloudinaryReceiptImageStorageService(
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
        this.baseFolder = trimSlashes(baseFolder == null || baseFolder.isBlank() ? "dev" : baseFolder);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public StoredReceiptImage upload(String objectKey, MultipartFile file) {
        try {
            String cleanKey = trimSlashes(objectKey);
            String assetFolder = baseFolder + parentFolder(cleanKey);
            String publicId = leaf(cleanKey);
            Map<String, String> params = signedParams(Map.of(
                    "public_id", publicId,
                    "asset_folder", assetFolder,
                    "timestamp", String.valueOf(Instant.now(clock).getEpochSecond()),
                    "overwrite", "true"));
            String boundary = "MoneyFlowBoundary" + UUID.randomUUID();
            HttpRequest request = HttpRequest.newBuilder(uploadUri())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(boundary, params, file)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Cloudinary receipt upload failed: status={}, assetFolder={}, publicId={}, body={}",
                        response.statusCode(), assetFolder, publicId, LogRedactor.redact(response.body()));
                throw storageFailed();
            }
            String fallbackPublicId = assetFolder + "/" + publicId;
            String storedPublicId = jsonString(response.body(), "public_id", fallbackPublicId);
            String url = jsonString(response.body(), "secure_url", cloudinaryUrl(storedPublicId));
            return new StoredReceiptImage(PROVIDER, storedPublicId, url);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Cloudinary receipt upload failed before response: exception={}", ex.getClass().getSimpleName());
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
        write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"receipt\"\r\n");
        write(out, "Content-Type: " + file.getContentType() + "\r\n\r\n");
        out.write(file.getBytes());
        write(out, "\r\n");
        write(out, "--" + boundary + "--\r\n");
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
        if (json == null) return fallback;
        String needle = "\"" + key + "\"";
        int keyIndex = json.indexOf(needle);
        int colon = keyIndex < 0 ? -1 : json.indexOf(':', keyIndex + needle.length());
        int start = colon < 0 ? -1 : json.indexOf('"', colon + 1);
        int end = start < 0 ? -1 : json.indexOf('"', start + 1);
        return end < 0 ? fallback : json.substring(start + 1, end).replace("\\/", "/");
    }

    private String cloudinaryUrl(String publicId) {
        return "https://res.cloudinary.com/" + cloudName + "/image/upload/" + publicId;
    }

    private String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String parentFolder(String value) {
        int slash = value.lastIndexOf('/');
        return slash < 0 ? "" : "/" + value.substring(0, slash);
    }

    private String leaf(String value) {
        int slash = value.lastIndexOf('/');
        return slash < 0 ? value : value.substring(slash + 1);
    }

    private BusinessException storageFailed() {
        return new BusinessException("RECEIPT_STORAGE_FAILED", "Receipt image storage failed", HttpStatus.BAD_GATEWAY);
    }
}
