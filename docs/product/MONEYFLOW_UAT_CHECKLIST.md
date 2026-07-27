# MoneyFlow UAT Checklist

Last synced: 2026-07-23.

Use staging-only data. Do not use real credentials in notes. Do not mutate real financial data.

## Setup

- Confirm backend branch/environment under test.
- Confirm frontend branch/environment under test.
- Confirm `SPRING_PROFILES_ACTIVE=staging`.
- Confirm `VITE_API_BASE_URL` points to staging backend.
- Confirm backend health:
  - `GET /api/public/health/live`
  - `GET /api/public/health/ready`
- Confirm no wildcard CORS with credentials.
- Confirm logs do not expose secrets.

## Authentication

- Register or use a staging-only account.
- Log in.
- Refresh page and confirm session remains valid.
- Log out and confirm protected pages redirect to login.
- Test Google login only with staging/local OAuth config.
- Confirm new Google users receive a personal workspace.
- Confirm username update works.
- Confirm invite search does not expose email unnecessarily.

## Workspace

- Confirm current workspace selector shows an accessible workspace.
- Switch workspace.
- Confirm Dashboard, Transactions, Wallets, Categories, and Jars reload for selected workspace.
- Confirm inaccessible cached workspace falls back safely after reload.

## Dashboard

- Open dashboard.
- Change month.
- Change comparison mode.
- Confirm totals, category movement, wallet balances, and recent transactions render without `null`, `undefined`, `NaN`, `Infinity`, or `[object Object]`.
- Confirm transfer and debt movements are not counted as normal income/expense.
- Confirm important numbers answer: what is this, where did it come from, what should user do next.

## Transactions

- Create one staging-only income.
- Create one staging-only expense.
- Create one staging-only transfer.
- Filter by month, type, wallet, category.
- Search raw input/note.
- Export CSV.
- Confirm CSV excludes audio URLs, storage keys, and secrets.
- Edit a transaction.
- Soft delete a transaction.
- Restore a transaction.
- Open audit history.
- Confirm unauthorized users see friendly permission messages.

## Wallets

- Create a staging wallet.
- Edit wallet.
- Set default wallet.
- Deactivate wallet.
- Confirm protected actions are blocked when business rules require.
- Confirm balances match posted live movements and exclude draft/planned/void/deleted/historical analytics-only rows.

## Categories, Jars, Keywords

- Create a staging jar.
- Create income and expense categories.
- Add keywords.
- Toggle quick action.
- Archive/deactivate a category.
- Confirm viewer role cannot mutate.
- Confirm jar is purpose, not physical wallet.

## Quick Entry

- Parse staging-only text.
- Try `ăn sáng 35k tiền mặt`.
- Try `mẹ cho 500k vào MB`.
- Try `chuyển 200k từ MB sang MoMo`.
- Confirm missing amount, wallet, or category stays draft and cannot save until fixed.
- Confirm preview.
- Save.
- Confirm Dashboard, Transactions, and Wallets refresh.

## Voice Phase 2

- Confirm parse creates draft candidates only.
- Confirm recognized debt/savings/funds/recurring commands are not auto-committed.
- Confirm current batch confirm supports only `INCOME`, `EXPENSE`, `TRANSFER`.
- Confirm debt create/payment candidates remain draft-only.
- Confirm audio upload happens only after confirm.
- Confirm voice transaction works when audio storage is disabled.
- Confirm saved transaction/transcript remain available if audio upload is skipped, disabled, or fails.
- If Cloudinary staging is configured, record short audio, confirm transaction, verify playback, then delete audio.
- Confirm playback/delete require authorization.
- Confirm repeated confirm with same idempotency key does not create duplicates.

## Debt Data Repair Readiness

- Confirm no DB mutation during forensic review.
- Confirm Excel total `GHI SỔ NỢ!E1 = 33,009,000`.
- Confirm current DB/API receivable remaining is 15,115,000 before repair.
- Confirm missing amount is 17,894,000.
- Confirm missing rows list is reviewed by user.
- Confirm repair plan has backup, dry-run, duplicate guard, transactional insert, validate total.
- Confirm root cause timeline remains unresolved unless forensic evidence proves it.

## Security Hardening

- Repeated login/register attempts return rate-limit response.
- Unauthorized workspace/invitation/audit actions show permission messages without stack traces or raw JSON.
- Expired session redirects or prompts login safely.
- Error responses do not expose Java stack traces, SQL details, database URLs, JWT internals, Cloudinary secrets, signed audio URLs, or storage public IDs.
- Response headers include `X-Content-Type-Options`, `X-Frame-Options`, and `Referrer-Policy`.

## Final Checks

- No fake runtime data appears.
- No hardcoded workspace ID is used.
- Empty states are clear.
- Error states are clear.
- No real financial data was created or changed.
- No secrets were copied into notes, screenshots, logs, or docs.
