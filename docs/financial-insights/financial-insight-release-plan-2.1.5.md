# Financial Insight Release Plan 2.1.5

Status: PLANNED / NOT IMPLEMENTED.

## Theme

Financial Insight Backend Foundation.

MoneyFlow should explain useful financial signals from real ledger data. This release must remain deterministic, evidence-backed, workspace-scoped, and read-only until the user chooses an existing action flow.

## Scope

Included:

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
- New database schema.
- User-configured budgets unless explicitly added later.
- Receipt/voice insight generation beyond confirmed transaction evidence.

## Phase Table

| Phase | Goal | Included | Deferred | Tests | Acceptance criteria |
| --- | --- | --- | --- | --- | --- |
| P12A | Spec and metric audit | Audit docs, contract, release plan, release stub | Runtime endpoints and code | Docs checks only | Docs identify available metrics, gaps, formulas, risks, and P12B-P12G plan. |
| P12B | Insight metric query layer | Read-only aggregate service for income, expense, category, jar, income source, no-wallet income, baselines | Public API and card copy | Focused repository/service tests | Queries are workspace-scoped, date-range aware, exclude deleted/draft/planned/void, and preserve debt/transfer rules. |
| P12C | Spending/category/jar insight rules | `SPENDING_SPIKE`, `CATEGORY_OVERSPEND`, `JAR_OVERSPEND`, `UNUSUAL_TRANSACTION` deterministic cards | AI summaries, custom budgets if absent | Rule service tests | Cards include evidence, severity, confidence, thresholds, and exclusions. |
| P12D | Actually spendable calculation backend | Insight wrapper around planning formula, reserve warnings, upcoming obligations | Payable debt auto-subtraction unless contract changes | Planning/insight integration tests | Uses existing planning service, shows assumptions and exclusions, does not alter balances. |
| P12E | Action items/data quality insights | Missing category, missing jar, no-wallet income, variable obligation amount, stale closing, drafts needing review | Auto-fixes | Service/API tests | Action items route to existing modules and never mutate data. |
| P12F | Insight API endpoints | `/insights/overview`, `/spending`, `/actually-spendable`, `/anomalies`, `/action-items` | Frontend UI | Controller integration tests | Endpoints return stable DTOs, membership checked, no writes, no fake data. |
| P12G | Release lock | Docs, validation, known limitations, UAT checklist | New features | Targeted suites and scans | Backend status locked honestly. |

## Acceptance Criteria

- No insight endpoint writes financial data.
- No fake/mock/sample runtime insight data.
- No external AI or LLM calls.
- Every response is workspace-scoped and evidence-backed.
- Transfers are excluded from income/expense insight totals.
- Debt movement transaction types are not counted as normal income/expense.
- No-wallet income is income-statistical only, not wallet balance.
- Historical analytics-only rows are never replayed into wallet balance.
- Unconfirmed voice/receipt drafts are excluded from ledger metrics.
- Actually spendable uses existing planning rules and states exclusions.
- Missing data appears as action items or data quality warnings, not guessed values.
- Large-history queries use aggregate DB queries, not full table scans in Java.

## Validation Strategy

- P12A: `git diff --check`, mojibake scan, secret scan.
- P12B: insight metric query tests plus `TransactionModuleIntegrationTests`.
- P12C: spending rule tests with threshold and insufficient-baseline cases.
- P12D: planning and actually-spendable tests.
- P12E: action item tests for missing fields, no-wallet income, drafts, stale closing.
- P12F: controller integration tests for every endpoint, workspace isolation, empty-state responses.
- P12G: targeted insight, dashboard, planning, transaction, voice/receipt smoke suites plus release scans.

## Risk Register

| Risk | Mitigation |
| --- | --- |
| Generic advice with no evidence | Require `evidence[]` and metric formulas for every card. |
| Double-count historical imports | Centralize inclusion/exclusion rules in P12B. |
| Counting debt movement as income/expense | Use statistical `INCOME` and `EXPENSE` only for cashflow. |
| Mixing wallet balance and income stats | Label wallet balance separately from net cashflow. |
| False anomaly positives | Require threshold plus minimum absolute delta and confidence levels. |
| Slow dashboard on large history | Aggregate in repository queries and page evidence IDs. |
| Cross-workspace leakage | Membership check plus `workspaceId` in every query. |
| Frontend invents meaning | Backend response includes labels, messages, actions, evidence, and exclusions. |
| Mojibake in Vietnamese copy | Scan docs/source before completion. |

## Deferred Modules

- Frontend insight dashboard.
- Push/email reminders.
- AI natural-language narrative.
- User-defined budgets.
- Forecasting beyond deterministic baselines.
- Bank sync or automatic balance import.

## Next Queue Item

P12B - insight metric query layer.
