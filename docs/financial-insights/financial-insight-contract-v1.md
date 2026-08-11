# Financial Insight Contract V1

Status: P12E internal action item and data quality detection implemented. Public insight APIs are not implemented yet.

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

## P12D Actually Spendable Service

`ActuallySpendableService` calculates an internal snapshot:

`actuallySpendable = availableLedgerBalance - activeReserveAmount - upcomingRequiredOutflowAmount - overdueRequiredOutflowAmount`

Snapshot fields:

- workspace id, as-of date, horizon days, currency
- available ledger balance
- active reserve amount
- upcoming required outflow amount
- overdue required outflow amount
- actually spendable amount
- expected incoming amount
- source breakdown items
- data quality warnings
- generated timestamp

Data sources:

- Wallet balances: active, include-in-total wallets through `WalletBalanceService`.
- Reserves: active sinking fund, savings goal, and emergency fund aggregates.
- Required outflows: pending payable obligation occurrences within horizon.
- Overdue outflows: pending payable obligation occurrences before `asOfDate`.
- Expected incoming: pending receivable obligations within horizon, informational only.
- No-wallet income: current-month no-wallet income metric, warning only.

No public controller is added in P12D.

## P12E Action Item Service

`FinancialActionItemService` produces grouped internal action items and data quality warnings.

Implemented action item types:

- `TRANSACTION_MISSING_CATEGORY`
- `TRANSACTION_MISSING_WALLET`
- `INCOME_WITHOUT_WALLET`
- `VOICE_DRAFT_PENDING`
- `RECEIPT_DRAFT_PENDING`
- `OCR_REVIEW_REQUIRED`
- `DEBT_MISSING_DUE_DATE`
- `RESERVE_DATA_MISSING`
- `UPCOMING_OBLIGATION_DATA_MISSING`
- `NEGATIVE_ACTUALLY_SPENDABLE`
- `LOW_ACTUALLY_SPENDABLE`
- `HISTORICAL_DATA_EXCLUDED_FROM_WALLET`
- `DATA_QUALITY_PARTIAL`

Internal model:

- `FinancialActionItem`
- `FinancialActionItemReport`
- `FinancialActionMetric`
- `ActionItemType`

Data sources:

- Posted expenses missing category or wallet.
- P12B no-wallet income metric.
- Pending voice session drafts.
- Pending receipt session drafts and incomplete or low-confidence OCR drafts.
- Open payable debts missing due date.
- Historical analytics-only transactions.
- P12D actually-spendable snapshot warnings.

No public controller is added in P12E.

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

## P12D Spendable Rules

- Default horizon is 30 days when caller passes zero or negative days.
- Caller-provided `asOfDate` drives the calculation; null falls back to `Clock`.
- Expected incoming money does not increase actually spendable.
- No-wallet income does not increase actually spendable.
- Zero or negative actually spendable is returned as-is and gets `NEGATIVE_SPENDABLE`.
- Missing reserve data yields `RESERVE_DATA_UNAVAILABLE` and `PARTIAL_DATA`.
- Missing payable obligation data yields `UPCOMING_OBLIGATION_DATA_UNAVAILABLE` and `PARTIAL_DATA`.
- Obligations without expected amount are excluded and reported through partial-data warnings.

## P12E Action Item Rules

- Missing expense category is grouped into one item; warning when count >= 3 or amount >= `200000 VND`.
- Expense missing wallet is a warning.
- Income without wallet is informational, not an error.
- Pending voice drafts are informational; warning when count >= 3.
- Pending receipt drafts are informational.
- OCR review is warning when required fields are missing or confidence is low.
- Payable debts without due date create a warning.
- Historical analytics-only rows create an informational data quality item.
- Negative actually spendable creates a critical item.
- Low positive actually spendable below `500000 VND` creates a warning.
- Reserve and upcoming obligation missing-data warnings map to informational action items.
- Similar records are grouped; default maximum is 10 items.
- Ranking is severity first, then amount/count, then deterministic key.

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
- No public actually-spendable insight endpoint until P12F.
- No public action item endpoint until P12F.
- Metrics depend on existing transaction classification correctness.
- Jar budget comparison is deferred because jars do not have a monthly budget amount.
- Calculation quality depends on reserve and obligation data completeness.
- Action routes are internal placeholders for future frontend mapping.
