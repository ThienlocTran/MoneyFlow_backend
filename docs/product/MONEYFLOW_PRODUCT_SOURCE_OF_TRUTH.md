# MoneyFlow Product Source Of Truth

Last synced: 2026-07-23.

## Product Purpose

MoneyFlow is a personal and shared-finance web app for fast manual money logging and clear financial visibility. It should answer:

- money came from where;
- money is stored where;
- money went to what purpose;
- what is already reserved;
- what is actually spendable.

MoneyFlow is not a web clone of the old Excel workbook. Excel is historical evidence, migration input, and reconciliation context.

## Current Product Philosophy

- Correctness and traceability beat convenience.
- Draft first, commit after user confirmation.
- A reliable ledger comes before AI, forecasts, or automatic optimization.
- The user wants fewer actions, but financial records must stay explainable.
- Runtime UI uses real backend APIs only. Empty data shows empty state; failed data shows error state.

## Manual Ledger And No Bank Sync Rule

MoneyFlow currently has no bank API, e-wallet API, Open Banking, automatic balance sync, real-time external balances, or automatic money transfers.

Wallet balances come only from MoneyFlow records:

- opening balances;
- posted user-entered transactions;
- Quick Entry or Voice entries after confirmation;
- internal transfers;
- internal migration;
- wallet snapshots or reconciliation records.

## Seven Financial Layers

1. Wallet: where money is stored.
2. Income Source: where money came from.
3. Transaction: what financial event happened.
4. Classification: category, jar, spending scope, person, source.
5. Obligations: what must be paid.
6. Funds and Goals: what money is reserved for.
7. Planning Engine: what remains realistically spendable after reserves.

## Module And Domain Map

- Auth and account: users, login, Google login, refresh tokens, account status.
- Workspace: personal/shared finance spaces, members, invitations, workspace people.
- Wallet: wallets, opening/live balances, wallet snapshots, daily closing.
- Income Source: income origins separate from wallets.
- Transaction: income, expense, transfer, debt movements, audit.
- Category and Jar: purpose classification; category belongs to jar.
- Debt/Công nợ: receivable/payable principal and payments.
- Quick Entry and Voice: parse, preview, confirm, audio evidence.
- Dashboard: totals, wallet balances, categories, jars, comparison.
- Planning: actually spendable, preferences, commitments.
- Recurring Obligations: templates, occurrences, financial inbox.
- Sinking Funds, Emergency Fund, Savings Goals, Student Loans: reserve/goal/payoff planning.
- Activity Timeline: explainable event history.

## Current Route Map

- `/login`, `/register`
- `/dashboard`
- `/transactions`
- `/recurring-obligations`
- `/financial-inbox`
- `/activity-timeline`
- `/savings-goals`
- `/wallets`
- `/daily-closing`
- `/debts`
- `/sinking-funds`
- `/categories`
- `/income-sources`
- `/student-loans`
- `/emergency-fund`
- `/planning`
- `/workspaces`
- `/settings`

## Current Backend Module Map

- `/api/public/health`
- `/api/public/auth/*`, `/api/auth/logout`, `/api/me`, `/api/users/me/*`
- `/api/workspaces`
- `/api/workspaces/{workspaceId}/wallets`
- `/api/workspaces/{workspaceId}/transactions`
- `/api/transactions/{transactionId}/audit`
- `/api/workspaces/{workspaceId}/categories`
- `/api/workspaces/{workspaceId}/categories/{categoryId}/keywords`
- `/api/workspaces/{workspaceId}/jars`
- `/api/workspaces/{workspaceId}/income-sources`
- `/api/workspaces/{workspaceId}/debts`
- `/api/workspaces/{workspaceId}/dashboard`
- `/api/workspaces/{workspaceId}/quick-entry`
- `/api/voice-records`
- `/api/workspaces/{workspaceId}/daily-closings/*`
- `/api/workspaces/{workspaceId}/wallet-snapshots`
- `/api/workspaces/{workspaceId}/recurring-obligations`
- `/api/workspaces/{workspaceId}/obligation-occurrences/*`
- `/api/workspaces/{workspaceId}/financial-inbox`
- `/api/workspaces/{workspaceId}/planning/*`
- `/api/workspaces/{workspaceId}/savings-goals`
- `/api/workspaces/{workspaceId}/sinking-funds`
- `/api/workspaces/{workspaceId}/student-loans`
- `/api/workspaces/{workspaceId}/emergency-fund`
- `/api/workspaces/{workspaceId}/activity-timeline`

## Source-Of-Truth Hierarchy

1. Current backend business rules and database model.
2. Current frontend router, stores, services, and visible UI behavior.
3. Latest readonly reports and migration evidence.
4. Migration docs and ERD.
5. Product context and master plan.
6. Excel workbook detailed rows and formulas as evidence, not app code.
7. Older handoff/audit docs only when they do not conflict with newer code/reports.

## What Must Not Be Guessed

- wallet for historical rows when Excel did not record it;
- whether a draft/planned/void/deleted record affects balances;
- whether a debt movement is normal income/expense;
- production DB state from stale reports;
- cutover date or opening balances;
- user intent from a recognized voice phrase when required fields are missing;
- private finance facts not present in local evidence.

## Conflicts

- CONFLICT: older UI blocker docs said Debt had no frontend route. Current router has `/debts`; backend has `/api/workspaces/{workspaceId}/debts`.
- NEEDS USER CONFIRMATION: official cutover date and final opening balances.
- NEEDS USER CONFIRMATION: six jar names/percentages are a starting template, not necessarily fixed forever.

## Voice Phase 2 And Debt Repair Preparation

- Voice Phase 2 must extend draft review first, not broaden auto-commit.
- Current voice batch commit safely supports only `INCOME`, `EXPENSE`, and `TRANSFER`.
- Debt, savings, funds, wallet snapshot, and recurring intents stay draft-only until module-specific backend commit contracts are implemented and tested.
- Debt repair work must remain forensic-first and write-free until backup, dry-run, duplicate guard, transactional insert plan, and validation are approved.
