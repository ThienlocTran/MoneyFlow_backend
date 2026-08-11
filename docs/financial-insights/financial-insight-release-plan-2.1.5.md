# Financial Insight Release Plan 2.1.5

Status: P12C complete in backend code. Public insight APIs remain planned.

## Theme

MoneyFlow 2.1.5 adds deterministic Financial Insight backend foundations. Insights must be real-data, read-only, workspace-scoped, and evidence-backed.

## Scope

Included across 2.1.5:

- Metric audit and backend contract.
- Read-only metric query layer.
- Deterministic spending/category/jar rules.
- Actually spendable insight wrapper.
- Action items and data quality insights.
- Insight API endpoints.
- Release lock and validation.

Deferred:

- AI/LLM summary generation.
- Scheduled notifications.
- Frontend insight dashboard.
- New database schema unless later phases prove need.
- User-configured budgets unless explicitly added later.

## Phase Table

| Phase | Goal | Status | Acceptance criteria |
| --- | --- | --- | --- |
| P12A | Spec and metric audit | Complete | Docs identify formulas, gaps, risks, and P12B-P12G plan. |
| P12B | Insight metric query layer | Complete | Queries are workspace-scoped, date-range aware, exclude deleted/draft/planned/void, preserve debt/transfer rules. |
| P12C | Spending/category/jar insight rules | Complete | Cards include evidence, severity, confidence, thresholds, and exclusions. |
| P12D | Actually spendable calculation backend | Planned | Uses existing planning service and states exclusions. |
| P12E | Action items/data quality insights | Planned | Action items route to existing modules and never mutate data. |
| P12F | Insight API endpoints | Planned | Endpoints return stable DTOs, membership checked, no writes, no fake data. |
| P12G | Release lock | Planned | Targeted suites and release scans pass or limitations are documented. |

## P12B Delivered

- Added `FinancialMetricQueryService`.
- Added internal DTO records for period totals, breakdown rows, no-wallet income, and wallet-affecting summary.
- Used JPQL aggregate queries to avoid loading transaction history into Java.
- Kept `from` and `to` inclusive with `LocalDate`.
- Kept public API work deferred.
- Kept insight card generation deferred.

## P12B Test Coverage

`FinancialInsightMetricQueryServiceTests` covers:

- period totals
- no-wallet income
- income with wallet
- expense by category
- expense by jar
- uncategorized expense
- transfers excluded from income/expense
- debt movements excluded from normal income/expense
- adjustments/snapshots excluded from normal income/expense
- drafts/planned/deleted excluded
- historical analytics-only rows included in income stats but excluded from wallet-affecting movement
- inclusive date range boundaries
- workspace isolation
- empty state
- invalid date range

## P12C Delivered

- Added internal `InsightCard` and evidence DTO records/enums.
- Added `SpendingInsightRuleService`.
- Implemented category overspend, jar overspend, overall spending spike, uncategorized spending, spending concentration, and no-wallet income cards.
- Used trailing 3-period baseline averages.
- Added severity/confidence mapping.
- Added deterministic keys and ranking with default max 8 cards.
- Kept public API work deferred.

## P12C Test Coverage

`FinancialInsightRuleServiceTests` covers:

- category overspend warning
- category overspend critical
- small-delta noise suppression
- new category high-spend info card
- jar overspend by baseline
- overall spending spike
- uncategorized spending action card
- spending concentration
- no-wallet income info card
- max-card ranking and severity order
- workspace metric-service isolation
- empty metrics

## Validation Strategy

- P12B: `FinancialInsightMetricQueryServiceTests` plus transaction regression suite.
- P12C: `FinancialInsightRuleServiceTests`.
- P12D: planning and actually-spendable integration tests.
- P12E: action item tests for missing fields and stale data.
- P12F: controller integration tests for membership, no writes, and empty states.
- P12G: targeted insight, dashboard, planning, transaction, voice, and receipt smoke suites.

## Risk Register

| Risk | Mitigation |
| --- | --- |
| Generic advice with no evidence | Require metric evidence for every later card. |
| Double-count historical imports | Centralize statistical vs wallet-affecting formulas. |
| Debt movement counted as income/expense | Normal totals use only `INCOME` and `EXPENSE`. |
| Wallet balance confused with cashflow | Wallet-affecting summary is separate; balances still use `WalletBalanceService`. |
| Cross-workspace leakage | Every P12B query filters by `workspaceId`; endpoint membership checks come in P12F. |
| Slow large-history insight queries | Use aggregate DB queries and small evidence samples. |

## Next Queue Item

P12D - actually spendable calculation backend.
