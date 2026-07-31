# Backend Domain Playbook

## MoneyFlow Domain Rules

- Wallet = where money is stored.
- Income Source = where money came from.
- Jar/Hũ = purpose bucket.
- Category belongs to Jar.
- Transaction amount is positive; type controls effect.
- Only `POSTED` non-deleted transactions affect totals.
- Historical Excel rows are analytics-only if imported as historical.
- Wallet snapshots are reconciliation/opening evidence, not income.
- Voice must draft first; no silent posting.
- Income without explicit wallet must not invent wallet.
- Savings, debt, and unknown voice intents must not be committed as normal expense.

## Debt Movement Rules

Debt movement is not normal income/expense:

- `LOAN_DISBURSEMENT` decreases wallet and increases receivable.
- `LOAN_COLLECTION` increases wallet and decreases receivable.
- `BORROWING_RECEIPT` is not income.
- `BORROWING_REPAYMENT` is not normal expense.

## Backend Rules

- Preserve API compatibility.
- Add focused tests for domain behavior.
- Do not change unrelated financial modules.
- Do not expose secrets.
- Use `BigDecimal` carefully.
- Keep Vietnamese text real UTF-8.
- Verify workspace scope and membership before workspace data access.
- Keep preview/suggestion endpoints read-only unless the contract explicitly says otherwise.

## Repo Recon Rule

Use exact `rg` search first for class, method, route, DTO, repository method, error code, and env key. Use Repo Recon Skill or semantic search only when feature location or behavior flow is unclear. Read current code contracts before editing.
