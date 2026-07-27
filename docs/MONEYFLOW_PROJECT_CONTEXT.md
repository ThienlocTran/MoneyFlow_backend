# MoneyFlow Project Context

Last consolidated: 2026-07-23.

This file is the working project context for future agents. It summarizes product intent and current code. It does not replace source code, migrations, or incident evidence.

## Product Purpose

MoneyFlow is a personal and shared finance app for fast manual money tracking. It is not a bank-sync product. Its core job is to help the user:

- record income, expenses, transfers, debt movements, and adjustments;
- know where money is stored by wallet;
- know where money came from by income source;
- classify spending by category, jar, and spending scope;
- track debts, obligations, savings, funds, and emergency reserves;
- see current ledger balances, reserved money, upcoming commitments, and actually spendable money;
- migrate from the existing Excel workflow without losing financial history.

Product principle: financial correctness and traceability beat convenience. Quick text and voice must draft first, then commit only after confirmation.

## Main User Workflows

- Register/login, complete username, choose personal/shared workspace.
- Manage wallets and wallet balances.
- Manage jars, categories, category keywords, and quick actions.
- Add transactions manually, by quick button, by quick text, or by voice confirmation.
- Review dashboard, monthly jar summary, category summary, wallet summary, and activity timeline.
- Record debt/công nợ principal and payments.
- Manage income sources and link posted income/expense transactions to sources.
- Manage recurring obligations, occurrence inbox, skips, snoozes, reopen, and confirm-to-transaction.
- Manage savings goals, sinking funds, emergency fund, and student loans.
- Review planning/actually-spendable calculation.
- Run manual wallet closing/reconciliation.
- Import or reconcile legacy Excel data only through explicit backup, dry-run, and approval.

## Current Modules

- Auth and Google auth account linking.
- Workspace, member, invitation, role, and workspace people.
- Wallets and wallet balance snapshots.
- Jars, categories, category keywords.
- Transactions, transfer details, transaction audit logs, CSV export.
- Quick Entry parser, quick buttons, voice single confirm, voice batch confirm.
- Voice record audio storage lifecycle.
- Dashboard and activity timeline.
- Daily closing and wallet reconciliation.
- Debts and debt payments.
- Income sources.
- Recurring obligations and financial inbox.
- Spending scope.
- Sinking funds.
- Student loans.
- Savings goals.
- Emergency fund.
- Planning preferences and actually-spendable calculation.

## Route Map

Frontend routes in `moneyflow-ui/moneyflow-ui/src/router/index.ts`:

| Route | View |
| --- | --- |
| `/login` | `LoginView` |
| `/register` | `RegisterView` |
| `/dashboard` | `DashboardView` |
| `/transactions` | `TransactionsView` |
| `/recurring-obligations` | `RecurringObligationsView` |
| `/financial-inbox` | `FinancialInboxView` |
| `/activity-timeline` | `ActivityTimelineView` |
| `/savings-goals` | `SavingsGoalsView` |
| `/wallets` | `WalletsView` |
| `/daily-closing` | `DailyClosingView` |
| `/debts` | `DebtsView` |
| `/sinking-funds` | `SinkingFundsView` |
| `/categories` | `CategoriesView` |
| `/income-sources` | `IncomeSourcesView` |
| `/student-loans` | `StudentLoansView` |
| `/emergency-fund` | `EmergencyFundView` |
| `/planning` | `PlanningView` |
| `/workspaces` | `SharedWorkspacesView` |
| `/settings` | `SettingsView` |

Auth guard: protected routes require login. Users without username are redirected to settings except while already on settings.

## Backend Domain Map

Primary API bases:

- `/api/public/health`
- `/api/public/auth/*`, `/api/auth/logout`, `/api/me`, `/api/users/me`, `/api/me/auth-accounts`
- `/api/me/avatar`
- `/api/workspaces`
- `/api/users/search`
- `/api/workspaces/{workspaceId}/wallets`
- `/api/workspaces/{workspaceId}/jars`
- `/api/workspaces/{workspaceId}/categories`
- `/api/workspaces/{workspaceId}/categories/{categoryId}/keywords`
- `/api/workspaces/{workspaceId}/transactions`
- `/api/transactions/{transactionId}/audit`
- `/api/workspaces/{workspaceId}/quick-entry`
- `/api/voice-records/{voiceRecordId}/*`
- `/api/workspaces/{workspaceId}/dashboard`
- `/api/workspaces/{workspaceId}/activity-timeline`
- `/api/workspaces/{workspaceId}/daily-closings/*`
- `/api/workspaces/{workspaceId}/wallet-snapshots`
- `/api/workspaces/{workspaceId}/debts`
- `/api/workspaces/{workspaceId}/income-sources`
- `/api/workspaces/{workspaceId}/recurring-obligations`
- `/api/workspaces/{workspaceId}/financial-inbox`
- `/api/workspaces/{workspaceId}/obligation-occurrences/*`
- `/api/workspaces/{workspaceId}/savings-goals`
- `/api/workspaces/{workspaceId}/sinking-funds`
- `/api/workspaces/{workspaceId}/student-loans`
- `/api/workspaces/{workspaceId}/emergency-fund`
- `/api/workspaces/{workspaceId}/planning`

Authorization pattern: read routes verify active workspace membership. Writes require OWNER or EDITOR unless a controller/service documents stricter behavior.

## Frontend View Map

- `DashboardView`: dashboard cards, recent transactions, linked filters.
- `TransactionsView`: transaction list, filters, create/edit/delete/restore, income source linking.
- `WalletsView`: wallets and wallet summary.
- `CategoriesView`: jars, categories, archive/status/quick action, keywords.
- `DebtsView`: debt list, debt summary, by-person view, create debt, record payment, payment history.
- `IncomeSourcesView`: source list, summaries, archive/restore.
- `RecurringObligationsView`: template management and preview.
- `FinancialInboxView`: occurrence inbox and confirm/skip/snooze/reopen workflows.
- `ActivityTimelineView`: unified timeline.
- `SavingsGoalsView`, `SinkingFundsView`, `StudentLoansView`, `EmergencyFundView`, `PlanningView`: planning and reserved-money modules.
- `DailyClosingView`: wallet closing and snapshot reconciliation.
- `SharedWorkspacesView`, `SettingsView`, `LoginView`, `RegisterView`: account/workspace support.

## Database And Domain Entities Summary

Core tables from migrations:

- Identity/workspaces: `users`, `auth_accounts`, `refresh_tokens`, `workspaces`, `workspace_members`, `workspace_people`, `workspace_invitations`.
- Classification: `jars`, `categories`, `category_keywords`.
- Wallet ledger: `wallets`, `wallet_balance_snapshots`, `daily_closings`.
- Transactions: `transactions`, `transfer_details`, `transaction_audit_logs`.
- Voice: `voice_records`.
- Debt: `debts`, `debt_payments`.
- Migration audit scaffolding: `migration_runs`, `migration_items`, `flyway_schema_history`.
- Recurring obligations: `recurring_obligation_templates`, `obligation_occurrences`.
- Income sources: `income_sources`.
- Reserved money and planning: `sinking_funds`, `sinking_fund_allocations`, `student_loans`, `savings_goals`, `savings_goal_ledger_entries`, `emergency_fund_plans`, `emergency_fund_ledger_entries`, `planning_preferences`, `planning_preference_wallet_ids`.

Important flags:

- `transactions.amount` is positive.
- `transactions.transaction_status`: `DRAFT`, `PLANNED`, `POSTED`, `VOID`.
- `transactions.source_type`: `MANUAL`, `QUICK_BUTTON`, `QUICK_TEXT`, `VOICE`, `EXCEL_MIGRATION`, `SYSTEM`.
- Historical imports may have `is_historical=true` and `affects_wallet_balance=false`.
- Live/manual/quick/voice transactions must affect wallet balance.
- Debts do not have a soft-delete column as of 2026-07-23.

## Current Known Dev Heads

As of this consolidation:

- Backend `dev`: `4be34f89b6a5efbe249458650c0eaf154a2cb382` (`fix emergency fund unconfigured state`)
- Frontend `dev`: `3e4fddf05f56df4a366baaa18113f121dba02c3b` (`fix emergency fund loading watcher`)

Re-check heads before starting future work.

## Active Risks

- Debt data incident from 2026-07-23: Excel unpaid receivable total is `33,009,000`; DB emergency audit showed `/debts` remaining `15,115,000`; missing Excel open rows total `17,894,000`.
- Existing older product docs contain useful facts but some files render mojibake. Preserve real UTF-8 in new/touched docs and UI strings.
- Do not use broad importers or repair scripts without backup, dry-run, exact row list, and explicit approval.
- Do not add fake financial runtime data. Empty backend data must show empty state; backend failure must show error state.
- Planning formula warns about possible overlap between reserve ledgers because the app does not infer money movement between wallets/reserve modules.
- Student loan advisory commitments are intentionally not included in actually-spendable until a confirmed loan-obligation deduplication contract exists.
- Backend repo may have unrelated untracked reports from audits; do not delete or commit incident evidence accidentally.

## Manual Smoke Checklist

- Login/register; username completion redirects correctly.
- Workspace selector loads; viewer cannot write; owner/editor can write.
- Dashboard loads for active workspace with no console errors.
- Wallet list/summary loads; wallet create/status/default flows work.
- Transaction list filters by date/type/status/wallet/category; create expense/income/transfer; delete/restore.
- Quick text parses to draft; missing amount/category/wallet requires review; confirm creates one transaction.
- Voice parse returns candidates; unsupported intents are visible as unsupported; confirm with same idempotency key does not duplicate.
- `/debts` loads; unconfigured/empty state is honest; create debt; record payment; summary updates; paid debt remaining is zero in API.
- Emergency fund unconfigured workspace returns success with null data, not a 404 loop; real workspace/auth errors still show as errors.
- Recurring obligation creates template; inbox occurrence confirm creates transaction only after confirmation.
- Savings goal/sinking fund/emergency fund allocation/release updates reserved totals.
- Planning actually-spendable loads and shows assumptions/warnings.
- Daily closing loads with current wallets and records adjustment only after user action.
- No mojibake in Vietnamese UI text.

## Vague Or Conflicting Inputs

- Older `docs/product/MONEYFLOW_PRODUCT_SOURCE_OF_TRUTH.md` states some modules were "later scope" or "not seen" at the time it was written. Code now contains income sources, recurring obligations, sinking funds, savings goals, student loans, emergency fund, spending scope, and planning. Treat code/migrations as current.
- Older docs use the term "Jar/Fund" broadly. Current code separates jars, savings goals, sinking funds, and emergency fund. Do not merge these concepts without user confirmation.
- Root project context mentions owner-specific private financial details. Keep public repo docs factual and avoid private personal details beyond approved incident numbers.
