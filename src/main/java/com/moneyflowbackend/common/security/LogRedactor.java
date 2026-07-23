package com.moneyflowbackend.common.security;

import java.util.regex.Pattern;

public final class LogRedactor {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern AUTH_HEADER = Pattern.compile("(?i)(Authorization\\s*[:=]\\s*)[^\\s,;]+");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile("(?i)(JWT_SECRET|DB_PASSWORD|DATABASE_URL|MONEYFLOW_DB_URL|SPRING_DATASOURCE_PASSWORD|CLOUDINARY_API_SECRET|CLOUDINARY_URL|api_secret|access_token|refresh_token|password|token|signature)(\\s*[:=]\\s*)([^\\s,;]+)");
    private static final Pattern KEY_VALUE_PRIVATE_TEXT = Pattern.compile("(?i)(originalTranscript|editedTranscript|transcript|rawInput|prompt|response|playbackUrl)(\\s*[:=]\\s*)([^\\r\\n,;]+)");
    private static final Pattern JDBC_URL = Pattern.compile("jdbc:postgresql://[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSTGRES_URL = Pattern.compile("postgresql://[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOUDINARY_URL = Pattern.compile("cloudinary://[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNED_AUDIO_URL = Pattern.compile("https?://[^\\s,;]*(cloudinary|audio|voice)[^\\s,;]*(signature|token|expires|expires_at|X-Amz-Signature)[^\\s,;]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORAGE_KEY = Pattern.compile("(?i)(storagePublicId|storage_public_id|public_id|voice storage key)(\\s*[:=]\\s*)([^\\s,;]+)");

    private LogRedactor() {
    }

    public static String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = BEARER_TOKEN.matcher(value).replaceAll("Bearer " + REDACTED);
        redacted = AUTH_HEADER.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = KEY_VALUE_SECRET.matcher(redacted).replaceAll("$1$2" + REDACTED);
        redacted = JDBC_URL.matcher(redacted).replaceAll(REDACTED);
        redacted = POSTGRES_URL.matcher(redacted).replaceAll(REDACTED);
        redacted = CLOUDINARY_URL.matcher(redacted).replaceAll(REDACTED);
        redacted = SIGNED_AUDIO_URL.matcher(redacted).replaceAll(REDACTED);
        redacted = KEY_VALUE_PRIVATE_TEXT.matcher(redacted).replaceAll("$1$2" + REDACTED);
        return STORAGE_KEY.matcher(redacted).replaceAll("$1$2" + REDACTED);
    }
}
