# Receipt OCR Release Plan 2.1.4

Status: planned. P11A is docs/audit only.

## Product Guardrail

- Product principle: lazy capture without silent ledger mutation.
- Domain objects touched: receipt session, receipt draft, transaction.
- Data integrity rule: only confirmed, posted, non-deleted transactions affect wallet balance.
- Transaction behavior: P11A creates none.
- User confirmation required: yes.

## Scope

Included:

- Backend receipt session/upload foundation.
- OCR provider abstraction using current provider pattern.
- Azure Document Intelligence provider behind backend config.
- Draft builder/parser from OCR/manual text.
- Confirm executor with transaction evidence link.
- Focused backend docs and tests.

Deferred:

- Frontend production receipt UI beyond current reference flow.
- Flo assistant.
- AI insights.
- Voice V2.
- Category/Jar redesign.
- Line-item ledger posting unless already supported safely.
- Bank sync or automatic external balance import.

## Phase Table

| Phase | Goal | Deliverable | Ledger mutation |
| --- | --- | --- | --- |
| P11A | Spec + audit | Audit, contract, release plan, release stub | No |
| P11B | Receipt session/upload foundation | Session model/API, image validation/storage status | No |
| P11C | OCR provider abstraction + mock | Stable provider contract, deterministic tests | No |
| P11D | Azure Document Intelligence provider | Real OCR provider behind config | No |
| P11E | Receipt draft builder/parser | Durable review drafts from OCR/manual text | No |
| P11F | Confirm executor + transaction evidence link | Idempotent confirm creates transactions | Yes, confirm only |
| P11G | Release lock | Release notes, validation, UAT checklist | No new feature work |

## Acceptance Criteria

- No receipt endpoint posts a transaction before explicit confirm.
- Workspace membership is verified for every session/draft/read/write.
- Image upload validates count, size, content type, empty file.
- Storage disabled/failed status is visible and non-blocking when text/OCR can proceed.
- OCR provider errors map to stable warning codes.
- Draft response exposes missing amount/wallet/category clearly.
- Confirm is idempotent and cannot duplicate transactions.
- Confirmed transaction is traceable to receipt session/draft.
- Runtime contains no fake receipt financial data.
- Touched docs contain real UTF-8 and no secrets.

## Validation Strategy

- P11A: `git diff --check`, scoped mojibake scan, scoped secret scan.
- P11B: session repository/service/controller integration tests.
- P11C: provider selection, disabled/mock behavior, limit validation tests.
- P11D: Azure provider unit tests using mocked HTTP, no real provider call.
- P11E: parser/draft builder tests for Vietnamese receipts, missing fields, category hints.
- P11F: confirm integration tests for success, missing required fields, idempotent replay, source traceability.
- P11G: release checklist and targeted test suite.

## Manual UAT Targets

- Paste receipt text and get expense draft.
- Upload image with OCR disabled and see paste-text fallback.
- Upload supported image with mock/Azure provider and get review draft.
- Upload unsupported file type and get stable validation error.
- Upload oversized image and get stable validation error.
- Confirm complete receipt draft and see one posted transaction.
- Repeat confirm and verify no duplicate transaction.
- Disable storage and verify review/confirm can continue from OCR/manual text.

## Risk Register

| Risk | Mitigation |
| --- | --- |
| OCR guesses wrong total/date | Keep OCR as evidence and require review. |
| Storage provider unavailable | Record storage warning/status; continue if text is usable. |
| Duplicate confirm | Persist confirmed entity link and replay idempotently. |
| Receipt treated as normal debt/savings event | Default to expense only; unsupported types remain review/manual. |
| Secrets leak through provider config/logs | Backend-only env, redacted logs, no provider URL/secrets in responses. |
| Mojibake in Vietnamese text | Scan touched docs before completion. |

## Next Queue Item

P11B - receipt session/upload backend foundation.
