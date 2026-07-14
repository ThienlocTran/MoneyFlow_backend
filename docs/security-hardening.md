# Security hardening

## Rate limits

MoneyFlow uses an in-memory fixed-window limiter for staging MVP hardening. It is intentionally small and has no external dependency.

Protected endpoints:

- `POST /api/public/auth/login`: 10 requests/minute/IP
- `POST /api/public/auth/register`: 10 requests/minute/IP
- `POST /api/public/auth/refresh`: 30 requests/minute/user or IP
- `POST /api/workspaces/{workspaceId}/invitations`: 20 requests/hour/user/workspace
- `POST /api/voice-records/{voiceRecordId}/audio`: 20 requests/hour/user
- `GET /api/workspaces/{workspaceId}/transactions/export.csv`: 10 requests/hour/user/workspace

Config knobs:

- `app.security.rate-limit.auth-per-minute`
- `app.security.rate-limit.refresh-per-minute`
- `app.security.rate-limit.invitations-per-hour`
- `app.security.rate-limit.voice-uploads-per-hour`
- `app.security.rate-limit.exports-per-hour`
- `app.security.rate-limit.trust-forwarded-for`

The limiter stores endpoint group, user id or client IP, and workspace id where needed. It does not store raw JWTs, refresh tokens, passwords, or request bodies.

Limitation: counters are process-local and reset on app restart. Multiple app instances do not share limits. Use Redis, gateway/WAF limits, or platform rate limiting before scaling beyond a single Render instance.

## Safe errors

Production and staging responses use stable codes and user-safe Vietnamese messages for unauthorized, forbidden, rate-limit, validation, storage-disabled, database, and internal errors. Generic 500/SQL errors do not include stack traces, class names, database URLs, tokens, or provider URLs.

Rate limit responses return HTTP `429`, code `RATE_LIMITED`, and `retryAfterSeconds`.

## Log redaction

`LogRedactor` redacts:

- `Authorization` and bearer tokens
- access/refresh tokens
- JWT and database env values
- PostgreSQL/JDBC URLs
- Cloudinary URLs/API secrets
- signed audio URLs
- voice storage/public ids
- password-like key/value pairs

Do not log auth request bodies, multipart audio payloads, raw exceptions that may include secrets, or signed playback URLs.

## Security headers and CORS

API responses include `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and `Referrer-Policy: no-referrer`.

Staging/production must set `CORS_ALLOWED_ORIGINS` to explicit frontend origins. Wildcard origins are filtered out when credentials are enabled. Local development defaults remain localhost-only.
