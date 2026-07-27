# MoneyFlow Deployment And Environment

Last synced: 2026-07-23.

## Database Environment Variables

Prefer project-scoped variables:

```text
MONEYFLOW_DB_URL
MONEYFLOW_DB_USERNAME
MONEYFLOW_DB_PASSWORD
```

Legacy fallbacks still exist, but generic names can collide with other projects:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
DB_URL
DB_USERNAME
DB_PASSWORD
```

Remove `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` from MoneyFlow terminals and IDE run configs when they belong to another project. Use the `MONEYFLOW_*` names for MoneyFlow. Rotate any Neon credential that appears in chat, logs, screenshots, or committed files.

Precedence:

```text
MONEYFLOW_DB_URL -> SPRING_DATASOURCE_URL -> DB_URL
MONEYFLOW_DB_USERNAME -> SPRING_DATASOURCE_USERNAME -> DB_USERNAME
MONEYFLOW_DB_PASSWORD -> SPRING_DATASOURCE_PASSWORD -> DB_PASSWORD
```

## Datasource Safety

- Tests must not run against Neon production-like DB.
- Staging must not mutate real financial data.
- Keep staging separate from production data.
- Do not run Flyway repair unless explicitly approved.
- Do not run database repair, mass update, mojibake fixer, importer, or corrective jobs without backup and dry-run.
- Startup guard should fail fast when pointed at a known wrong database target.

## Staging Rules

- Use dedicated staging DB/project/branch.
- Use staging-only accounts and staging-only financial records.
- No Excel import during smoke/UAT unless explicitly approved.
- No mutation of real financial data.
- Health checks:
  - `/api/public/health/live` does not touch DB.
  - `/api/public/health/ready` may verify DB connectivity.

## Render Backend

Required variable names:

```text
SPRING_PROFILES_ACTIVE=staging
MONEYFLOW_DB_URL
MONEYFLOW_DB_USERNAME
MONEYFLOW_DB_PASSWORD
JWT_SECRET
GOOGLE_CLIENT_ID
CORS_ALLOWED_ORIGINS
MONEYFLOW_AUDIO_STORAGE_PROVIDER=disabled
```

Production uses `SPRING_PROFILES_ACTIVE=production` and production-only values. Never commit real values.

For free Neon or development environments where compute sleep matters, prefer:

```text
DB_MIN_IDLE=0
DB_MAX_POOL_SIZE=2
```

Production may keep larger pool values when traffic requires them; keep pool sizing environment-driven.

## Keep-Alive And Health Checks

- Render health check path should be `/api/public/health/live`.
- UptimeRobot and other keep-alive monitors should ping `/api/public/health/live` only.
- Do not use `/api/public/health/ready` for keep-alive because it checks DB connectivity.
- Disable external keep-alive temporarily when saving Neon CU-hours matters more than cold-start avoidance.

## Audio Storage

- Audio storage provider disabled is safest until Cloudinary is configured.
- Use `MONEYFLOW_AUDIO_STORAGE_PROVIDER=disabled` by default.
- Enable Cloudinary only with backend-only provider variables configured.
- Provider secrets stay backend-only.
- Signed playback URLs must not be logged.

Cloudinary variable names only:

```text
MONEYFLOW_CLOUDINARY_CLOUD_NAME
MONEYFLOW_CLOUDINARY_API_KEY
MONEYFLOW_CLOUDINARY_API_SECRET
```

## CORS

- No wildcard CORS with credentials.
- `CORS_ALLOWED_ORIGINS` must list explicit frontend origins.
- Localhost defaults are acceptable only for local development.

## Logs And Secrets

- No secrets in logs.
- Do not log Authorization headers, JWTs, refresh tokens, database URLs, Cloudinary secrets, signed audio URLs, storage public IDs, passwords, or raw multipart audio payloads.
- Error responses must not expose Java stack traces, SQL details, DB URLs, JWT parser internals, provider secrets, or signed URLs.

## Deployment Safety

- No deploy from feature branches unless explicitly approved.
- No repair scripts during deploy.
- No import scripts during deploy.
- Rollback should redeploy previous successful app version.
- Database rollback should prefer forward-only corrective migrations; never edit applied migrations.
