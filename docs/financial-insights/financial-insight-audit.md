# Financial Insight Backend Audit

Status: P12A audit only. No insight runtime, API, migration, scheduler, or AI call is implemented in this phase.

## Product Principle

- Affects: explainable financial visibility.
- Domain objects touched: transactions, wallets, income sources, categories, jars, reserves, obligations, debts, planning summaries, dashboard summaries.
- Data integrity rule: insights must read confirmed ledger facts only and must not create transactions, drafts, reserves, obligations, or synthetic data.
- User confirmation required: not applicable for read-only insights; future action items must route users to existing review/edit flows instead of mutating data.

## Current Model Map

| Source | Main entities/tables | What it can support | Notes |
| --- | --- | --- | --- |
| Transactions | `Transaction`, `TransferDetail`, `transactions` | Income, expense, net cashflow, category/jar spending, wallet deltas, source traceability | Amount is positive; `transactionType` controls meaning. Use only `POSTED` and `deletedAt IS NULL` unless a metric explicitly says otherwise. |
| Wallets | `Wallet`, `WalletBalanceService` | Current wallet balance, selected-wallet balance, include-in-total | Balance includes opening balance plus posted wallet-affecting ledger movements and transfers. |
| Categories | `Category` | Spending/income grouping, jar linkage | Category belongs to jar. Missing category must be grouped as `Chưa phân loại`, not guessed. |
| Jars | `Jar` | Purpose bucket grouping and allocation percent | Existing dashboard computes spending by jar when category has jar. It does not yet compute overspend against allocation targets. |
| Income sources | `IncomeSource`, transaction `incomeSource` | Income by source, source shift | Income source is separate from wallet. No-wallet income can count in income stats but not wallet balance. |
| Debts | `Debt`, debt payments, debt movement transaction types | Receivable/payable remaining, due-date signals | Debt movements are not normal income/expense. Payable debt is currently excluded from planning formula unless represented by an obligation. |
| Recurring obligations | `RecurringObligationTemplate`, `ObligationOccurrence` | Upcoming payable/receivable commitments, missing amount warnings | Planning currently uses pending payable occurrences with known expected amount. |
| Reserves/goals | Sinking funds, savings goals, emergency fund ledgers | Reserved funds and progress | Planning aggregates active reserved amounts. Potential overlap remains a documented assumption. |
| Wallet snapshots | `WalletBalanceSnapshot`, daily closing | Data freshness/reconciliation signals | Snapshots are evidence, not income or expense. |
| Historical imports | `Transaction.isHistorical`, `affectsWalletBalance=false`, `migrationKey` | Analytics-only historical trends | Never replay into wallet balance. Insights must show clear inclusion/exclusion when using historical rows. |
| Voice/OCR review | Voice/receipt session drafts and confirmed transaction links | Missing review items, source evidence on confirmed records | Unconfirmed drafts are not ledger facts. |

## Existing APIs And Services

| API/service | Current output | Date-range aware | Workspace scoped | Tested evidence | Insight reuse |
| --- | --- | --- | --- | --- | --- |
| `GET /api/workspaces/{workspaceId}/dashboard` | Summary income/expense/net/count, wallet total, category/jar breakdowns, comparison, recent transactions, member breakdown | Month plus comparison mode | Yes, membership verified | Dashboard and cross-module tests | Reuse formulas for overview/spending. |
| `GET /api/workspaces/{workspaceId}/dashboard/categories` | Expense by category for year/month | Month | Yes | Dashboard tests | Reuse for spending breakdown. |
| `GET /api/workspaces/{workspaceId}/dashboard/jars` | Expense by jar for year/month | Month | Yes | Dashboard tests | Reuse for jar cards; needs budget/threshold layer. |
| `GET /api/workspaces/{workspaceId}/dashboard/comparison` | Expense comparison | Month | Yes | Dashboard tests | Reuse for simple spike comparisons. |
| `GET /api/workspaces/{workspaceId}/planning/actually-spendable` | Available ledger, reserves, recurring obligations, exclusions, warnings, formula | Current month or custom | Yes | Planning tests | Reuse for actually spendable insight. |
| `POST /api/workspaces/{workspaceId}/voice-query/ask` | Read-only totals, top categories, largest expense, debt summary, actually spendable | Intent-specific | Yes | Voice query tests | Reuse as proof of read-only metric patterns, not as public insight API. |
| Transaction repository aggregates | Sum/count expenses/income, category/jar aggregation, largest expense | Date ranges | Query filters workspace | Transaction/voice tests | Move into dedicated insight query layer in P12B. |
| Obligation occurrence inbox/planning queries | Pending obligations by due date and amount | Date ranges | Query filters workspace | Obligation/planning tests | Reuse for upcoming obligations and action items. |
| Reserve repositories | Active reserved amount by module | Current aggregate | Query filters workspace | Planning tests | Reuse for actually spendable and reserve warning cards. |

## Metric Availability

| Metric | Can compute today? | Current source | Correctness notes |
| --- | --- | --- | --- |
| Total income by day/week/month | Partial | Transaction aggregates and dashboard totals | Month exists; day exists through voice query; week needs a dedicated date-range query wrapper. Include no-wallet income. Exclude debt movements, transfers, deleted, drafts, void/planned. |
| Total expense by day/week/month | Partial | Transaction aggregates and dashboard totals | Month/day exists; week needs wrapper. Use `EXPENSE` only, not debt movement expense-like types. |
| Net cashflow | Yes | Dashboard totals | Formula: statistical income minus statistical expense. Not wallet delta. |
| Spending by category | Yes | Dashboard category breakdown, transaction repository | Missing category is currently absent from dashboard category join; insight should explicitly group missing category. |
| Spending by jar | Partial | Dashboard jar breakdown, jar repository aggregates | Works only when expense category has jar. Missing jar/category needs explicit group. |
| Income by source | Partial | Transaction `incomeSource`, `IncomeSource` | Schema supports it; no dedicated dashboard endpoint yet. Need P12B query. |
| Wallet balance | Yes | `WalletBalanceService` | Correctly uses posted, non-deleted, wallet-affecting transaction types and transfers. |
| No-wallet income total | Partial | Transaction query needed | Schema supports `INCOME` with null wallet. Need dedicated query and card. |
| Upcoming obligations | Yes | `ObligationOccurrenceRepository.findPendingPayablePlanningOccurrences` | Known amount only; variable amount must produce data quality warning. |
| Reserved funds | Yes | Active reserve repository sums | Planning has known overlap warning between reserve modules. |
| Actually spendable | Yes | `PlanningService.actuallySpendable` | Formula available; payable debt excluded unless represented as obligation. |
| Debt receivable/payable impact | Partial | Debt rows and debt payments | Voice query has native debt summary. Need dedicated repository/query service and due-date behavior. |
| Abnormal spending vs baseline | Partial | Dashboard comparison/category changes | Current dashboard compares current vs previous period; trailing 3 completed months baseline needs P12B/P12C query. |
| Missing info drafts/transactions | Partial | Transaction nullable fields, voice/receipt draft statuses | Need exact definition: missing category/wallet for posted records, no-wallet income, unconfirmed drafts needing review. |
| Historical vs current ledger distinction | Yes | `isHistorical`, `affectsWalletBalance`, `sourceType=EXCEL_MIGRATION` | Current wallet balance excludes non-affecting historical rows. Insights must decide when analytics include historical rows and label it. |

## Correctness Risks

- Historical imports could be double-counted if an insight mixes analytics totals with wallet-balance totals.
- Unconfirmed voice or receipt drafts could be mistaken for real spending if future query code reads draft tables as ledger data.
- Transfers and debt movement transaction types could be counted as income/expense if future code uses wallet balance deltas instead of statistical transaction type rules.
- No-wallet income could be incorrectly added to wallet balance; it is income for stats only.
- Dashboard category/jar joins can hide uncategorized expense rows if not handled by a left join.
- Payable debts are not currently subtracted from actually spendable unless modeled as recurring obligations.
- Reserve modules can overlap; planning already warns that it does not infer movement between wallets and reserves.
- Large transaction histories need indexed aggregate queries. Avoid loading all transactions into application memory for insight cards.
- Source evidence from voice/receipt confirms is useful, but drafts remain non-ledger until confirmed.

## Gaps To Close

| Gap | Why it matters | Proposed phase |
| --- | --- | --- |
| Dedicated insight query layer | Avoid duplicating dashboard/voice/planning query logic and subtle filters. | P12B |
| Date-range wrappers for day/week/month/custom | Insight endpoints need consistent period parsing. | P12B |
| Income by source aggregate | Needed for income source shift and source breakdown. | P12B |
| No-wallet income aggregate | Needed for action item and data quality card. | P12B/P12E |
| Uncategorized/unjarrred expense grouping | Needed for honest spending totals. | P12B/P12C |
| Trailing baseline query | Needed for spending spike and anomaly rules. | P12C |
| Jar overspend threshold contract | Jars have allocation percent but no monthly budget amount. | P12C |
| Payable debt due integration | Debt due dates exist but planning excludes standalone debts. | P12D/P12E |
| Missing transaction info definition | Need stable action item codes. | P12E |
| Insight response DTO and endpoints | No insight API exists yet. | P12F |

## Reusable Patterns

- `DashboardService` shows workspace-scoped, date-aware aggregate response shape.
- `TransactionRepository` has tested aggregate queries for posted income/expense/category/jar/largest expense.
- `WalletBalanceService` is the authoritative wallet-balance engine.
- `PlanningService.actuallySpendable` already returns formula, warnings, exclusions, assumptions, and freshness.
- `VoiceQueryService` proves read-only question answering can be deterministic and grounded without AI.
- Existing receipt/voice session contracts reinforce draft-first behavior and source evidence.

## P12A Conclusion

Financial Insight can start without schema changes if P12B first centralizes query rules. Most core metrics exist, but first-version insights must be deterministic, evidence-backed, read-only, and explicit about exclusions.
