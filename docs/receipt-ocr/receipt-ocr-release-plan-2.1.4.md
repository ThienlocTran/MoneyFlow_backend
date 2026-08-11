# Receipt OCR Release Plan 2.1.4

Status: planned. P11F receipt draft confirm is implemented.

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
- P11B: session repository/service/controller integration tests; receipt and transaction targeted Maven tests.
- P11C: provider selection, disabled/mock/Azure-placeholder behavior, OCR persistence, workspace isolation, no transaction side effect.
- P11D: Azure provider tests using mocked HTTP/fake local HTTP server, no real provider call.
- P11E: parser/draft builder tests for receipts, missing OCR, category hints, workspace isolation, idempotent rebuild, no transaction side effect.
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

P11G - Receipt OCR 2.1.4 backend release lock.

## P11B Delivered

- Added `receipt_sessions` table.
- Added receipt session create endpoint.
- Added receipt image upload endpoint.
- Added receipt session detail endpoint.
- Added image validation for missing/empty, unsupported type, and size.
- Added disabled/success/failure storage statuses.
- Added backend-only Cloudinary receipt image storage, disabled by default.
- Added integration tests for create, upload, storage status, validation, workspace isolation, missing session, and no transaction side effect.

Known limitations:

- OCR not implemented yet.
- Receipt draft builder not implemented yet.
- Confirm executor not implemented yet.
- UI not implemented in this phase.
- HEIC/HEIF support deferred.

## P11C Delivered

- Added session OCR run endpoint.
- Reused receipt OCR provider abstraction for session OCR.
- Added `azure_document_intelligence` placeholder provider for P11D.
- Expanded deterministic mock OCR behavior by filename.
- Persisted OCR result fields on `receipt_sessions`.
- Added OCR text normalization to NFC and LF line breaks.
- Added tests for mock success, no image, provider none, Azure placeholder, empty OCR, Unicode normalization, workspace isolation, rerun, and no transaction side effect.

Known limitations:

- Mock OCR is deterministic and not real OCR.
- Azure Document Intelligence provider is placeholder-only until P11D.
- Receipt draft builder not implemented yet.
- Confirm executor not implemented yet.
- UI not implemented in this phase.

## P11D Delivered

- Implemented `azure_document_intelligence` provider behind backend config.
- Chosen Azure Document Intelligence REST API version: `2024-11-30`.
- Default model: `prebuilt-receipt`.
- Request strategy: send image bytes when available; for receipt sessions, send stored image URL as `urlSource` because P11B stores URL metadata, not image bytes.
- Poll strategy: read `Operation-Location`, poll until `succeeded`, `failed`, timeout, or max attempts.
- Mapped raw text, merchant, date, total, currency, confidence warning, missing total/date warnings.
- Mapped provider errors to stable OCR warning codes.
- Added mocked HTTP provider tests and fake-server session integration tests.
- Verified no OCR transaction side effect.
- No Azure SDK or new dependency added.

Known limitations:

- Live Azure OCR UAT is not executed unless real env is configured locally.
- Receipt draft builder not implemented yet.
- Confirm executor not implemented yet.
- UI not implemented in this phase.
- OCR may miss totals/date/merchant; backend stores raw text and warnings, not fake values.

## P11E Delivered

- Added `receipt_session_drafts` table.
- Added receipt draft entity/repository/response.
- Added `POST /api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts`.
- Receipt detail now includes draft list.
- Draft builder creates one primary expense review draft from OCR fields/text.
- Amount parsing prefers structured total, then total-marker OCR text, then largest plausible amount with `RECEIPT_TOTAL_INFERRED`.
- Date parsing uses structured date, then simple OCR date formats.
- Category is hint-only.
- Wallet is never guessed.
- Rebuild is idempotent for P11E: current session drafts are replaced, not duplicated.
- Added integration tests for structured OCR, raw total parsing, multiple amounts, inferred totals, date parse, missing OCR, category/wallet behavior, workspace isolation, and no transaction side effect.

Known limitations:

- UI not implemented yet.
- Category ID resolution is deferred.
- Wallet remains null until user/client supplies it.
- Line-item split into multiple transactions is deferred.

## P11F Delivered

- Added `POST /api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts/{draftId}/confirm`.
- Confirm creates a normal posted expense through `TransactionService.createWithReceiptSource(...)`.
- Added receipt transaction traceability: `receipt_session_id`, `receipt_session_draft_id`.
- Added receipt draft confirm metadata: `confirmed_entity_type`, `confirmed_entity_id`, `confirmed_at`.
- Added `RECEIPT` transaction source type.
- Confirm validates amount, date, wallet, category, draft/session ownership, and workspace-scoped references.
- Confirm warning responses do not create transactions.
- Confirm is idempotent and replays the existing transaction after double submit.
- Draft status becomes `CONFIRMED`; session status becomes `PARTIALLY_CONFIRMED` or `CONFIRMED`.
- Upload, OCR, and draft build still do not create transactions.
- Added receipt confirm integration tests for success, missing amount/wallet/category, idempotency, partial status, workspace isolation, invalid references, and no-auto-save regression.

Known limitations:

- No frontend UI yet.
- Receipt line items are not split into separate transactions.
- Azure OCR quality still requires user review.
- Storage disabled means transaction can save without image playback.
- Category and wallet must be supplied by the client/user before confirm.
