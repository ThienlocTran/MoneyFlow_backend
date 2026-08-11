# Planning Backend Contract V1

Status: COMPLETE / BACKEND LOCKED for 2.1.6 backend/API scope. Frontend UI remains deferred.

## Goals

Planning Backend v1 answers:

- what money is available now
- what required money is due soon or overdue
- what money is intentionally reserved
- what income is expected but not spendable yet
- whether the user is projected to fall short

Planning must be deterministic, workspace-scoped, ledger-grounded, and read/write explicit.

## Core Concepts

### PlannedObligation

A known future or overdue required outflow.

Examples:

- rent
- electricity, water, internet
- tuition
- debt repayment
- subscription
- required family payment

Proposed fields:

- `id`
- `workspaceId`
- `name`
- `amount`
- `currency`
- `dueDate`
- `status`: `PLANNED`, `DUE_SOON`, `OVERDUE`, `PAID`, `CANCELLED`
- `priority`: `REQUIRED`, `IMPORTANT`, `OPTIONAL`
- `walletId` optional
- `categoryId` optional
- `jarId` optional if the project later supports direct jar relation
- `recurrenceRule` optional later
- `linkedTransactionId` nullable
- `note`
- `createdAt`
- `updatedAt`

First implementation should reuse or wrap existing recurring obligation occurrence data where possible. A separate one-off planned obligation table belongs in P13B only if recurring obligations cannot model one-off required outflows cleanly.

### ReserveAllocation

Money intentionally held aside. Reserve allocation does not create or destroy money.

Examples:

- emergency fund
- rent reserve
- savings goal
- sinking fund
- envelope/jar reserve

Proposed fields:

- `id`
- `workspaceId`
- `name`
- `amount`
- `currency`
- `walletId` optional
- `jarId` optional
- `categoryId` optional
- `status`: `ACTIVE`, `RELEASED`, `USED`, `CANCELLED`
- `targetDate` optional
- `note`
- `createdAt`
- `updatedAt`

P13C adds a thin planning reserve table for explicit locked money. Existing sinking fund, savings goal, and emergency fund ledgers remain separate until projection unifies them.

### PlanningProjection

A read model/calculation. It does not need a table in v1.

Fields:

- `asOfDate`
- `horizonDays`
- `availableLedgerBalance`
- `activeReserveAmount`
- `upcomingRequiredOutflowAmount`
- `overdueRequiredOutflowAmount`
- `expectedIncomingAmount`
- `actuallySpendable`
- `projectedShortfall`
- `warnings`

Formula:

`actuallySpendable = availableLedgerBalance - activeReserveAmount - upcomingRequiredOutflowAmount - overdueRequiredOutflowAmount`

`projectedShortfall = max(0, -actuallySpendable)`

P13D implementation source rules:

- `availableLedgerBalance`: active include-in-total wallets via `WalletBalanceService`
- `activeReserveAmount`: P13C `reserve_allocations` with status `ACTIVE`
- `upcomingRequiredOutflowAmount`: P13B planned obligations with status `PLANNED`, priority `REQUIRED` or `IMPORTANT`, due from `asOfDate` through `asOfDate + horizonDays`
- `overdueRequiredOutflowAmount`: P13B planned obligations with status `PLANNED`, priority `REQUIRED` or `IMPORTANT`, due before `asOfDate`
- `expectedIncomingAmount`: `0` in P13D because no safe planned-income source is wired
- no-wallet income stays excluded from ledger through `WalletBalanceService`

Expected incoming is informational and must not increase actually spendable.

## Proposed Endpoints

Base path: `/api/workspaces/{workspaceId}/planning`

### Overview

`GET /overview`

Query:

- `asOfDate` optional, `YYYY-MM-DD`
- `horizonDays` optional, default `30`

Returns:

- projection
- upcoming obligations
- active reserves
- warnings
- action items

P13F implements:

- `GET /overview`
- `GET /projection`
- `GET /obligations/summary`
- `GET /reserves/summary`

Query defaults:

- `asOfDate`: current date when omitted
- `horizonDays`: default `30`, valid `1..365`
- `includeInactive`: default `false`

Overview response includes projection, obligation summary, reserve summary, upcoming/overdue obligations, active reserves, planning action items, and warnings.

### Obligations

`GET /obligations`

Query:

- `from` optional, `YYYY-MM-DD`
- `to` optional, `YYYY-MM-DD`
- `status` optional
- `priority` optional
- `includeCancelled` optional, default `false`

`POST /obligations`

Creates a planned obligation. Must not create a transaction or affect wallet balance.

`PATCH /obligations/{obligationId}`

Updates planned obligation fields. Must not mutate linked transaction data. Cancelled obligations cannot be updated.

`GET /obligations/{obligationId}`

Returns one planned obligation in the workspace.

`POST /obligations/{obligationId}/cancel`

Sets status to `CANCELLED`. Second cancel is idempotent. Paid reversal is deferred.

`POST /obligations/{obligationId}/mark-paid`

Later behavior:

- if linking an existing posted transaction, set status `PAID` and `linkedTransactionId`
- if creating a transaction, route through normal transaction service by explicit user action
- no auto-posting from due date alone

Status: implemented in P13E.

P13E implemented endpoints:

- `POST /obligations/{obligationId}/link-transaction`
- `POST /obligations/{obligationId}/mark-paid`

Link existing transaction:

- requires a same-workspace posted, non-deleted `EXPENSE` transaction
- transaction amount and currency must match the obligation
- marks obligation `PAID`
- sets `linkedTransactionId`, `paidAt`, and optional `paidNote`
- creates no transaction
- repeated link with the same transaction returns the existing paid obligation
- linking a different transaction after paid is blocked

Mark paid:

- requires a same-workspace wallet and category
- creates a normal posted `EXPENSE` through `TransactionService`
- links the created transaction to the obligation
- repeated mark-paid returns the existing linked transaction and creates no duplicate
- cancelled obligations cannot be paid

Projection effect:

- `PAID` obligations are excluded by P13D projection because projection only includes `PLANNED`.

No auto-pay:

- overdue obligations stay `PLANNED` until explicit link/mark-paid.

### Reserves

`GET /reserves`

Lists active and historical reserve allocations from Planning v1 or existing reserve modules.

`POST /reserves`

Creates reserve allocation. Must not create income or expense.

`PATCH /reserves/{reserveId}`

Updates reserve metadata/status.

`POST /reserves/{reserveId}/release`

Releases reserve. It increases actually spendable but does not create income.

`POST /reserves/{reserveId}/cancel`

Cancels active reserve. Second cancel is idempotent. Released reserve cancellation is deferred.

## Response DTO Proposal

`PlanningOverviewResponse`:

- `workspaceId`
- `asOfDate`
- `horizonDays`
- `projection`
- `upcomingObligations`
- `activeReserves`
- `warnings`
- `actionItems`
- `generatedAt`

`PlanningProjectionResponse`:

- `availableLedgerBalance`
- `activeReserveAmount`
- `upcomingRequiredOutflowAmount`
- `overdueRequiredOutflowAmount`
- `expectedIncomingAmount`
- `actuallySpendable`
- `projectedShortfall`
- `currency`

`PlannedObligationResponse`:

- `id`
- `workspaceId`
- `name`
- `amount`
- `currency`
- `dueDate`
- `status`
- `computedState`
- `priority`
- `walletId`, `walletName`
- `categoryId`, `categoryName`
- `jarId`, `jarName` derived through category
- `note`
- `recurrenceType`
- `linkedTransactionId`
- `createdAt`
- `updatedAt`

P13B stores `recurrenceType` but does not generate recurring rows.

Computed state:

- `PAID` if status is `PAID`
- `CANCELLED` if status is `CANCELLED`
- `OVERDUE` if status is `PLANNED` and due date is before today
- `DUE_SOON` if due date is today through 7 days from today
- `UPCOMING` otherwise

`ReserveAllocationResponse`:

- `id`
- `workspaceId`
- `name`
- `amount`
- `currency`
- `status`: `ACTIVE`, `RELEASED`, `CANCELLED`
- `purposeType`: `EMERGENCY_FUND`, `RENT`, `BILL`, `GOAL`, `CATEGORY_JAR`, `DEBT_PAYMENT`, `CUSTOM`
- `walletId`, `walletName`
- `categoryId`, `categoryName`
- `jarId`, `jarName`
- `targetDate`
- `note`
- `warnings`
- `releasedAt`
- `cancelledAt`
- `createdAt`
- `updatedAt`

## Business Rules

- Planned obligation does not affect wallet balance until paid as an actual transaction.
- Planned obligation reduces projection and actually spendable.
- Reserve allocation does not create or destroy money.
- Reserve release increases actually spendable but does not create income.
- Expected income does not increase actually spendable.
- Receivable debt is not spendable until collected into a wallet.
- Payable debt due within horizon should reduce projection once the debt-planning contract is implemented.
- Linked transaction must belong to the same workspace.
- Transfers do not create money.
- Wallet snapshots are reconciliation evidence, not income.
- Drafts do not affect planning until confirmed if current product rules say so.
- Historical rows with `affectsWalletBalance=false` do not affect wallet balance or spendable money.
- Recurrence generation must be bounded by a clear horizon.

## P13B Implemented Foundation

Table: `planned_obligations`

Fields implemented:

- workspace/user references
- name, amount, currency, due date
- status: `PLANNED`, `PAID`, `CANCELLED`
- priority: `REQUIRED`, `IMPORTANT`, `OPTIONAL`
- recurrence type: `NONE`, `WEEKLY`, `MONTHLY`, `YEARLY`
- optional wallet/category references
- optional linked transaction reference
- paid timestamp and paid note
- note, cancel reason, cancelled timestamp, timestamps, soft-delete column, version

Validation:

- name required, trimmed, max 120
- amount required and greater than zero
- currency defaults to `VND`, must be 3 uppercase letters
- due date required
- priority defaults to `REQUIRED`
- recurrence type defaults to `NONE`
- wallet/category must belong to the same workspace
- cancelled obligations cannot be patched
- paid obligations cannot be cancelled in P13B

Ledger rule:

- create/update/cancel planned obligation does not create or mutate transactions
- wallet balance is unchanged by planned obligation CRUD

## P13C Implemented Foundation

Table: `reserve_allocations`

Fields implemented:

- workspace/user references
- name, amount, currency
- status: `ACTIVE`, `RELEASED`, `CANCELLED`
- purpose type: `EMERGENCY_FUND`, `RENT`, `BILL`, `GOAL`, `CATEGORY_JAR`, `DEBT_PAYMENT`, `CUSTOM`
- optional wallet/category/jar references
- optional target date
- note, released/cancelled timestamps, timestamps, soft-delete column, version

Endpoints:

- `POST /api/workspaces/{workspaceId}/planning/reserves`
- `GET /api/workspaces/{workspaceId}/planning/reserves`
- `GET /api/workspaces/{workspaceId}/planning/reserves/{reserveId}`
- `PATCH /api/workspaces/{workspaceId}/planning/reserves/{reserveId}`
- `POST /api/workspaces/{workspaceId}/planning/reserves/{reserveId}/release`
- `POST /api/workspaces/{workspaceId}/planning/reserves/{reserveId}/cancel`

Validation:

- name required, trimmed, max 120
- amount required and greater than zero
- currency defaults to `VND`, must be 3 uppercase letters
- purpose type defaults to `CUSTOM`
- wallet/category/jar must belong to the same workspace
- released/cancelled reserves cannot be patched
- cancelled reserves cannot be released
- released reserves cannot be cancelled

Available balance behavior:

- no reliable reserve-specific available-balance source is used in P13C
- create/update returns `RESERVE_BALANCE_CHECK_UNAVAILABLE`
- P13C does not invent balance math from expected income, receivables, or non-wallet rows

Ledger rule:

- create/update/release/cancel reserve does not create or mutate transactions
- reserve does not move money between wallets
- release does not create income
- wallet balance is unchanged by reserve CRUD

## P13D Implemented Foundation

Service: `PlanningProjectionService`

DTOs:

- `PlanningProjectionSnapshot`
- `PlanningProjectionBreakdownItem`
- `PlanningProjectionWarning`

Formula:

`actuallySpendable = availableLedgerBalance - activeReserveAmount - upcomingRequiredOutflowAmount - overdueRequiredOutflowAmount`

`projectedShortfall = max(0, -actuallySpendable)`

Data sources:

- wallet ledger: `WalletBalanceService.calculateCurrentBalances` over active include-in-total wallets
- reserves: P13C active planning reserves only
- obligations: P13B planned required/important obligations only
- expected incoming: not wired in P13D, shown as zero with `EXPECTED_INCOME_DATA_UNAVAILABLE`

Included/excluded behavior:

- active reserves reduce spendable
- released/cancelled reserves do not reduce spendable
- planned required/important obligations reduce spendable
- optional obligations are excluded by default
- paid/cancelled obligations are excluded
- overdue obligations are separated from upcoming obligations
- no-wallet income is excluded from available ledger by the wallet-balance source

Warning behavior:

- `NEGATIVE_ACTUALLY_SPENDABLE` when actually spendable is negative
- `LOW_ACTUALLY_SPENDABLE` when actually spendable is below `500000 VND` and non-negative
- `PROJECTED_SHORTFALL` when projected shortfall is positive
- `WALLET_BALANCE_SOURCE_UNCLEAR` and `PARTIAL_PLANNING_DATA` when no active include-in-total wallets exist
- `EXPECTED_INCOME_DATA_UNAVAILABLE` because P13D does not invent expected income
- `MIXED_CURRENCY_UNSUPPORTED` when reserve/obligation currency differs from workspace currency

Read-only rule:

- projection creates no transactions
- projection updates no wallet, reserve, or obligation state
- overdue/upcoming classification is computed, not persisted

## P13E Implemented Foundation

Migration: `V33__planned_obligation_payments.sql`

Fields added:

- `paid_at`
- `paid_note`
- unique `linked_transaction_id` index
- workspace/link lookup index

Endpoints:

- `POST /api/workspaces/{workspaceId}/planning/obligations/{obligationId}/link-transaction`
- `POST /api/workspaces/{workspaceId}/planning/obligations/{obligationId}/mark-paid`

Validation:

- obligation must belong to workspace
- transaction must belong to workspace
- linked transaction must be posted, non-deleted, `EXPENSE`
- linked transaction amount/currency must match obligation
- duplicate transaction links are blocked
- mark-paid requires wallet/category in same workspace
- cancelled obligation payment is blocked
- already-paid obligation cannot be re-linked to another transaction

Idempotency:

- same existing transaction link returns paid obligation
- repeated mark-paid returns existing linked transaction
- no duplicate payment transaction is created on repeat

Ledger rule:

- link existing transaction creates no transaction
- mark-paid creates exactly one transaction through `TransactionService`
- due date alone never creates a transaction

## P13F Implemented Foundation

Endpoints:

- `GET /api/workspaces/{workspaceId}/planning/overview`
- `GET /api/workspaces/{workspaceId}/planning/projection`
- `GET /api/workspaces/{workspaceId}/planning/obligations/summary`
- `GET /api/workspaces/{workspaceId}/planning/reserves/summary`

Overview response:

- projection snapshot from P13D
- obligation summary: upcoming/overdue/due-soon/paid/cancelled counts
- reserve summary: active/released/cancelled amounts and counts
- upcoming obligations list
- overdue obligations list
- active reserves list
- deterministic planning action items
- projection warnings

Planning action items:

- `PLANNING_OBLIGATION_OVERDUE`
- `PLANNING_OBLIGATION_DUE_SOON`
- `PROJECTED_SHORTFALL`
- `LOW_ACTUALLY_SPENDABLE`
- `ACTIVE_RESERVE_SUMMARY`
- `PARTIAL_PLANNING_DATA`

Read-only rule:

- overview/projection/summary endpoints are GET-only
- no transaction is created
- no obligation is marked paid/cancelled
- no reserve is released/cancelled
- due-soon/overdue states are computed only

Financial Insight integration:

- not touched in P13F
- planning action items are returned inside `PlanningOverviewResponse`
- cross-module integration can be added later if needed

## Warning Codes

- `PLANNING_OBLIGATION_MISSING_AMOUNT`
- `PLANNING_OBLIGATION_MISSING_DUE_DATE`
- `PLANNING_OBLIGATION_OVERDUE`
- `PLANNING_OBLIGATION_DUE_SOON`
- `PLANNING_OBLIGATION_ALREADY_PAID`
- `PLANNING_OBLIGATION_LINKED_TRANSACTION_MISSING`
- `PLANNING_OBLIGATION_CANCELLED`
- `PLANNING_OBLIGATION_INVALID_STATE`
- `PLANNING_OBLIGATION_TRANSACTION_REQUIRED`
- `PLANNING_OBLIGATION_TRANSACTION_NOT_FOUND`
- `PLANNING_OBLIGATION_TRANSACTION_NOT_POSTED`
- `PLANNING_OBLIGATION_TRANSACTION_DELETED`
- `PLANNING_OBLIGATION_TRANSACTION_TYPE_UNSUPPORTED`
- `PLANNING_OBLIGATION_AMOUNT_MISMATCH`
- `PLANNING_OBLIGATION_CURRENCY_MISMATCH`
- `PLANNING_OBLIGATION_WALLET_REQUIRED`
- `PLANNING_OBLIGATION_CATEGORY_REQUIRED`
- `PLANNING_OBLIGATION_DUPLICATE_LINK`
- `PLANNING_OBLIGATION_MARK_PAID_FAILED`
- `RESERVE_AMOUNT_EXCEEDS_AVAILABLE`
- `RESERVE_BALANCE_CHECK_UNAVAILABLE`
- `RESERVE_ALREADY_RELEASED`
- `RESERVE_ALREADY_CANCELLED`
- `RESERVE_CANCELLED_CANNOT_RELEASE`
- `RESERVE_RELEASED_CANNOT_CANCEL`
- `RESERVE_CANNOT_UPDATE_INACTIVE`
- `RESERVE_WALLET_NOT_FOUND`
- `RESERVE_CATEGORY_NOT_FOUND`
- `RESERVE_JAR_NOT_FOUND`
- `RESERVE_CROSS_WORKSPACE_REFERENCE`
- `RESERVE_DATA_UNAVAILABLE`
- `EXPECTED_INCOME_NOT_SPENDABLE`
- `EXPECTED_INCOME_DATA_UNAVAILABLE`
- `NEGATIVE_ACTUALLY_SPENDABLE`
- `LOW_ACTUALLY_SPENDABLE`
- `WALLET_BALANCE_SOURCE_UNCLEAR`
- `RECEIVABLE_NOT_SPENDABLE`
- `PROJECTED_SHORTFALL`
- `PARTIAL_PLANNING_DATA`
- `MIXED_CURRENCY_UNSUPPORTED`

## Workspace And Security Rules

- All endpoints require authenticated user.
- Read endpoints call `WorkspaceService.verifyMembership`.
- Write endpoints call `WorkspaceService.requireWritableMember`.
- All repository queries filter by `workspaceId`.
- Entity ids in responses must belong to the same workspace.
- Planning endpoints must never expose cross-workspace transaction, wallet, debt, reserve, or obligation ids.

## Deferred

- frontend UI changes
- notification system
- scheduled obligation reminders
- AI/LLM planning summary
- fake projected income
- unbounded recurrence generation
- broad planning/jar/debt redesign
