# Voice Regression Matrix

## Purpose

This matrix locks the backend contract for voice and receipt interpretation. Parse and interpret endpoints are review-only: they may create voice draft records where the voice-review flow requires evidence, but they must not post transactions, mutate wallet balances, or create debt/payment rows.

## Routes

- `POST /api/workspaces/{workspaceId}/voice-review/parse`
- `PATCH /api/workspaces/{workspaceId}/voice-review/{voiceRecordId}/draft`
- `POST /api/workspaces/{workspaceId}/voice-review/{voiceRecordId}/confirm`
- `POST /api/workspaces/{workspaceId}/voice-command/interpret`
- `POST /api/workspaces/{workspaceId}/voice-query/ask`
- `POST /api/workspaces/{workspaceId}/receipt-review/parse`
- `POST /api/workspaces/{workspaceId}/receipt-review/parse-with-images`

## Matrix

| User phrase | Expected mode | Expected candidate type | Must not become | Ledger mutation on parse/interpret? | Confirm behavior | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| Tôi ăn hết 50.000 | `SINGLE` / `TRANSACTION_REVIEW` | `EXPENSE` | income, debt, snapshot | No posted transaction | Normal confirm only after required fields | Category/wallet may be required. |
| Tôi nhận lương 800k vào Cake | `SINGLE` / `TRANSACTION_REVIEW` | `INCOME` | expense, debt | No posted transaction | Requires valid income source and wallet | Wallet may be matched from text. |
| Hôm nay tôi kiếm được 800 | `SINGLE` / `INCOME_FACT_REVIEW` | `INCOME_FACT` | wallet income with guessed wallet | No posted transaction | Confirm records income fact without wallet effect | `walletId` is not required. |
| MB còn 4 triệu 8 | `SINGLE` / `WALLET_SNAPSHOT_REVIEW` | `WALLET_SNAPSHOT` | income, expense | No posted transaction | Snapshot confirm currently unsupported | Reconciliation data only. |
| Tôi gửi tiết kiệm 72.000 | `SINGLE` / review | `SAVINGS_ALLOCATION` | expense | No posted transaction | Unsupported confirm, no transaction | Source wallet and target fund are required later. |
| Tôi cho Nam mượn 500k | `SINGLE` / `DEBT_DRAFT` | `LOAN_DISBURSEMENT` | expense | No transaction/debt/payment | Unsupported confirm, no transaction | Direction `RECEIVABLE`. |
| Nam trả tôi 200k | `SINGLE` / `DEBT_DRAFT` | `LOAN_COLLECTION` | income | No transaction/debt/payment | Unsupported confirm, no transaction | Needs existing debt and destination wallet. |
| Tôi mượn Nam 1 triệu | `SINGLE` / `DEBT_DRAFT` | `BORROWING_RECEIPT` | income | No transaction/debt/payment | Unsupported confirm, no transaction | Direction `PAYABLE`. |
| Tôi trả nợ Nam 100k | `SINGLE` / `DEBT_DRAFT` | `BORROWING_REPAYMENT` | expense | No transaction/debt/payment | Unsupported confirm, no transaction | Needs existing debt and source wallet. |
| Tháng này tôi tiêu bao nhiêu? | `READ_ONLY_QUERY` | none | draft | No draft/transaction | Not applicable | Query only. |
| Nam còn nợ tôi bao nhiêu? | `READ_ONLY_QUERY` | none | debt payment draft | No draft/transaction/payment | Not applicable | Debt summary answer. |
| Hôm nay kiếm được 800, MB còn 4tr8, tôi ăn 50k | `MULTI_DRAFT_REVIEW` | `INCOME_FACT`, `WALLET_SNAPSHOT`, `EXPENSE` | single wrong draft | No posted transaction | Per-draft confirm only | Bulk save must respect missing fields. |
| Tôi ăn 50k, tháng này tiêu bao nhiêu? | `NEEDS_CLARIFICATION` | none | partial post | No draft/transaction | Not applicable | Mixed query + mutation is intentionally blocked. |
| Receipt text with total 40.000 | `RECEIPT_REVIEW` | `EXPENSE` | posted transaction | No posted transaction | Future explicit receipt confirm only | Receipt parse is review-only. |
| Receipt image only, OCR disabled | receipt parse response | none | fake OCR text | No posted transaction | Not applicable | Returns OCR disabled / needs text warnings. |

## Safety Invariants

- Parse and interpret endpoints never create `POSTED` transactions.
- Read-only query routes do not create voice records or transaction drafts.
- Debt candidates return `countsAsIncome=false` and `countsAsExpense=false`.
- Savings/fund candidates return `countsAsExpense=false`.
- Wallet snapshots are not income/expense and do not affect wallet balance on parse.
- Income facts without wallet do not require `walletId` and do not affect wallet balance.
- Warnings use stable codes; UI should map them to user-friendly copy.

## Frontend Notes

- Render unknown candidate types safely.
- Do not show raw warning codes as final copy.
- Disable save when `canConfirm=false` or required fields are missing.
- Treat parse/interpret as drafts only; confirmation is a separate explicit action.
