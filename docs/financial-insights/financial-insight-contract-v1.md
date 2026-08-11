# Financial Insight Contract V1

Status: P12C internal spending insight rules implemented. Public insight APIs are not implemented yet.

## Guardrails

- Read-only: insight code must not create, edit, delete, or infer financial records.
- Workspace-scoped: every query filters by `workspaceId`; future API endpoints must also verify active workspace membership.
- Evidence-backed: later cards must cite metric values, date ranges, and exclusions.
- No fake runtime data: empty backend data returns empty or zero metrics.
- No AI calls in the 2.1.5 backend foundation.

## Proposed Endpoints

Base path: `/api/workspaces/{workspaceId}/insights`

| Endpoint | Purpose | Writes? | Status |
| --- | --- | --- | --- |
| `GET /overview?period=month` | Income, expense, cashflow, wallet, spendable, top cards | No | Planned |
| `GET /spending?from=YYYY-MM-DD&to=YYYY-MM-DD` | Category and jar spending | No | Planned |
| `GET /actually-spendable?date=YYYY-MM-DD` | Ledger balance, reserves, obligations, available estimate | No | Planned |
| `GET /anomalies?period=month` | Rule-based unusual spending/income signals | No | Planned |
| `GET /action-items` | Missing info and records needing review | No | Planned |

## P12B Metric Query Service

`FinancialMetricQueryService` is the internal query layer for later insight rules and endpoints.

Implemented methods:

- `getPeriodTotals(workspaceId, from, to)`
- `getIncomeBySource(workspaceId, from, to)`
- `getNoWalletIncome(workspaceId, from, to)`
- `getExpenseByCategory(workspaceId, from, to)`
- `getExpenseByJar(workspaceId, from, to)`
- `getUncategorizedExpense(workspaceId, from, to)`
- `getWalletAffectingSummary(workspaceId, from, to)`

DTO records:

- `FinancialPeriodMetric`
- `MetricBreakdownRow`
- `NoWalletIncomeMetric`
- `WalletAffectingMetric`

## P12C Spending Rule Service

`SpendingInsightRuleService` turns P12B metrics into internal deterministic `InsightCard` records.

Implemented card types:

- `CATEGORY_OVERSPEND`
- `JAR_OVERSPEND`
- `SPENDING_SPIKE`
- `UNCATEGORIZED_SPENDING`
- `SPENDING_CONCENTRATION`
- `NO_WALLET_INCOME`

Internal card model:

- `InsightCard`
- `InsightEvidence`
- `InsightType`
- `InsightSeverity`
- `InsightConfidence`

Every card includes:

- deterministic key
- type, severity, confidence
- title and message
- amount and currency
- period from/to
- evidence with metric value, baseline when applicable, delta, ratio/share, entity id/name, rule key, and count
- optional action metadata for cleanup-style cards

No public controller is added in P12C.

## Metric Formulas

### Total Income

`totalIncome = sum(amount where type=INCOME and status=POSTED and deletedAt is null and transactionDate between from and to)`

Includes no-wallet income. Excludes transfers, debt movements, adjustments, deleted rows, `DRAFT`, `PLANNED`, and `VOID`.

### Total Expense

`totalExpense = sum(amount where type=EXPENSE and status=POSTED and deletedAt is null and transactionDate between from and to)`

Excludes transfers, loan disbursement, borrowing repayment, adjustments, deleted rows, `DRAFT`, `PLANNED`, and `VOID`.

### Net Cashflow

`netCashflow = totalIncome - totalExpense`

This is statistical cashflow, not wallet balance delta.

### Income By Source

`incomeBySource = sum(POSTED INCOME amount by incomeSource in range)`

Missing income source groups as `Chua ro nguon` in contract wording. Runtime DTO uses Vietnamese Unicode label `Chưa rõ nguồn`.

### No-Wallet Income

No-wallet income is `POSTED INCOME` where `wallet IS NULL`.

It counts in income and net cashflow stats. It does not count as wallet balance movement.

### Expense By Category

`expenseByCategory = sum(POSTED EXPENSE amount by category in range)`

Missing category groups as `Chua phan loai` in contract wording. Runtime DTO uses `Chưa phân loại`.

### Expense By Jar

`expenseByJar = sum(POSTED EXPENSE amount by category.jar in range)`

Missing category groups separately from category-without-jar:

- no category: `Chưa phân loại`
- category without jar: `Chưa có hũ`

### Wallet-Affecting Summary

Wallet-affecting income includes posted non-deleted balance-positive transaction types with wallet and `affectsWalletBalance=true`:

- `INCOME`
- `LOAN_COLLECTION`
- `BORROWING_RECEIPT`

Wallet-affecting expense includes posted non-deleted balance-negative transaction types with wallet and `affectsWalletBalance=true`:

- `EXPENSE`
- `LOAN_DISBURSEMENT`
- `BORROWING_REPAYMENT`

Transfers are reported as transfer-in and transfer-out totals from `TransferDetail`. Workspace-wide internal transfers normally net to zero.

Adjustment snapshot amount is reported separately from normal income/expense.

Authoritative wallet balances still come from `WalletBalanceService`.

## Date Range Convention

P12B uses explicit `LocalDate from` and `LocalDate to`, both inclusive:

`transactionDate BETWEEN :from AND :to`

The query layer does not use current date.

## P12C Rule Thresholds

Baseline definition:

- Full-month periods use the previous 3 completed calendar months.
- Other explicit date ranges use 3 immediately preceding equal-length ranges.
- Baseline amount is the average of those periods.

Thresholds:

- Category overspend: ratio >= `1.35`, delta >= `200000 VND`, current >= `300000 VND`.
- Category critical: ratio >= `2.00`, delta >= `500000 VND`.
- New category high spend: baseline zero and current >= `500000 VND`, `INFO`.
- Jar overspend: ratio >= `1.30`, delta >= `300000 VND`, current >= `500000 VND`.
- Jar critical: ratio >= `2.00`, delta >= `1000000 VND`.
- Overall spending spike: ratio >= `1.30`, delta >= `500000 VND`.
- Overall critical: ratio >= `1.75`, delta >= `1000000 VND`.
- Uncategorized spending: count >= `3`, or amount >= `200000 VND`, or share >= `15%`.
- Spending concentration: top category share >= `40%` and total expense >= `500000 VND`; warning at share >= `60%`.
- No-wallet income: amount > `0`, `INFO`.

Ranking and dedup:

- Default maximum is 8 cards.
- Sort by severity first, then amount, then deterministic key.
- Zero-value cards are skipped.
- Category and jar cards are allowed together when both have separate evidence.

## Security Rules

- Metric service methods are internal and filter by `workspaceId`.
- Future public endpoints must call existing workspace membership checks before invoking metrics.
- Evidence transaction IDs must stay limited.
- Do not expose raw OCR text, voice transcripts, private notes, or secrets in insight responses.

## Known Limitations

- No user-facing insight dashboard.
- No public insight API endpoints.
- No public insight API endpoints yet.
- No anomaly detection beyond deterministic P12C spending spike rules.
- No final actually-spendable insight wrapper until P12D.
- Metrics depend on existing transaction classification correctness.
- Jar budget comparison is deferred because jars do not have a monthly budget amount.
