# Planning Backend Audit

Status: Historical P13A audit baseline. P13G later locked Planning 2.1.6 backend/API scope as complete.

## Current Model Map

Planning already has a small backend foundation:

- `PlanningController`
  - `GET /api/workspaces/{workspaceId}/planning/actually-spendable`
  - `GET /api/workspaces/{workspaceId}/planning/preferences`
  - `PUT /api/workspaces/{workspaceId}/planning/preferences`
- `PlanningService`
  - calculates current actually-spendable from selected wallets, active reserves, and pending payable recurring obligations
  - uses workspace timezone for current-month ranges
  - returns warnings, assumptions, formula, breakdown, and data freshness
- `PlanningPreference`
  - one row per workspace
  - default horizon, custom dates, included-wallet behavior, selected wallet ids

At P13A time, Planning did not yet have first-class `PlannedObligation`, `ReserveAllocation`, or `PlanningProjection` models under a dedicated Planning v1 contract. P13B through P13F implemented those backend foundations.

## Planning-Adjacent Models Found

### Wallets and Ledger Balance

- `Wallet` stores workspace, name, type, opening balance/date, active/default flags, and include-in-total.
- `WalletBalanceService` derives balance from opening balance plus posted, non-deleted, wallet-affecting ledger movements.
- `WalletBalanceSnapshot` stores manual/daily-closing reconciliation evidence, not projected money.

Reliable today:

- current active wallet ledger balance
- selected-wallet balance
- end-of-day balance

Gaps:

- no projection-specific wallet balance table
- no reserved-by-wallet allocation contract shared across all reserve modules

### Recurring Obligations

- `RecurringObligationTemplate` supports payable/receivable, fixed/variable amount, frequency, interval, start/end date, reminder days, default wallet/category, spending scope, note, and status.
- `ObligationOccurrence` supports due date, reminder date, expected/actual amount, pending/confirmed/skipped/cancelled status, snooze, linked transaction, completed/skipped timestamps.
- Controllers exist for template CRUD, preview, pause/resume/archive, occurrence history, skip, snooze, reopen, and confirm.

Reliable today:

- upcoming payable obligations within a range
- receivable obligations as informational future income
- linked transaction on confirmed occurrence
- recurrence generation by finite horizon

Gaps:

- obligation model is recurring-first, not one-off planned obligation first
- no `PLANNED/DUE_SOON/OVERDUE/PAID` names; current equivalent is occurrence due date plus `PENDING/CONFIRMED`
- variable amount occurrences may have no `expectedAmount`, so they cannot be safely subtracted

### Debts

- `Debt` stores workspace id, counterparty, direction `RECEIVABLE/PAYABLE`, principal, opened date, optional due date, closed date, status, note, and origin transaction id.
- `DebtPayment` stores debt id, optional transaction id, amount, payment date, and note.
- `DebtController` supports list, create, record payment, payment history, summary, and by-person summary.

Reliable today:

- payable debt due dates can exist
- remaining payable/receivable can be calculated
- debt payments can be linked to transactions if provided

Gaps:

- current `PlanningService` explicitly excludes standalone payable debts from actually-spendable
- no repository/service contract for "payable debts due in horizon"
- debt statuses are strings, not enum-protected in the entity

### Reserves and Goals

Existing reserve-like sources:

- `SinkingFund` + `SinkingFundAllocation`
- `SavingsGoal` + `SavingsGoalLedgerEntry`
- `EmergencyFundPlan` + `EmergencyFundLedgerEntry`

All three expose active reserved amount queries used by Planning and Financial Insight:

- `SinkingFundAllocationRepository.sumActiveWorkspaceReservedAmount`
- `SavingsGoalLedgerEntryRepository.sumActiveWorkspaceReservedAmount`
- `EmergencyFundLedgerEntryRepository.sumActiveWorkspaceReservedAmount`

Reliable today:

- active reserved amount by module
- allocation/release ledger entries in each module
- workspace-scoped reserve aggregates

Gaps:

- no single planning reserve allocation model
- no cross-module deduplication between wallets and reserve ledgers
- sinking fund allocations allow `ADJUST`, which needs careful interpretation in projection
- no direct jar/category reserve allocation API

### Jars and Categories

- Category belongs to Jar.
- Jar expresses purpose/budget bucket, not physical money location.
- Existing dashboard/insight code can aggregate expense by jar/category.

Reliable today:

- transaction classification by category/jar
- jar/category spending summaries

Gaps:

- no jar budget table that reserves money
- jar target percentages are not the same as locked money
- no direct "reserve this amount for jar/category" model

## Current APIs Found

Planning:

- `GET /api/workspaces/{workspaceId}/planning/actually-spendable`
- `GET /api/workspaces/{workspaceId}/planning/preferences`
- `PUT /api/workspaces/{workspaceId}/planning/preferences`

Recurring obligation:

- `GET/POST/PUT /api/workspaces/{workspaceId}/recurring-obligations`
- `POST /pause`, `/resume`, `/archive`, `/preview`
- occurrence history, skip, snooze, reopen, confirm

Reserves/goals:

- `/sinking-funds` with allocation history and allocation endpoint
- `/savings-goals` with contribution/release ledger
- `/emergency-fund` with allocation/release ledger

Debt:

- `/debts`
- `/debts/{debtId}/payments`
- `/debts/summary`
- `/debts/by-person`

Financial Insight:

- `/insights/actually-spendable` wraps the Insight spendable calculation
- `/insights/action-items` already reports planning/data-quality warnings

All inspected controller/service paths enforce workspace membership or writable membership before returning or mutating workspace data.

## Current Calculations

Wallet balance:

- starts from wallet opening balance
- adds posted, non-deleted, `affectsWalletBalance=true` transaction deltas
- includes loan/debt movement types for wallet balance only
- includes transfer in/out through `TransferDetail`
- excludes drafts, planned, void, deleted, and historical non-wallet-affecting rows

Actually spendable today:

`availableLedger - activeReserves - knownPendingPayableObligations`

Financial Insight P12D uses a stricter snapshot:

`availableLedgerBalance - activeReserveAmount - upcomingRequiredOutflowAmount - overdueRequiredOutflowAmount`

Expected incoming is shown separately and not counted as spendable.

## Gap Summary

- No dedicated one-off planned obligation model.
- No dedicated Planning v1 reserve allocation model across wallet/jar/category.
- No planning projection API at `/planning/overview`.
- Standalone payable debts are not included in current planning formula.
- Variable recurring obligations without expected amount are excluded with warnings.
- Expected income/receivables are not spendable and need explicit UI copy.
- No notification/scheduler work should be assumed.
- No fake future income source exists and none should be added.
- No Planning v1 API for warnings/action items beyond current actually-spendable response.

## Post-P13G Status

P13B through P13F supersede several P13A gaps:

- Planned obligation model/API exists.
- Reserve allocation model/API exists.
- Planning projection service exists.
- Planning overview, projection, obligation summary, reserve summary, warnings, and action items APIs exist.
- Mark-paid and link-transaction behavior exists.

Remaining backend limitations:

- Standalone payable debts are not included in the current planning formula.
- Recurring obligation generation remains deferred.
- Notification/reminder system remains deferred.
- AI planning summary remains deferred.
- Undo/reopen paid obligation remains deferred.
- Reserve usage workflow remains deferred.
- Multi-currency projection is unsupported and returned with warnings.

## Main Risks

- Double-counting planned expense and actual posted expense.
- Counting expected income or receivables as spendable before collection.
- Treating debt repayment as normal expense instead of debt movement.
- Treating reserve allocation as money creation/destruction.
- Overwriting ledger balance with projection.
- Cross-workspace leakage from mixed repository patterns.
- Building a large planning system before locking minimal ledger-grounded primitives.

## Reusable Patterns

- Use `WorkspaceService.verifyMembership` for read and `requireWritableMember` for writes.
- Keep planning read models deterministic and evidence-backed like Financial Insight.
- Reuse `WalletBalanceService` for available ledger.
- Reuse active reserve aggregate repository methods until a unified reserve model exists.
- Reuse obligation occurrence queries for upcoming/overdue required outflows.
- Keep no-wallet income and expected income informational only.
