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

Set MoneyFlow-specific variables instead:

```text
MONEYFLOW_DB_URL=<jdbc url>
MONEYFLOW_DB_USERNAME=<username>
MONEYFLOW_DB_PASSWORD=<password>
```

Do not paste credentials into docs or committed files. Keep real values in local `.env` or the IDE run configuration.

## Startup checks

Startup logs a masked target:

```text
MoneyFlow DB target: host=ep-rou***.aws.neon.tech, database=neondb, profile=production, flyway=true
```

If a database contains known warehouse/inventory Flyway scripts, startup fails fast with:

```text
WRONG_DATABASE_TARGET
```
