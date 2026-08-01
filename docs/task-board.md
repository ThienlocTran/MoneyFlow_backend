# MoneyFlow Task Board

Purpose: keep parallel agents from colliding. Update the owner and status line when you start and
when you finish. One task = one owner = one file scope.

Last updated: 2026-07-28.

## Ownership legend

- **Codex** — backend, API contracts, business logic, backend tests, release.
- **Antigravity** — UI/UX, layout, visual polish.
- **Kiro** — review, integration, focused tasks.

## In progress

| Task | Owner | Repo | File scope | Status |
| --- | --- | --- | --- | --- |
| Voice audio status badge alignment | Antigravity | frontend | `views/TransactionsView.vue`, closing voice card, `types/closing.ts` | working tree dirty as of last check, do not touch these files |

## Done in the current wave

| Task | Owner | Repo | Commit | Notes |
| --- | --- | --- | --- | --- |
| Planning: expose actually spendable breakdown | Codex | backend | `e412291` | adds formula/breakdown/warningDetails/exclusions/dataFreshness |
| Transactions: expose human audit history | Codex | backend | `051085a` | actionLabel, changes[], context, technicalPayload |
| Daily closing: voice balance preview | Codex | backend | `323872a`, `16c8a29` | read-only preview; `16c8a29` fixes the "và" connector split |
| Planning UI: map backend breakdown | Kiro | frontend | `deab4c7` | formula, breakdown groups, freshness, warnings/exclusions with fallback |
| Transactions UI: use human audit DTO | Antigravity | frontend | `29cf5d8` | panel reads actionLabel/changes/context, keeps technical toggle |
| Daily closing UI: fill draft from voice preview | Kiro | frontend | `a4dd269` | preview card + apply-to-form, never auto-saves |
| Suggestions: quick entry foundation | Kiro | backend | `464c1bc` | deterministic rules, read-only, `*Suggestion*Tests` |

## Open, not started

| Task | Suggested owner | Repo | Scope | Notes |
| --- | --- | --- | --- | --- |
| Transactions deep link detail fetch | unassigned | frontend | `TransactionsView.vue`, transaction service/store | `/transactions?id=<id>` currently only shows a banner when the row is outside the loaded page; fetch the detail instead |
| Suggestion chips in review UI | unassigned | frontend | quick-entry/voice review components, suggestion service/types | consumes `POST /api/workspaces/{id}/suggestions/quick-entry`; chips fill fields locally, never auto-confirm |
| Mobile/PWA shell hardening | unassigned | frontend | `index.html`, manifest, `App.vue` shell, global CSS | high collision risk with UI owner; needs exclusive lock |
| Planning: decide whether payable debts enter the formula | unassigned | backend | planning service | today `payableDebtsTotal` is always 0 and the exclusion says so; product decision needed |
| Transaction audit access policy | unassigned | backend + frontend | audit service, transactions view | API is OWNER-only; UI shows the history button to every role |

## Known product decisions still pending

- Official cutover date and final opening balance per wallet.
- Whether the six jar names/percentages are fixed or a starting template.
- Whether EDITOR may read transaction audit history.

## Rules for editing this board

- Add a row before you start, not after.
- If you find someone else's uncommitted changes in your file scope, stop, report, and note it here.
- Do not delete history rows; move them to "Done" with the commit hash.
