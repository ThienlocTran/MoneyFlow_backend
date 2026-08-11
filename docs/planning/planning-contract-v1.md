# Planning Backend Contract V1

Status: P13B planned obligation CRUD foundation implemented. Projection/reserve/mark-paid phases remain planned.

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

Existing sinking fund, savings goal, and emergency fund ledgers already cover much of this behavior. Planning v1 should not duplicate them unless a unified reserve contract is required for UI consistency.

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

Status: deferred to P13E.

### Reserves

`GET /reserves`

Lists active and historical reserve allocations from Planning v1 or existing reserve modules.

`POST /reserves`

Creates reserve allocation. Must not create income or expense.

`PATCH /reserves/{reserveId}`

Updates reserve metadata/status.

`POST /reserves/{reserveId}/release`

Releases reserve. It increases actually spendable but does not create income.

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

- fields from `ReserveAllocation`
- `sourceType`: `PLANNING_RESERVE`, `SINKING_FUND`, `SAVINGS_GOAL`, `EMERGENCY_FUND`
- `evidence`

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
- optional linked transaction reference reserved for P13E
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

## Warning Codes

- `PLANNING_OBLIGATION_MISSING_AMOUNT`
- `PLANNING_OBLIGATION_MISSING_DUE_DATE`
- `PLANNING_OBLIGATION_OVERDUE`
- `PLANNING_OBLIGATION_DUE_SOON`
- `PLANNING_OBLIGATION_ALREADY_PAID`
- `PLANNING_OBLIGATION_LINKED_TRANSACTION_MISSING`
- `RESERVE_AMOUNT_EXCEEDS_AVAILABLE`
- `RESERVE_DATA_UNAVAILABLE`
- `RESERVE_ALREADY_RELEASED`
- `EXPECTED_INCOME_NOT_SPENDABLE`
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
