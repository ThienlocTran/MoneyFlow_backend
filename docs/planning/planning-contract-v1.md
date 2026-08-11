# Planning Backend Contract V1

Status: PLANNED / NOT IMPLEMENTED. P13A defines the contract only.

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

- `from`
- `to`
- `status` optional

`POST /obligations`

Creates a planned obligation. Must not create a transaction or affect wallet balance.

`PATCH /obligations/{obligationId}`

Updates planned obligation fields. Must not mutate linked transaction data.

`POST /obligations/{obligationId}/mark-paid`

Later behavior:

- if linking an existing posted transaction, set status `PAID` and `linkedTransactionId`
- if creating a transaction, route through normal transaction service by explicit user action
- no auto-posting from due date alone

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

- fields from `PlannedObligation`
- `sourceType`: `ONE_OFF`, `RECURRING_OCCURRENCE`, `DEBT`
- `evidence`

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
