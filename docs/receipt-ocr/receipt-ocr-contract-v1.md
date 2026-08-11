# Receipt OCR Contract V1

Status: backend contract for 2.1.4. P11C OCR provider abstraction and mock provider are implemented; Azure, draft builder, and confirm are not implemented yet.

## Product Guardrail

- Product principle: fewer capture steps, explainable ledger.
- Domain objects touched: receipt session, receipt draft, transaction evidence.
- Data integrity rule: receipt OCR never auto-posts.
- Transaction behavior: draft/session APIs are read/write review state only; confirm APIs are the only ledger mutation.
- User confirmation required: yes.

## Target Flow

1. User uploads or captures receipt image, or pastes receipt text.
2. Backend creates a receipt session scoped to a workspace and user.
3. Backend stores image evidence when storage is configured.
4. Backend runs OCR when configured.
5. Backend builds one or more draft expense transactions.
6. User reviews amount, wallet, category, date, merchant/note.
7. User confirms one draft or eligible drafts.
8. Backend creates posted transactions and links them to receipt evidence.

## Proposed Endpoints

Base path: `/api/workspaces/{workspaceId}/receipt-sessions`

| Endpoint | Purpose | Ledger mutation |
| --- | --- | --- |
| `POST /` | Create receipt session metadata. | No |
| `POST /{sessionId}/image` | Upload one or more receipt images. | No |
| `POST /{sessionId}/ocr` | Run OCR against stored or uploaded image inputs. | No |
| `POST /{sessionId}/drafts` | Build review drafts from OCR/manual text. | No |
| `PATCH /{sessionId}/drafts/{draftId}` | User edits draft fields. | No |
| `POST /{sessionId}/drafts/{draftId}/confirm` | Confirm one draft. | Yes |
| `POST /{sessionId}/confirm-eligible` | Confirm eligible complete drafts. | Yes |
| `GET /{sessionId}` | Load review/debug detail. | No |

Keep existing `/receipt-review/parse` and `/parse-with-images` compatible during migration. They remain stateless preview endpoints unless explicitly replaced.

## P11B Implemented Endpoints

`POST /api/workspaces/{workspaceId}/receipt-sessions`

- Creates a durable receipt session.
- Sets `status=CREATED`.
- Sets `imageStorageStatus=NOT_REQUESTED`.
- Sets `ocrStatus=NOT_REQUESTED`.
- Does not create a transaction.

`POST /api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/image`

- Accepts multipart field `file`.
- Validates empty file, size, and content type before storage.
- Stores image metadata.
- If storage is disabled, returns `imageStorageStatus=STORAGE_NOT_CONFIGURED`.
- If storage succeeds, returns `imageStorageStatus=STORED` and `imageUrl`.
- If storage fails, returns `imageStorageStatus=STORAGE_FAILED`.
- Does not run OCR.
- Does not create a transaction.

`GET /api/workspaces/{workspaceId}/receipt-sessions/{sessionId}`

- Loads receipt session detail.
- Enforces workspace ownership by session ID and workspace ID.

## P11C Implemented OCR

`POST /api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/ocr`

- Runs configured receipt OCR provider.
- Persists `ocrStatus`, `ocrProvider`, `rawOcrText`, `normalizedOcrText`, merchant/date/total metadata, currency, and warnings.
- Does not build receipt drafts.
- Does not create transactions.
- Re-running OCR overwrites the current OCR result.

Provider modes:

- `none`: safe default; returns `ocrStatus=NOT_CONFIGURED` and `OCR_NOT_CONFIGURED`.
- `mock`: deterministic dev/test provider.
- `azure_document_intelligence`: placeholder only; returns `OCR_PROVIDER_NOT_IMPLEMENTED` until P11D.
- `external_http`: retained for existing stateless receipt-review compatibility.

Mock provider behavior:

- Filename containing `coffee` or `cafe`: coffee receipt text, total `25000`.
- Filename containing `fuel` or `xang`: fuel receipt text, total `60000`.
- Filename containing `empty`: empty result mapped to `OCR_EMPTY_TEXT`.
- Filename containing `unicode`: decomposed text used to verify NFC normalization.
- Default: existing mock receipt text, total `40000`.

## Session DTO

Likely `ReceiptSession` fields:

- `id`
- `workspaceId`
- `createdByUserId`
- `status`
- `imageStorageStatus`
- `imageUrl`
- `storagePublicId`
- `ocrStatus`
- `ocrProvider`
- `rawOcrText`
- `normalizedOcrText`
- `merchantName`
- `receiptDate`
- `totalAmount`
- `currency`
- `warnings`
- `createdAt`
- `updatedAt`

## Draft DTO

Likely `ReceiptDraft` fields:

- `id`
- `sessionId`
- `type`, default `EXPENSE`
- `amount`
- `occurredAt`
- `walletId`
- `categoryId`
- `categoryHint`
- `merchantName`
- `note`
- `sourceText`
- `evidenceLines`
- `confidence`
- `warnings`
- `status`
- `confirmedEntityType`
- `confirmedEntityId`
- `confirmedAt`

## Statuses

Session status:

- `CREATED`
- `IMAGE_UPLOADED`
- `IMAGE_STORAGE_FAILED`
- `OCR_PENDING`
- `OCR_SUCCEEDED`
- `OCR_FAILED`
- `DRAFT_READY`
- `NEEDS_REVIEW`
- `PARTIALLY_CONFIRMED`
- `CONFIRMED`
- `CANCELLED`

Image storage status:

- `NOT_REQUESTED`
- `STORAGE_NOT_CONFIGURED`
- `STORED`
- `STORAGE_FAILED`
- `UNSUPPORTED_FORMAT`
- `FILE_TOO_LARGE`
- `DELETED`

OCR status:

- `NOT_REQUESTED`
- `READY`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `NOT_CONFIGURED`

Draft status:

- `NEEDS_REVIEW`
- `READY`
- `CONFIRMED`
- `SKIPPED`
- `UNSUPPORTED`

## Warning Codes

- `RECEIPT_IMAGE_REQUIRED`
- `RECEIPT_STORAGE_NOT_CONFIGURED`
- `RECEIPT_STORAGE_FAILED`
- `OCR_NOT_REQUESTED`
- `OCR_NOT_CONFIGURED`
- `OCR_PROVIDER_NOT_IMPLEMENTED`
- `OCR_PROVIDER_FAILED`
- `OCR_UNSUPPORTED_IMAGE_FORMAT`
- `OCR_FILE_TOO_LARGE`
- `OCR_EMPTY_TEXT`
- `OCR_PROVIDER_TIMEOUT`
- `OCR_PROVIDER_AUTH_FAILED`
- `OCR_PROVIDER_RATE_LIMITED`
- `OCR_LOW_CONFIDENCE`
- `OCR_TOTAL_NOT_FOUND`
- `OCR_DATE_NOT_FOUND`
- `RECEIPT_DRAFT_MISSING_AMOUNT`
- `RECEIPT_DRAFT_MISSING_WALLET`
- `RECEIPT_DRAFT_MISSING_CATEGORY`

Existing preview warnings should be mapped or retained compatibly:

- `RECEIPT_OCR_NOT_CONFIGURED`
- `RECEIPT_OCR_FAILED`
- `RECEIPT_OCR_TEXT_EMPTY`
- `RECEIPT_TEXT_REQUIRED_WHEN_OCR_DISABLED`
- `RECEIPT_TOTAL_NOT_FOUND`
- `RECEIPT_WALLET_NOT_SELECTED`
- `RECEIPT_CATEGORY_NOT_SELECTED`

## Storage Behavior

- Receipt images are evidence, not ledger truth.
- Storage uses backend-only credentials.
- Storage failure records warning/status and should not block review when OCR text/manual text is available.
- If storage is disabled, session should continue with `imageStorageStatus=NOT_CONFIGURED`.
- Do not log image bytes, signed URLs, storage IDs, OCR text, provider secrets, or full provider URLs.

## Confirm Behavior

- Only confirm endpoints create transactions.
- Confirm requires workspace membership, positive amount, wallet when expense rules require it, and category when expense rules require it.
- Confirm creates transaction through existing transaction service paths.
- Confirm records source/evidence metadata.
- Reconfirming an already confirmed draft returns the stored transaction link and does not create a duplicate.
- Unsupported/debt/savings/fund intents are not silently converted into normal expense.

## Transaction Evidence Link

Preferred future shape:

- Add `TransactionSourceType.RECEIPT`.
- Store `sourceReference=receipt-session:{sessionId}:draft:{draftId}`.
- Add nullable receipt session/draft link columns only after migration review.
- Preserve image evidence URL/public ID on receipt session, not directly on transaction unless product requires it.

## Security

- Every endpoint is workspace scoped.
- Backend verifies active workspace membership before reading or writing receipt state.
- Session/draft IDs must be constrained by workspace.
- OCR provider calls must be backend-only.
- File validation happens before storage/OCR.
- No fake OCR output in runtime provider modes.

## P11B Known Limitations

- No receipt draft entity exists yet.
- No confirm executor exists yet.
- HEIC/HEIF upload support is deferred.
- Existing stateless receipt review endpoints remain separate from receipt sessions.

## P11C Known Limitations

- Mock OCR is deterministic and not real OCR.
- Azure Document Intelligence is not implemented until P11D.
- Receipt drafts are not implemented until P11E.
- Confirm executor is not implemented until P11F.
- UI is not implemented in this phase.
