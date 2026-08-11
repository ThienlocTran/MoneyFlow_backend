# Receipt OCR Backend Audit

Status: P11A audit only. No feature implementation in this phase.

## Product Guardrail

- Product principle: fast manual finance capture with correctness and traceability.
- Domain objects touched: receipt review draft, future receipt session, future transaction evidence link.
- Data integrity rule: OCR output is evidence only; no ledger mutation before user confirmation.
- Transaction behavior today: current receipt endpoints create review drafts only.
- User confirmation required: yes, before any posted transaction.

## Current Backend Foundation

| Area | Current state |
| --- | --- |
| Package | `src/main/java/com/moneyflowbackend/receipt` exists. |
| Controller | `ReceiptReviewController` exposes `/api/workspaces/{workspaceId}/receipt-review`. |
| Endpoints | `POST /parse`; `POST /parse-with-images`. |
| DTOs | `ReceiptReviewParseRequest`, `ReceiptReviewParseResponse`, `ReceiptReviewSource`. |
| Parser | `ReceiptTextParser` extracts merchant, receipt date, total amount, line amounts. |
| Provider abstraction | `ReceiptOcrProvider`, `ReceiptOcrService`, `ReceiptOcrProperties`, provider enum/status/result/input classes. |
| Providers | `none`, `mock`, `external_http`. |
| Image validation | Max image count, max image bytes, MIME allow-list: JPEG, PNG, WEBP. |
| Image storage | Not implemented for receipts. Attachment storage status is currently `NOT_STORED`. |
| Cloudinary | No receipt Cloudinary integration. Voice audio has Cloudinary/S3/disabled storage patterns only. |
| Confirm executor | Not implemented for receipts. |
| Transaction integration | No receipt source type, no receipt session link, no receipt confirm path. |
| Workspace security | `ReceiptReviewService` checks active workspace membership before parsing. |
| Tests | Receipt parser/review integration, multipart validation, mock OCR integration, external HTTP provider tests. |

## Existing Endpoint Behavior

`POST /api/workspaces/{workspaceId}/receipt-review/parse`

- Accepts text payload.
- Requires workspace membership.
- Produces `mode=RECEIPT_REVIEW`.
- Produces an expense candidate when total amount exists.
- Adds warnings for missing wallet/category/merchant/date/total.
- Does not create a transaction.

`POST /api/workspaces/{workspaceId}/receipt-review/parse-with-images`

- Accepts multipart `images`, optional `rawText`, `source`, `occurredAtHint`, `walletId`, `timezone`.
- Requires either text or at least one valid image.
- If text is present, skips OCR and parses text.
- If images are present without OCR configured, returns `OCR_NOT_CONFIGURED`.
- If OCR succeeds, parses OCR text as `PHOTO_OCR`.
- Does not store images.
- Does not create a transaction.

## Current Gaps

- No durable `ReceiptSession` or `ReceiptRecord` model.
- No receipt draft entity for review state over time.
- No receipt image storage service or lifecycle states.
- No receipt Cloudinary object-key contract.
- No confirm executor.
- No idempotent receipt confirm behavior.
- No transaction evidence link such as `receipt_session_id`.
- No `TransactionSourceType.RECEIPT`.
- No API to load session/detail state.
- No edit-draft API.
- No storage-disabled behavior for receipts beyond `NOT_STORED` attachment metadata.
- No audit trail for OCR/provider decisions.
- No line-item confirmation contract.

## Reusable Voice V1 Patterns

- `VoiceSession` separates capture, interpretation, draft review, confirm.
- Voice confirm is idempotent by persisted draft status/entity link.
- Voice no-auto-post rule: create/update/transcribe/interpret do not mutate ledger.
- Voice provider abstraction maps provider failures to stable warning codes.
- Voice storage treats audio evidence independently from transaction validity.
- Voice transaction traceability uses source fields and session/draft links.
- Voice workspace-scoped endpoints verify active membership before state access.

## Risks

- Existing receipt Java messages and existing receipt OCR docs contain mojibake; P11A does not edit runtime code.
- Current external HTTP provider uses small custom JSON extraction; acceptable for current narrow tests, but Azure provider should use reviewed parsing.
- `mock` provider is deterministic test support and must not become fake runtime OCR data.
- Receipt storage failure must stay non-blocking when OCR/review can proceed.
- Confirm executor must preserve expense wallet/category rules and positive amounts.
