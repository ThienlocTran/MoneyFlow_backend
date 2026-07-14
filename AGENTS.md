# AGENTS.md

## MoneyFlow Product Source Of Truth Guardrail

Before any MoneyFlow task, read:
- `docs/product/MONEYFLOW_PRODUCT_SOURCE_OF_TRUTH.md`
- `docs/product/MONEYFLOW_TASK_GUARDRAIL.md`

Every task must state:
- Which product principle it affects
- Which domain object it touches
- What data integrity rule applies
- Whether it creates real transactions or only drafts/plans
- Whether user confirmation is required

Hard guardrails:
- Jar is not wallet/category.
- Category belongs to jar.
- Wallet balance must not be recalculated from historical Excel imports unless explicitly approved.
- Recurring fixed commitment must not auto-create transaction without user confirmation.
- Debt movement must not become normal income/expense.
- UI must answer: what is this number, where did it come from, what should user do next.
- No fake/mock/sample/demo runtime data.
