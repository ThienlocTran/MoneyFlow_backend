# AGENTS.md

## MoneyFlow Product Source Of Truth Guardrail

Before any MoneyFlow task, read:
- `docs/product/MONEYFLOW_PRODUCT_SOURCE_OF_TRUTH.md`
- `docs/product/MONEYFLOW_TASK_GUARDRAIL.md`

Every task must state:
- Which product principle it affects
- Which domain object it touches
- What data integrity rule applies
- Whether it creates real transactions or only drafts/plans
- Whether user confirmation is required

Hard guardrails:
- Jar is not wallet/category.
- Category belongs to jar.
- Wallet balance must not be recalculated from historical Excel imports unless explicitly approved.
- Recurring fixed commitment must not auto-create transaction without user confirmation.
- Debt movement must not become normal income/expense.
- UI must answer: what is this number, where did it come from, what should user do next.
- No fake/mock/sample/demo runtime data.

## Agent Operating Guide (Backend)

Repo: `moneyflow-backend` (Java 21, Spring Boot 4.1, JPA/Hibernate, Flyway, PostgreSQL/Neon, Maven).

### Product in one paragraph

MoneyFlow is a smart manual finance assistant for lazy users. It is a manual ledger, not a bank
sync product: balances come from opening balances, user-entered transactions, confirmed
quick/voice entries, internal transfers, migration records and wallet snapshots. The loop is
capture → review draft → confirm → daily closing reconciles reality → planning explains what is
actually spendable. API responses must stay explainable: labels, reasons, warnings and exclusions
belong in the response so the UI never has to invent financial meaning.

### Domain rules the API must enforce

- Wallet = where money is stored. Income Source = where money came from. Separate concepts.
- Transfers are neither income nor expense.
- Debt movements are not everyday income/expense: `LOAN_DISBURSEMENT`, `LOAN_COLLECTION`,
  `BORROWING_RECEIPT`, `BORROWING_REPAYMENT` move wallet balance and debt balance.
- `DRAFT`, `PLANNED`, `VOID` and soft-deleted transactions never affect live wallet balance.
- Student Loan Simulation is reference-only: no transactions, no wallet mutation, and it is
  excluded from actually-spendable unless the user records a real obligation or debt.
- Historical Excel rows (`is_historical`, `affects_wallet_balance = false`) are analytics-only.
  Never guess a wallet for a historical row.
- Voice record, transcript and stored audio are independent. Audio storage failure must not
  invalidate the transaction or its transcript.
- Wallet balance snapshots are reconciliation data, not income/expense.
- Preview and suggestion endpoints are read-only: no writes, workspace scoped, membership verified.

### Hard rules

- Real UTF-8 Vietnamese in messages and docs. No mojibake.
- Every workspace-scoped query filters by `workspaceId` and verifies membership first.
- No secrets, tokens or environment-specific IDs in code, tests or logs.
- Prefer no DB migration. If unavoidable, get approval first; migrations are forward-only and
  must be safe on existing data.
- Do not modify `pom.xml`, `.env*`, Docker or deploy configuration without asking.
- No LLM/AI calls and no external API keys in suggestion or parsing logic. Deterministic rules only.
- Do not change ledger math or transaction commit logic as a side effect of another task.

### Agent ownership

- **Codex** — backend, API contracts, business logic, backend tests, release.
- **Antigravity** — UI/UX in the frontend repo.
- **Kiro** — review, integration, focused tasks assigned per request.

One task = one owner = one file scope. Stop and report on file conflict.

### Coordination protocol

1. `git status --short` must be clean before starting. If dirty, stop and report.
2. Confirm your file scope before editing. Do not expand scope mid-task.
3. Re-check `git log --oneline -5` when finishing. Unexpected commits inside your file scope mean
   a possible collision: stop and report instead of committing over it.
4. No unrelated auto-fixes and no dependency bumps.
5. No fake validation. Report the exact commands you ran and their result.
6. One logical change per commit, scoped prefix (`feat(suggestions): ...`, `fix(planning): ...`).
7. Never overwrite an existing file with a full rewrite when an append or targeted edit is enough.

### Test scope

Backend work runs targeted Maven tests only:

```
$env:JAVA_HOME="C:\Program Files\Java\jdk-24.0.2"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd "-Dtest=<Pattern>Tests" test
git diff --check
```

Examples: `Planning*Tests`, `TransactionModuleIntegrationTests`, `ActivityTimelineServiceTests`,
`DailyClosingVoicePreviewIntegrationTests`, `*Suggestion*Tests`.

Full regression (`.\mvnw.cmd test`) only before release or when a targeted run fails to compile
for unrelated reasons. Do not run frontend commands from this repo.

Tests run on H2 in `MODE=PostgreSQL` with `ddl-auto: create-drop` and Flyway disabled, so the
test schema does not validate production migrations. Migration changes need separate review.

### Where things live

Package-by-feature under `com.moneyflowbackend`: `auth`, `workspace`, `wallet`, `transaction`
(+`audit`), `category`, `jar`, `income`, `debt`, `obligation`, `savingsgoal`, `sinkingfund`,
`emergencyfund`, `studentloan`, `closing`, `planning`, `dashboard`, `quickentry`, `voice`,
`activity`, `suggestion`, plus `config`, `security`, `common`. Each feature holds its own
`controller`, `service`, `dto`, `model`, `repository`. Flyway migrations live in
`src/main/resources/db/migration`.

### Related docs

- `docs/task-board.md` — who is doing what, and what is still open.
- `docs/test-scope.md` — exact commands per task type.
- `docs/product/` — product source of truth, domain rules, guardrails.
