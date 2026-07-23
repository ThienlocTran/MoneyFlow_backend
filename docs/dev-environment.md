# MoneyFlow Dev Environment

## Database variables

Prefer project-scoped variables:

```text
MONEYFLOW_DB_URL
MONEYFLOW_DB_USERNAME
MONEYFLOW_DB_PASSWORD
```

MoneyFlow still supports these legacy fallbacks, but they can collide with other projects:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
DB_URL
DB_USERNAME
DB_PASSWORD
```

Precedence:

```text
MONEYFLOW_DB_URL -> SPRING_DATASOURCE_URL -> DB_URL
MONEYFLOW_DB_USERNAME -> SPRING_DATASOURCE_USERNAME -> DB_USERNAME
MONEYFLOW_DB_PASSWORD -> SPRING_DATASOURCE_PASSWORD -> DB_PASSWORD
```

## IntelliJ

Use `Run -> Edit Configurations -> MoneyflowBackendApplication -> Environment variables`.

Remove stale generic variables:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

This is especially important when a shell already has `DB_URL` from another project. If `.env` is not loaded, MoneyFlow can still fall back to that generic value.

Set MoneyFlow-specific variables instead:

```text
MONEYFLOW_DB_URL=<jdbc url>
MONEYFLOW_DB_USERNAME=<username>
MONEYFLOW_DB_PASSWORD=<password>
```

Do not paste credentials into docs or committed files. Keep real values in local `.env` or the IDE run configuration.

## Neon cost controls

When using free Neon or any dev database that should sleep, set:

```text
DB_MIN_IDLE=0
DB_MAX_POOL_SIZE=2
```

The `local` profile defaults to these smaller Hikari values. Production remains environment-driven.

Use `/api/public/health/live` for Render, UptimeRobot, and other keep-alive checks. Do not use `/api/public/health/ready` for keep-alive because it runs a DB readiness query. Disable external keep-alive when saving Neon CU-hours matters more than avoiding cold starts.

Rotate any Neon credential that appears in chat, logs, screenshots, or committed files.

## Startup checks

Startup logs a masked target:

```text
MoneyFlow DB target: host=ep-rou***.aws.neon.tech, database=neondb, profile=production, flyway=true
```

If a database contains known warehouse/inventory Flyway scripts, startup fails fast with:

```text
WRONG_DATABASE_TARGET
```
