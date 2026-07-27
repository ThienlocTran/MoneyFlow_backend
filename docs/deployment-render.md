# MoneyFlow Backend Render Deployment

## Render Docker service

Use a Render Web Service for the backend.

Recommended settings:

```text
Runtime/Language: Docker
Branch: dev
Root Directory: blank if this repo root is moneyflow-backend
Build Command: leave blank; Dockerfile handles build
Start Command: leave blank; Dockerfile CMD handles start
Health check path: /api/public/health/live
```

Do not choose Node. Do not use yarn. Docker deployment requires the `Dockerfile` at the repo root.

Native Java fallback, only if Render offers a Java runtime:

```text
Build command: ./mvnw clean package -DskipTests
Start command: java -jar target/moneyflow-backend-0.0.1-SNAPSHOT.jar
```

The liveness endpoint is public and does not touch the database. Do not use dashboard, wallet, transaction, or other authenticated financial APIs for health checks.

## Required environment variables

Set names only; never commit values.

```text
SPRING_PROFILES_ACTIVE=production
MONEYFLOW_DB_URL
MONEYFLOW_DB_USERNAME
MONEYFLOW_DB_PASSWORD
JWT_SECRET
GOOGLE_CLIENT_ID
CORS_ALLOWED_ORIGINS
MONEYFLOW_AVATAR_STORAGE_PROVIDER=cloudinary
MONEYFLOW_CLOUDINARY_CLOUD_NAME
MONEYFLOW_CLOUDINARY_API_KEY
MONEYFLOW_CLOUDINARY_API_SECRET
MONEYFLOW_CLOUDINARY_BASE_FOLDER=moneyflow/prod
```

Optional tuning:

```text
SERVER_PORT
DB_MAX_POOL_SIZE
DB_MIN_IDLE
DB_CONNECTION_TIMEOUT
DB_IDLE_TIMEOUT
DB_MAX_LIFETIME
JWT_ACCESS_TOKEN_TTL_MINUTES
JWT_REFRESH_TOKEN_TTL_DAYS
MONEYFLOW_AVATAR_MAX_BYTES
```

For free Neon or low-traffic development services where compute sleep matters, use:

```text
DB_MIN_IDLE=0
DB_MAX_POOL_SIZE=2
```

Production can use larger values when traffic needs them. Keep pool sizing in environment variables instead of hardcoding deployment-specific values.

Production CORS must include the Vercel frontend origin in `CORS_ALLOWED_ORIGINS`, for example:

```text
CORS_ALLOWED_ORIGINS=https://<moneyflow-frontend>.vercel.app
```

## Cloudinary folders

Use separate folders:

```text
dev/local: MONEYFLOW_CLOUDINARY_BASE_FOLDER=moneyflow/dev
production: MONEYFLOW_CLOUDINARY_BASE_FOLDER=moneyflow/prod
```

Never use the prod folder locally. Never use the dev folder in production.

## Database safety

MoneyFlow uses `MONEYFLOW_DB_URL` first, then falls back to `SPRING_DATASOURCE_URL`, then `DB_URL`.

The startup guard checks Flyway history before migration and fails if it detects known non-MoneyFlow migration scripts. This helps avoid deploying against the wrong project database.

## UptimeRobot keep-alive

Preferred pattern:

```text
UptimeRobot -> https://<moneyflow-render-service>.onrender.com/api/public/health/live
```

Create a monitor at https://dashboard.uptimerobot.com/onboarding:

```text
Monitor Type: HTTP(s)
Friendly Name: MoneyFlow Backend
URL: https://<moneyflow-render-service>.onrender.com/api/public/health/live
Monitoring Interval: 10 minutes
Timeout: 10 seconds if available
```

Expected result: HTTP `200`.

Do not ping `/api/public/health/ready` for keep-alive because it touches the database. Use `/ready` only for readiness checks when DB connectivity matters.

If saving Neon CU-hours matters more than avoiding cold starts, disable UptimeRobot or any other external keep-alive temporarily. Rotate any Neon credential that has been exposed in chat, logs, screenshots, or committed files.

## InternFlow comparison

Inspected project:

```text
D:\InternFlow\InternFlow
```

InternFlow has an internal `KeepAliveScheduler` that self-pings:

```text
APP_BASE_URL + /api/health/live
Interval: 840000 ms, about 14 minutes
Endpoint: public liveness, dbChecked=false
Secrets: none for the ping
```

That endpoint design is reusable: public, lightweight, no DB access.

The self-ping source is not reusable as the only keep-alive for MoneyFlow. If the Render service is already asleep, its scheduler is asleep too. An external pinger such as UptimeRobot is safer.
