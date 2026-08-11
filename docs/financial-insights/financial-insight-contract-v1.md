# Financial Insight Contract V1

Status: proposal only. No endpoints are implemented in P12A.

## Guardrails

- Product principle: practical insight over generic advice.
- Domain object touched: read-only financial metrics across ledger, planning, reserve, debt, obligation, and review modules.
- Data integrity rule: insight generation must not write financial records, create transactions, infer missing wallet/category, or call external AI.
- User confirmation required: future actions must send users to existing edit/confirm flows.
- No hallucinated advice: every insight must include evidence metrics and exclusions.

## Proposed Endpoints

Base path: `/api/workspaces/{workspaceId}/insights`

| Endpoint | Purpose | Writes? |
| --- | --- | --- |
| `GET /overview?period=month` | High-level income, expense, cashflow, wallet, spendable, top cards | No |
| `GET /spending?from=YYYY-MM-DD&to=YYYY-MM-DD` | Category/jar spending and overspend warnings | No |
| `GET /actually-spendable?date=YYYY-MM-DD` | Ledger balance, reserves, obligations, available estimate | No |
| `GET /anomalies?period=month` | Rule-based unusual spending/income signals | No |
| `GET /action-items` | Missing info and records needing review | No |

All endpoints must verify active workspace membership before reading data. They must never accept workspace IDs from request bodies.

## DTO Proposal

### `InsightSummaryResponse`

- `workspaceId`
- `period`
- `generatedAt`
- `currency`
- `totals`
- `insightCards[]`
- `actionItems[]`
- `dataQualityWarnings[]`

### `InsightCard`

- `deterministicKey`
- `type`
- `severity`: `INFO`, `WARNING`, `CRITICAL`
- `title`
- `message`
- `amount`
- `currency`
- `period`
- `evidence[]`
- `action`
- `confidence`: `HIGH`, `MEDIUM`, `LOW`
- `generatedAt`

### `Evidence`

- `metricName`
- `value`
- `baselineValue`
- `delta`
- `transactionIds` limited to small count
- `categoryId`, `categoryName`
- `jarId`, `jarName`
- `incomeSourceId`, `incomeSourceName`
- `dateRange`
- `exclusions[]`

### `ActionItem`

- `type`
- `title`
- `message`
- `targetRoute`
- `targetEntityId`
- `severity`

### `DataQualityWarning`

- `code`
- `message`
- `affectedCount`
- `severity`

## Metric Formulas

### Total Income

Include:

- `POSTED`, non-deleted `INCOME` transactions in period.
- Income transactions with `walletId = null`, because they count for income statistics.

Exclude:

- `TRANSFER`
- `LOAN_COLLECTION`
- `BORROWING_RECEIPT`
- wallet snapshots
- unconfirmed drafts
- deleted, `DRAFT`, `PLANNED`, `VOID`

Formula:

`totalIncome = sum(amount where type=INCOME and status=POSTED and deletedAt is null and date in range)`

### Total Expense

Include:

- `POSTED`, non-deleted `EXPENSE` transactions in period.

Exclude:

- `TRANSFER`
- `LOAN_DISBURSEMENT`
- `BORROWING_REPAYMENT`
- snapshots
- unconfirmed drafts
- deleted, `DRAFT`, `PLANNED`, `VOID`

Formula:

`totalExpense = sum(amount where type=EXPENSE and status=POSTED and deletedAt is null and date in range)`

### Net Cashflow

Formula:

`netCashflow = totalIncome - totalExpense`

Use statistical income/expense, not wallet delta.

### Wallet Balance

Use `WalletBalanceService`.

Includes:

- wallet opening balance
- `POSTED`, non-deleted, `affectsWalletBalance=true` income-like balance movements
- `POSTED`, non-deleted, `affectsWalletBalance=true` expense-like balance movements
- transfers via transfer details

No-wallet income does not increase wallet balance.

### Actually Spendable

First version reuses `PlanningService.actuallySpendable`.

Formula:

`actuallySpendable = availableLedger - reservedTotal - knownUpcomingObligations`

Known exclusions:

- standalone payable debts unless represented as recurring obligations
- student loan simulation commitments
- variable obligations with unknown amount
- unconfirmed drafts
- historical analytics-only rows

### Category And Jar Spending

Formula:

`categorySpending = sum(POSTED EXPENSE amount by category in range)`

`jarSpending = sum(POSTED EXPENSE amount by category.jar in range)`

Missing category groups as `Chưa phân loại`. Missing jar groups as `Chưa gắn hũ`.

### Income By Source

Formula:

`incomeBySource = sum(POSTED INCOME amount by incomeSource in range)`

Missing income source groups as `Chưa gắn nguồn thu`.

### Anomaly / Spike

No ML in V1.

Suggested deterministic rule:

- current period category spending > trailing 3 completed months average * `1.35`
- and absolute delta >= `200000 VND`

If no baseline exists, emit `LOW` confidence or skip unless current amount is materially high.

## Insight Types

| Type | Required input | Rule | Severity | Confidence | Dashboard | Actionable | Vietnamese example | Limitation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `SPENDING_SPIKE` | Category spend current period plus 3-month baseline | Current > baseline * threshold and delta >= minimum | WARNING | MEDIUM/HIGH | Yes | Yes | `Ăn uống đã dùng 1.250.000đ, cao hơn 38% so với trung bình 3 tháng.` | Needs enough baseline history. |
| `CATEGORY_OVERSPEND` | Category spend and threshold/budget | Category exceeds configured or inferred limit | WARNING | MEDIUM | Yes | Yes | `Danh mục Ăn uống đang vượt mức dự kiến 420.000đ.` | No explicit category budget exists yet. |
| `JAR_OVERSPEND` | Jar spend, jar allocation percent, income total | Jar spend > allocation share of income | WARNING | MEDIUM | Yes | Yes | `Hũ Ăn uống đã dùng 42% thu nhập tháng này.` | Allocation percent is not a hard budget. |
| `INCOME_DROP` | Current income and baseline income | Current income < baseline * threshold | WARNING | MEDIUM | Yes | No | `Thu nhập tháng này thấp hơn trung bình 3 tháng 1.800.000đ.` | Partial current month can mislead. |
| `INCOME_SOURCE_SHIFT` | Income by source current and baseline | Dominant source share changes materially | INFO | MEDIUM | Yes | No | `Nguồn freelance chiếm 55% thu nhập tháng này, cao hơn bình thường.` | Needs income source tagging. |
| `NO_WALLET_INCOME` | Posted income with null wallet | Sum no-wallet income in period > 0 | INFO | HIGH | Yes | Yes | `Bạn có 800.000đ thu nhập chưa gắn vào ví nào. Khoản này tính vào thống kê thu nhập nhưng chưa làm tăng số dư ví.` | User may intentionally track stats-only income. |
| `UPCOMING_OBLIGATION` | Pending payable occurrences | Due within configured window | WARNING | HIGH | Yes | Yes | `Bạn có 2 nghĩa vụ cần giữ 1.200.000đ trước ngày 15/08.` | Variable amount may be unknown. |
| `LOW_ACTUALLY_SPENDABLE` | Planning actually spendable | Amount below threshold or negative | CRITICAL | HIGH | Yes | Yes | `Số tiền còn có thể chi đang âm 350.000đ sau khi trừ quỹ và nghĩa vụ.` | Depends on fresh wallet/reserve data. |
| `DEBT_COLLECTION_DUE` | Receivable debts with due date | Due soon or overdue | INFO/WARNING | MEDIUM | Yes | Yes | `Có khoản phải thu 2.000.000đ đã đến hạn từ Minh.` | Debt payment schedule is basic. |
| `DEBT_REPAYMENT_DUE` | Payable debts with due date | Due soon or overdue | WARNING | MEDIUM | Yes | Yes | `Có khoản phải trả 1.500.000đ sắp đến hạn.` | Standalone debts are not in planning formula yet. |
| `MISSING_TRANSACTION_INFO` | Posted records missing category/wallet/source | Count missing fields by type | INFO/WARNING | HIGH | Yes | Yes | `Có 4 giao dịch chi chưa có danh mục nên phân tích theo hũ chưa đầy đủ.` | Some no-wallet income may be intentional. |
| `UNUSUAL_TRANSACTION` | Largest transaction and baseline/category distribution | Single transaction materially above personal pattern | INFO/WARNING | MEDIUM | Yes | Yes | `Khoản chi 3.200.000đ lớn hơn nhiều so với các khoản cùng danh mục.` | Needs baseline and false-positive guard. |
| `CASHFLOW_SUMMARY` | Total income, expense, net | Deterministic period summary | INFO | HIGH | Yes | No | `Tháng này thu 12.000.000đ, chi 8.400.000đ, còn dư 3.600.000đ.` | Summary only, not advice. |
| `SAVINGS_PROGRESS` | Savings/reserve ledger totals and targets | Progress percent or off-track signal | INFO/WARNING | HIGH | Yes | Yes | `Mục tiêu Quỹ khẩn cấp đạt 62%.` | Needs target and active status. |
| `RESERVE_WARNING` | Reserve totals and wallet balances | Reserved total high or overlapping warnings present | WARNING | MEDIUM | Yes | Yes | `Tiền giữ lại đang chiếm phần lớn ví khả dụng; kiểm tra trùng lặp giữa quỹ.` | Reserve overlap cannot be inferred safely. |

## Data Quality Warning Codes

- `INSIGHT_MISSING_CATEGORY`
- `INSIGHT_MISSING_JAR`
- `INSIGHT_NO_WALLET_INCOME`
- `INSIGHT_VARIABLE_OBLIGATION_AMOUNT_UNKNOWN`
- `INSIGHT_HISTORICAL_ROWS_EXCLUDED`
- `INSIGHT_DRAFTS_EXCLUDED`
- `INSIGHT_STALE_DAILY_CLOSING`
- `INSIGHT_RESERVE_OVERLAP_POSSIBLE`
- `INSIGHT_PAYABLE_DEBTS_EXCLUDED_FROM_SPENDABLE`

## Security Rules

- Every query must filter by `workspaceId`.
- Every endpoint must verify active workspace membership before reading.
- Do not expose raw OCR text, voice transcript, or notes unless an endpoint explicitly returns evidence snippets.
- Limit transaction evidence IDs to small arrays; use totals for the rest.
- Do not log private financial values in warnings/errors.
- Do not call external LLMs or AI providers in V1.

## Implementation Notes For Later Phases

- P12B should create a read-only query service before endpoint work.
- P12C/P12D should reuse `DashboardService`, `TransactionRepository`, `WalletBalanceService`, and `PlanningService` rules instead of recoding ledger math.
- P12F endpoint responses should include explanations and exclusions so frontend does not invent financial meaning.
