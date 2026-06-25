# MoneyFlow Backend

Spring Boot 4.1 backend for the MoneyFlow personal finance application.

## Tech Stack

- **Java 21** / **Spring Boot 4.1**
- **PostgreSQL** (local Docker or [Neon](https://neon.tech) serverless)
- **Flyway** for database migrations
- **Spring Security** for endpoint protection
- **HikariCP** connection pooling
- **Lombok** for boilerplate reduction

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose (for local development)
- A Neon account (for cloud development)

## Quick Start

### Option 1: Local PostgreSQL (Docker)

```bash
# Start PostgreSQL container
docker compose up -d

# Run the backend with local profile
# Linux/macOS:
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run

# Windows PowerShell:
$env:SPRING_PROFILES_ACTIVE="local"
mvn spring-boot:run
```

Local DB credentials are pre-configured in `application-local.yml`:
- URL: `jdbc:postgresql://localhost:5432/moneyflow`
- User: `moneyflow`
- Password: `moneyflow`

### Option 2: Neon PostgreSQL (Cloud)

1. Copy `.env.example` to `.env` and fill in your Neon credentials:

```bash
cp .env.example .env
# Edit .env with your Neon pooler endpoint, username, and password
```

2. Set environment variables and run:

**Linux/macOS:**
```bash
export SPRING_PROFILES_ACTIVE=production
export DB_URL="jdbc:postgresql://<neon-pooler-host>/<database>?sslmode=require"
export DB_USERNAME="<username>"
export DB_PASSWORD="<password>"
mvn spring-boot:run
```

**Windows PowerShell:**
```powershell
$env:SPRING_PROFILES_ACTIVE="production"
$env:DB_URL="jdbc:postgresql://<neon-pooler-host>/<database>?sslmode=require"
$env:DB_USERNAME="<username>"
$env:DB_PASSWORD="<password>"
mvn spring-boot:run
```

> **Important:** Never commit `.env` files with real credentials. Only `.env.example` with placeholders is committed.

## Profiles

| Profile | Description | Database |
|---------|-------------|----------|
| `local` | Local development | Docker PostgreSQL on localhost:5432 |
| `production` | Cloud / staging / CI | Neon PostgreSQL via environment variables |

## Health Endpoints

| Endpoint | Purpose | DB Access |
|----------|---------|----------|
| `GET /api/public/health/live` | Liveness — process is running | No |
| `GET /api/public/health/ready` | Readiness — DB connected | Yes (`SELECT 1`) |

Example response:
```json
{
  "success": true,
  "message": "MoneyFlow backend is ready",
  "data": {
    "application": "UP",
    "database": "UP"
  }
}
```

## Database Migrations

Flyway manages all schema changes. Migration files are in:
```
src/main/resources/db/migration/
```

Migrations run automatically on application startup. Hibernate is set to `validate` mode — it verifies entities match the schema but never modifies tables.

## Running Tests

```bash
mvn clean test
```

## Project Structure

```
src/main/java/com/moneyflowbackend/
├── MoneyflowBackendApplication.java
├── config/
│   └── SecurityConfig.java
├── controller/
│   └── HealthController.java
└── dto/
    └── ApiResponse.java

src/main/resources/
├── application.yml              # Shared config (env vars)
├── application-local.yml        # Local Docker PostgreSQL
├── application-production.yml   # Neon PostgreSQL
└── db/migration/
    └── V1__create_core_schema.sql
```
