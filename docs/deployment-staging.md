# MoneyFlow Staging Deployment

This checklist prepares a non-production staging environment from the `release/staging-moneyflow-core` branch. Do not commit real credentials, database URLs, JWT secrets, Cloudinary secrets, or `.env` files.

Security hardening requirements:

- Set `CORS_ALLOWED_ORIGINS` to explicit staging frontend origin(s). Do not use `*` with credentials.
- Keep rate limiting enabled with defaults unless staging load testing needs temporary lower/higher values.
- Leave `app.security.rate-limit.trust-forwarded-for=false` unless the proxy chain is trusted and documented.
- Confirm logs do not include Authorization headers, tokens, database URLs, Cloudinary secrets, signed audio URLs, or storage public ids.
- In-memory rate limits are acceptable for the single-instance staging MVP; use shared/platform limits before multi-instance scaling.

## Branches

- Backend repository: deploy `release/staging-moneyflow-core`.
- Frontend repository: deploy `release/staging-moneyflow-core`.
- Do not deploy directly from feature branches.
- Do not merge this branch to `main` until staging UAT is complete.

## Neon Staging Database

1. Create a dedicated Neon project or branch for staging.
2. Use the pooled PostgreSQL connection string for the backend.
3. Keep staging separate from production data.
4. Let Flyway apply migrations on backend startup.
5. Do not run Flyway repair unless a human explicitly approves a recovery plan.

Required backend DB variables:

```text
MONEYFLOW_DB_URL=jdbc:postgresql://<neon-pooler-host>:5432/<database>?sslmode=require&channelBinding=require
MONEYFLOW_DB_USERNAME=<username>
MONEYFLOW_DB_PASSWORD=<password>
```

## Render Backend

Create or update the Render staging web service:

- Repository: MoneyFlow backend repository.
- Branch: `release/staging-moneyflow-core`.
- Runtime: Java 21.
- Build command: `./mvnw clean package`.
- Start command: `java -jar target/moneyflow-backend-0.0.1-SNAPSHOT.jar`.
- Health check path: `/api/public/health/live`.

Required environment variables:

```text
SPRING_PROFILES_ACTIVE=staging
MONEYFLOW_DB_URL=
MONEYFLOW_DB_USERNAME=
MONEYFLOW_DB_PASSWORD=
JWT_SECRET=
CORS_ALLOWED_ORIGINS=
MONEYFLOW_AUDIO_STORAGE_PROVIDER=disabled
MONEYFLOW_AUDIO_FOLDER=moneyflow/staging/voice
MONEYFLOW_AUDIO_MAX_BYTES=10485760
MONEYFLOW_AUDIO_ALLOWED_TYPES=audio/webm,audio/mp4,audio/mpeg,audio/wav
```

Optional Cloudinary staging variables, required only when `MONEYFLOW_AUDIO_STORAGE_PROVIDER=cloudinary`:

```text
MONEYFLOW_CLOUDINARY_CLOUD_NAME=
MONEYFLOW_CLOUDINARY_API_KEY=
MONEYFLOW_CLOUDINARY_API_SECRET=
```

## Vercel Frontend

Create or update the Vercel staging project:

- Repository: MoneyFlow frontend repository.
- Branch: `release/staging-moneyflow-core`.
- Framework: Vite.
- Install command: `pnpm install --frozen-lockfile`.
- Build command: `pnpm run build`.
- Root directory: `moneyflow-ui/moneyflow-ui`.
- Output directory: `dist`.

Required frontend variable:

```text
VITE_API_BASE_URL=https://<render-backend-staging-domain>/api
```

The frontend service reads only `VITE_API_BASE_URL`; do not add duplicate API URL variables unless the code changes. The frontend normalizes this value, so both `https://<render-backend-staging-domain>` and `https://<render-backend-staging-domain>/api` are accepted without creating `/api/api`.

## Health Checks

After backend deploy:

```text
GET https://<backend-staging-domain>/api/public/health/live
GET https://<backend-staging-domain>/api/public/health/ready
```

`live` must not require database access. `ready` should verify database connectivity.

## Smoke Checklist

- Backend `live` returns success.
- Backend `ready` returns success.
- Frontend loads the staging bundle.
- API base URL points to the staging backend.
- Login page renders.
- No Excel import.
- No transaction creation.
- No mutation of real financial data.

## Rollback

- Backend: redeploy the previous successful Render deploy.
- Frontend: promote or redeploy the previous successful Vercel deployment.
- Database: prefer forward-only corrective migrations. Do not edit applied migrations and do not run Flyway repair without approval.

## Known Risks

- V3 changes the `voice_records.voice_status` CHECK constraint for audio storage states.
- `MONEYFLOW_AUDIO_STORAGE_PROVIDER=disabled` is safest for initial staging unless Cloudinary staging credentials are ready.
- `CORS_ALLOWED_ORIGINS` must exactly match the Vercel staging origin.
