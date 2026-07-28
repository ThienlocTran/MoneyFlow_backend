# MoneyFlow Test Scope

Goal: run enough to be honest, not everything. Over-testing wastes time and hides real signal.

## Backend-only task

```
$env:JAVA_HOME="C:\Program Files\Java\jdk-24.0.2"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd "-Dtest=<Pattern>Tests" test
git diff --check
```

Pick the narrowest pattern that covers the change:

| Area | Pattern |
| --- | --- |
| Planning / actually spendable | `Planning*Tests` |
| Transactions + audit | `TransactionModuleIntegrationTests` |
| Activity timeline | `ActivityTimelineServiceTests` |
| Daily closing voice preview | `DailyClosingVoicePreviewIntegrationTests` |
| Quick entry suggestions | `*Suggestion*Tests` |
| Voice audio storage | `VoiceAudioServiceTests,CloudinaryVoiceAudioStorageServiceTests` |
| Income source | `IncomeSource*Tests` |
| Student loan | `StudentLoan*Tests` |
| Cross-module money invariants | `CrossModuleFinancialInvariantTests` |
| Migration safety | `MigrationSqlSafetyTests` |

Full regression `.\mvnw.cmd test` only before a release, or when a targeted run fails to compile
because of unrelated modules. Say explicitly which one you ran.

Tests use H2 in `MODE=PostgreSQL` with `ddl-auto: create-drop` and Flyway disabled. Passing tests
therefore do **not** prove that Flyway migrations apply cleanly to PostgreSQL.

## Frontend-only task

```
pnpm run type-check
pnpm run scan:mojibake
git diff --check
pnpm run build      # when types, components or views changed
```

There is no unit test runner configured in the frontend yet. Do not add one as a side effect of
another task.

## Docs-only task

```
git diff --check
```

Plus the mojibake scan when the docs contain Vietnamese:

```
powershell -ExecutionPolicy Bypass -File ..\scripts\scan-mojibake.ps1
```

The scan walks the whole workspace, so running it once covers both repos. Do not run application
tests for a docs-only change.

## Release smoke

- Frontend: type-check, mojibake, `git diff --check`, build, then manual page smoke on
  `/dashboard`, `/financial-inbox`, `/transactions`, `/planning`, `/daily-closing`,
  `/student-loans`, `/help`.
- Backend: the targeted patterns for everything that changed in the wave, plus a security review
  (no env/secret/migration/Docker changes, planning and audit endpoints still authenticated,
  preview endpoints still read-only).

## Honesty rules

- Static review is not browser UAT. If you did not open the page, report UAT as not run.
- Report exact commands and exact results. Never summarise a run you did not perform.
- A green targeted run does not mean the whole system is green; say what you did not cover.
