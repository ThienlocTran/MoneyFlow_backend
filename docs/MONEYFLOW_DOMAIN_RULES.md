# MoneyFlow Domain Rules

Last consolidated: 2026-07-23.

These rules define financial meaning for MoneyFlow. If code and docs conflict, code is the current runtime behavior; mark the conflict before changing behavior.

## Transactions

A transaction is a financial event in a workspace.

Runtime transaction types:

- `INCOME`
- `EXPENSE`
- `TRANSFER`
- `LOAN_DISBURSEMENT`
- `LOAN_COLLECTION`
- `BORROWING_RECEIPT`
- `BORROWING_REPAYMENT`
- `ADJUSTMENT`

Rules:

- `amount` is always positive.
- Status values are `DRAFT`, `PLANNED`, `POSTED`, `VOID`.
- Only `POSTED` transactions should affect current financial totals unless a feature explicitly includes planned/draft data.
- Soft-delete is `deleted_at`; deleted transactions are excluded from normal list/summary calculations.
- Source types identify provenance: `MANUAL`, `QUICK_BUTTON`, `QUICK_TEXT`, `VOICE`, `EXCEL_MIGRATION`, `SYSTEM`.
- Historical Excel rows can be stored for analysis without changing wallet balance: `is_historical=true`, `affects_wallet_balance=false`.
- Manual, quick button, quick text, and voice transactions must affect wallet balance.
- Transfers require `transfer_details.source_wallet_id` and `destination_wallet_id`; source and destination must differ.
- Debt movement types are separate from normal income/expense. Do not classify loan receipts as living income or loan disbursements as spending expense unless product rules explicitly change.

## Wallets

A wallet answers: where is money stored?

Examples: cash, bank, e-wallet, saving wallet, other.

Rules:

- A wallet is not an income source.
- Current wallet balance is derived from opening balance plus posted ledger movements that affect wallet balance.
- `include_in_total=false` wallets are excluded from default selected-wallet planning totals.
- Historical imports must not be replayed blindly to compute live balances.
- Wallet snapshots are reconciliation evidence. A snapshot is not an income transaction.

## Income Sources

An income source answers: where did money come from?

Runtime source types include `SALARY`, `FREELANCE`, `BUSINESS`, `GIG_PLATFORM`, `INVESTMENT`, `RENTAL`, `OTHER`.

Rules:

- Income source is separate from wallet and category.
- Income sources can be `ACTIVE` or `ARCHIVED`.
- Transactions can link to `income_source_id` or `related_income_source_id`.
- Active income source names are unique per workspace.
- Archiving a source must not erase historical transaction links.

## Debts / Công Nợ

Debt models money owed between the user/workspace and a counterparty.

Directions:

- `RECEIVABLE`: người ta nợ mình, money the user expects to receive.
- `PAYABLE`: mình nợ người ta, money the user expects to pay.

Fields:

- `principal_amount`: original principal.
- `debt_payments.amount`: payments against principal.
- `debt_status`: `OPEN`, `PARTIAL`, `PAID`, `CANCELLED`.

Rules:

- Principal is positive.
- Payments are positive.
- API remaining amount is zero for `PAID` and `CANCELLED`.
- For open/partial debts, remaining amount is `max(principal - sum(payments), 0)`.
- Paid debt payment rows are history; they should not be treated as current receivable/payable remaining.
- Debt payments should not distort normal income/expense dashboards.
- Current schema has no debt soft-delete column. Missing debt rows cannot be recovered from a soft-delete flag unless schema changes.

## Student Loans

Student loans are separate from generic công nợ.

Rules:

- Student loan fields include original principal, current principal, annual interest rate, minimum monthly payment, planned extra monthly payment, start date, and target payoff date.
- Status values are `ACTIVE`, `PAUSED`, `PAID_OFF`, `ARCHIVED`.
- Student loan strategy/projection is advisory.
- Planning currently excludes student loan advisory commitments from actually-spendable until a confirmed deduplication contract exists between student loans and recurring obligations.

## Savings Goals

A savings goal tracks progress toward a target.

Rules:

- Target amount is positive.
- Status values are `ACTIVE`, `PAUSED`, `COMPLETED`, `ARCHIVED`.
- Ledger entries are `CONTRIBUTION` or `RELEASE`.
- Contribution amount deltas are positive; release amount deltas are negative.
- Active savings goal reserved balances are included in planning reserves.
- Paused, completed, and archived goals are excluded from active reserve calculation unless feature rules change.

## Sinking Funds

A sinking fund reserves money for a known future expense.

Rules:

- Target amount may be null or positive.
- Status values are `ACTIVE`, `PAUSED`, `COMPLETED`, `ARCHIVED`.
- Allocations are `ALLOCATE` or `RELEASE`.
- Allocation amount deltas are positive; release amount deltas are negative.
- Active sinking fund reserved balances are included in planning reserves.
- Completed or archived sinking funds are read-only for allocations.

## Emergency Fund

Emergency fund tracks reserve against months of expense.

Rules:

- One emergency fund plan per workspace.
- `target_months` is positive.
- Current basis mode is `MANUAL`.
- `manual_monthly_expense` is positive.
- Plan status is `ACTIVE` or `PAUSED`.
- Ledger entries are `ALLOCATE` or `RELEASE`.
- Active emergency fund reserve is included in planning reserves.
- If a workspace has no plan, `GET /emergency-fund` returns success with null data. It must not become a 404 loading loop.
- Workspace not found and access denied remain distinct errors.

## Recurring Obligations

Recurring obligations model expected future payable or receivable commitments.

Rules:

- Template direction is `PAYABLE` or `RECEIVABLE`.
- Amount mode is `FIXED` or `VARIABLE`.
- Frequency is `WEEKLY`, `MONTHLY`, or `YEARLY`; interval count is at least 1.
- Template status is `ACTIVE`, `PAUSED`, or `ARCHIVED`.
- Occurrences have statuses including due/confirmed/skipped/snoozed/reopened behavior in service code.
- Confirming an occurrence creates a real transaction only after user confirmation.
- Variable amount occurrences may make planning uncertain and should surface warnings.

## Planning Formula

Current actually-spendable logic:

```text
 availableLedger
- active reserve ledgers
- known upcoming obligations
= actuallySpendable
```

Definitions:

- `availableLedger`: current wallet ledger balance for selected active wallets.
- Selected wallets default to active wallets with `include_in_total=true`.
- Reserve ledgers: active sinking funds + active savings goals + active emergency fund.
- Known upcoming obligations: upcoming obligations in the selected planning horizon.
- Advisory commitments: reported separately when not included in the numeric formula.

Current assumptions and risks:

- Reserve ledgers may overlap because the app does not infer money movement between wallets or reserve modules.
- Negative reserve aggregates are excluded from numeric spendable calculation and surfaced as warnings.
- Student loans are advisory only in planning until deduplication is defined.

## Totals Glossary

- `original`: the original principal/target/planned amount before payments or releases.
- `principal`: same as original for debts and loans unless a module distinguishes original/current principal.
- `paid`: total payments recorded against a debt or obligation.
- `remaining`: amount still open after payments/releases. For paid/cancelled debts, API remaining is zero.
- `receivable`: money owed to the user/workspace.
- `payable`: money owed by the user/workspace.
- `reserved`: money allocated to funds/goals/emergency reserve.
- `availableLedger`: wallet balance before subtracting planning reserves/commitments.
- `actuallySpendable`: available ledger minus recognized reserves and upcoming obligations.

## Hard Data Rules

- Do not add mock, fake, sample, fallback, or random financial runtime data.
- Empty backend data means empty state.
- Backend failure means error state.
- Do not run import, repair, migration, or destructive SQL without explicit approval.
- Import/repair must start with backup/export, dry-run, exact row list, and validation plan.
