# MoneyFlow Task Guardrail

Last synced: 2026-07-23.

## Before-Task Checklist

- Read this guardrail and the relevant source-of-truth doc.
- Search before reading broad files.
- Inspect only the files needed for the task.
- State root cause before editing; if unknown, keep investigating.
- Patch minimally and keep existing style.
- Do not touch production config, secrets, DB, Docker, deploy files, package files, migrations, or SQL schema without confirmation.
- Do not run build, dev, test, deploy, import, or repair commands without confirmation.

## Data Safety Checklist

- No fake, mock, sample, random, demo, or fallback financial data in runtime code.
- No hardcoded workspace, user, wallet, category, jar, debt, or production IDs.
- No broad importer, repair, delete, truncate, or overwrite without explicit approval.
- No transaction from summary totals when detailed rows exist.
- No guessed wallet for historical Excel transactions.
- No wallet snapshot converted into income.
- No historical analytics-only transaction replayed into live wallet balance.
- Draft, planned, void, deleted, and historical analytics-only records must not affect live balances.

## DB And Test Datasource Safety Checklist

- Confirm the datasource before any DB write.
- Prefer readonly reports for forensic work.
- Backup before repair.
- Dry-run before insert/update.
- Use duplicate guards and transaction boundaries.
- Validate totals after write.
- Never mutate production DB by accident.
- Tests must not run against Neon production-like DB.
- Never expose secrets from `.env`, application properties, logs, or shell history.

## Frontend And Backend Responsibility Split

- Backend owns money rules, balances, status effects, debt remaining, dashboard totals, validation, audit, idempotency.
- Frontend owns clear forms, previews, empty/error states, route-level flows, confirmation UX, and explaining numbers.
- Frontend must not compute authoritative financial totals when backend provides them.
- Backend must not auto-post user-facing records from ambiguous voice/text/recurring input.

## UTF-8 And Vietnamese Text Rules

- Save source, Markdown, Vue, TypeScript/JavaScript, Java, SQL, JSON, YAML, and reports as real UTF-8.
- Preserve real Vietnamese Unicode.
- Before completion, scan touched text files for mojibake patterns.
- Never report success while touched Vietnamese text is corrupted.

Scan for mojibake codepoints/fragments:

- U+00C3, U+00C4, U+00C6, U+00C2, U+FFFD
- broken fragments of Vietnamese words such as Công, nợ, Tài, Hũ

## No Fake Runtime Data Rule

Runtime UI must use backend APIs. Empty backend data means empty state. Backend failure means error state.

Allowed fake data only in tests, fixtures under test paths, stories if already present, or documentation examples.

## Confirmation-Before-Commit Rules

- Ask before changing protected config/deploy/DB/package/migration files.
- Ask before running build/dev/test/deploy/import/repair commands.
- Ask before committing if the task did not explicitly request commit.
- If commit is requested, stage only the requested files and report the hash.
- Push only to the requested branch/remote; never force push.

## Validation And Reporting Standard

- Run `git diff --check`.
- Run a mojibake scan on touched text files.
- Report exactly what was changed.
- Report duplicate docs merged.
- Report conflicts and facts needing confirmation.
- Report what was not tested.
- Report risks and next step.
