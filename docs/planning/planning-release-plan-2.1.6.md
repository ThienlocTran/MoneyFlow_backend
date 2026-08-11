# Planning Backend Release Plan 2.1.6

Status: P13C complete in backend code. P13D planning projection remains planned.

## Theme

MoneyFlow 2.1.6 should turn existing planning-adjacent backend pieces into a clear Planning Backend v1: upcoming obligations, reserved money, projection, and safe action warnings.

## Included Scope

- audit current planning/reserve/debt/obligation capabilities
- define Planning Backend v1 contract
- build planned obligation foundation
- build reserve allocation foundation or unify existing reserve modules
- build projection service
- define mark-paid/link-to-transaction behavior
- expose planning overview API and action items
- lock backend release with targeted tests

## Deferred Scope

- frontend planning dashboard redesign
- AI/LLM advice
- notifications
- scheduled jobs
- automatic transaction posting
- fake expected income
- broad jar/category redesign
- broad debt rewrite unless a phase proves it necessary

## Phase Table

| Phase | Goal | Scope | Deferred | Tests | Acceptance criteria |
| --- | --- | --- | --- | --- | --- |
| P13A | Planning backend audit + contract | Docs, model map, API proposal, risk register | Runtime code | Docs validation only | Audit/contract/plan/release docs exist and do not claim implementation. |
| P13B | Planned obligation model + CRUD foundation | Complete: one-off planned obligation table, workspace-scoped CRUD, validation, cancel flow | Projection and UI | `PlannedObligationApiIntegrationTests` | Planned obligation can be created, read, updated, cancelled without wallet balance effects. |
| P13C | Reserve allocation model + CRUD foundation | Complete: planning reserve table, workspace-scoped CRUD, release/cancel flow | Projection and cross-module dedupe | `ReserveAllocationApiIntegrationTests` | Reserve allocation can be created, read, updated, released, cancelled without wallet balance effects. |
| P13D | Planning projection service | Projection formula, warnings, debt/obligation/reserve inputs | Frontend UI | Projection service tests | Available ledger, reserves, upcoming, overdue, expected incoming, shortfall calculated correctly. |
| P13E | Mark-paid/link-to-transaction behavior | Link obligation to posted transaction or explicit transaction creation path | Auto-posting | Transaction/linking tests | Paid obligation links safely, same workspace only, no duplicate spend. |
| P13F | Planning API overview + action item integration | `/planning/overview`, warnings/action items, stable DTOs | Notifications | Controller/API tests | Frontend can fetch projection, obligations, reserves, warnings. |
| P13G | Planning backend release lock | Targeted tests, docs, scans, release status | Full frontend UAT | Targeted release validation | Backend status honestly marked locked/partial/blocked. |

## Validation Strategy

- P13A: `git diff --check`, mojibake scan, secret scan on touched docs.
- P13B: planned obligation repository/service/controller tests only.
- P13C: reserve allocation service/controller tests only.
- P13D: projection service tests for formula and edge cases.
- P13E: mark-paid/link tests, transaction side-effect tests.
- P13F: planning API/controller tests, workspace isolation, no fake data.
- P13G: targeted Planning backend tests only, release scans, docs lock.

No full Maven suite is required during queue phases unless specifically requested.

## P13B Delivered

- Added `planned_obligations` Flyway migration.
- Added planned obligation entity, status/priority/recurrence/computed-state enums.
- Added repository search capped at 100 rows.
- Added service validation and same-workspace wallet/category checks.
- Added CRUD/list/detail/cancel endpoints under `/api/workspaces/{workspaceId}/planning/obligations`.
- Added computed state mapping: upcoming, due soon, overdue, paid, cancelled.
- Stored recurrence type but deferred recurrence generation.
- Preserved ledger invariants: no transactions are created or modified by planned-obligation CRUD.

Targeted validation:

`.\mvnw.cmd "-Dtest=*PlannedObligation*Tests,*PlanningObligation*Tests" test`

Result: 9 tests passed, 0 failures, 0 errors, 0 skipped.

Known P13B limitations:

- No reserve allocation until P13C.
- No projection engine integration until P13D.
- No mark-paid/link-to-transaction until P13E.
- No recurrence generation.
- No frontend UI change.

## P13C Delivered

- Added `reserve_allocations` Flyway migration.
- Added reserve allocation entity, status enum, and purpose type enum.
- Added repository search capped at 100 rows.
- Added service validation and same-workspace wallet/category/jar checks.
- Added CRUD/list/detail/release/cancel endpoints under `/api/workspaces/{workspaceId}/planning/reserves`.
- Added release flow: `ACTIVE -> RELEASED`, repeated release returns released state, cancelled release blocked.
- Added cancel flow: `ACTIVE -> CANCELLED`, repeated cancel returns cancelled state, released cancel blocked.
- Added `RESERVE_BALANCE_CHECK_UNAVAILABLE` warning instead of inventing available-balance math.
- Preserved ledger invariants: no transactions are created or modified by reserve CRUD.

Targeted validation:

`.\mvnw.cmd "-Dtest=*ReserveAllocation*Tests,*PlanningReserve*Tests" test`

Result: targeted reserve allocation tests passed.

Known P13C limitations:

- No projection engine integration until P13D.
- No mark-paid/link-to-transaction until P13E.
- Reserve does not move money between wallets.
- Reserve release does not create income.
- Available balance check warns because no reliable reserve-specific source is used in P13C.
- No frontend UI change.

## Acceptance Rules

- No expected income is counted as spendable.
- No planned obligation changes wallet balance before payment/link.
- No reserve allocation creates income, expense, transfer, or wallet movement.
- Payable debt is not normal expense.
- Receivable debt is not spendable before collection.
- Workspace isolation is enforced before data access.
- Empty data returns zero/empty state plus warnings, not fake rows.
- All Vietnamese UI/API copy in touched files must be real UTF-8.

## Risk Register

| Risk | Mitigation |
| --- | --- |
| Double-count planned and posted expense | Mark-paid/link contract; exclude linked paid obligations from pending projection. |
| Expected income treated as cash | Keep expected incoming informational only. |
| Reserve allocation confused with wallet movement | Reserve docs/API state allocation is a lock, not income/expense. |
| Debt repayment counted as normal expense | Preserve debt movement transaction types and planning-specific payable debt logic. |
| Cross-workspace leakage | Require workspace membership and workspace-filtered repositories. |
| Over-building duplicate reserve models | Reuse existing sinking/savings/emergency modules unless v1 needs one thin unifier. |
| Infinite recurrence generation | Generate occurrences only for bounded horizons. |
| Planning UI depends on missing backend fields | P13F exposes stable overview DTO only after projection service is locked. |

## Current Evidence

Existing reusable pieces:

- `PlanningService`
- `PlanningPreference`
- `WalletBalanceService`
- `RecurringObligationTemplate`
- `ObligationOccurrence`
- `Debt`
- `SinkingFundAllocation`
- `SavingsGoalLedgerEntry`
- `EmergencyFundLedgerEntry`
- P12 Financial Insight actually-spendable and action item patterns

## Next Queue Item

P13D - planning projection service.
