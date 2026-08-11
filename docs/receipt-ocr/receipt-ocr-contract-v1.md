# Receipt OCR Contract V1

Status: backend contract for 2.1.4. Backend locked; Azure local setup documented.

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

## P11D Implemented Azure OCR

Provider mode:

- `azure_document_intelligence`

Chosen REST API:

- Azure AI Document Intelligence REST `2024-11-30`.
- Analyze endpoint shape: `/documentintelligence/documentModels/{modelId}:analyze?api-version=2024-11-30`.
- Default model: `prebuilt-receipt`.
- Microsoft reference: `https://learn.microsoft.com/azure/ai-services/document-intelligence/`.

Configuration placeholders:

- `MONEYFLOW_RECEIPT_OCR_PROVIDER=azure_document_intelligence`
- `AZURE_DOCUMENT_INTELLIGENCE_ENDPOINT=<endpoint>`
- `AZURE_DOCUMENT_INTELLIGENCE_KEY` from local `.env`
- `AZURE_DOCUMENT_INTELLIGENCE_MODEL_ID=prebuilt-receipt`
- `AZURE_DOCUMENT_INTELLIGENCE_API_VERSION=2024-11-30`
- `MONEYFLOW_RECEIPT_OCR_TIMEOUT_SECONDS=45`
- `MONEYFLOW_RECEIPT_OCR_POLL_INTERVAL_MS=1500`
- `MONEYFLOW_RECEIPT_OCR_MAX_POLL_ATTEMPTS=20`

Request strategy:

- Session OCR uses existing stored receipt image URL because P11B stores image metadata and URL, not image bytes.
- Stateless image OCR can still pass bytes through `ReceiptImageInput`.
- Provider sends image bytes when available; otherwise it sends Azure JSON `urlSource`.
- The Azure key is sent only in `Ocp-Apim-Subscription-Key`, never in a query string.

Polling:

- POST analyze request.
- Read `Operation-Location`.
- Poll with the same key until `succeeded`, `failed`, timeout, or max attempts.

Mapped fields:

- `rawOcrText` from `analyzeResult.content`.
- `merchantName` from `MerchantName`.
- `receiptDate` from `TransactionDate`.
- `totalAmount` and `currency` from `Total.valueCurrency`.
- Normalized text remains NFC/LF at session service level.

Status and warning mapping:

- Missing config: `OCR_NOT_CONFIGURED`.
- Missing image: `RECEIPT_IMAGE_REQUIRED`.
- Missing stored URL/bytes: `OCR_IMAGE_NOT_ACCESSIBLE`.
- Unsupported content type: `OCR_UNSUPPORTED_IMAGE_FORMAT`.
- Azure 401/403: `OCR_PROVIDER_AUTH_FAILED`.
- Azure 408 or poll timeout: `OCR_PROVIDER_TIMEOUT`.
- Azure 429: `OCR_PROVIDER_RATE_LIMITED`.
- Azure 400/415: `OCR_PROVIDER_BAD_REQUEST`.
- Missing `Operation-Location` or Azure failed result: `OCR_PROVIDER_FAILED`.
- Succeeded with empty content: `OCR_EMPTY_TEXT`.
- Succeeded with text but missing total/date: `OCR_TOTAL_NOT_FOUND`, `OCR_DATE_NOT_FOUND`.

Security and privacy:

- No OCR text, image bytes, image URL, or Azure key is logged by the provider.
- Workspace membership remains enforced before OCR.
- OCR still does not build drafts and does not create transactions.

## P11E Implemented Draft Builder

`POST /api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts`

- Requires auth and active workspace membership.
- Requires `ocrStatus=SUCCEEDED` and non-empty normalized OCR text.
- Builds one primary `EXPENSE` review draft.
- Persists draft rows in `receipt_session_drafts`.
- Returns drafts in `ReceiptSessionDetailResponse`.
- Rebuild behavior: deletes and replaces current receipt session drafts, so repeated `POST /drafts` does not duplicate unconfirmed drafts.
- Does not create transactions.

Draft model fields:

- `draftId`
- `draftIndex`
- `type`
- `status`
- `amount`
- `currency`
- `transactionDate`
- `walletId`
- `categoryId`
- `categoryHint`
- `merchantName`
- `note`
- `sourceText`
- `confidence`
- `warnings`
- `createdAt`
- `updatedAt`

Builder rules:

- Type defaults to `EXPENSE`.
- Amount uses the Java receipt amount candidate ranker.
- Azure structured `Total` with good confidence is preferred.
- Vietnamese high-priority total labels beat larger numeric tokens.
- Rounded cash/payment labels are secondary candidates.
- Customer tendered, change returned, loyalty points, receipt codes, phones/hotlines, dates/times, quantities, VAT, and percent values are excluded from primary selection.
- Largest-number selection is forbidden.
- If no safe amount exists, amount remains missing and review warnings explain the reason.
- Date prefers structured `receiptDate`, then parses simple OCR dates.
- Merchant prefers structured `merchantName`, then the first safe OCR merchant line.
- Note is `Hóa đơn: <merchant>` or `Hóa đơn OCR`.
- Source text stores a concise OCR excerpt, not the full raw OCR payload.
- Wallet is never guessed and remains `null`.
- Category ID is never guessed; category hint is derived from keywords only.

Category hints:

- Fuel/petrol/xang: `Xăng xe`.
- Coffee/cafe/tra sua: `Cà phê`.
- Restaurant/food/quan: `Ăn uống`.
- Grocery/mart/sieu thi: `Mua sắm`.
- Pharmacy/thuoc: `Y tế`.
- Utilities/internet/dien/nuoc: `Tiện ích`.

Draft warnings:

- `RECEIPT_DRAFT_MISSING_AMOUNT`
- `RECEIPT_DRAFT_MISSING_WALLET`
- `RECEIPT_DRAFT_MISSING_CATEGORY`
- `RECEIPT_DATE_NOT_FOUND`
- `RECEIPT_TOTAL_INFERRED`
- `RECEIPT_TOTAL_NOT_FOUND`
- `RECEIPT_TOTAL_LOW_CONFIDENCE`
- `RECEIPT_TOTAL_AMBIGUOUS`
- `RECEIPT_AMOUNT_FROM_ROUNDED_CASH`
- `RECEIPT_AMOUNT_CANDIDATES_AVAILABLE`
- `RECEIPT_EXCLUDED_CUSTOMER_TENDERED`
- `RECEIPT_EXCLUDED_CHANGE_RETURNED`
- `RECEIPT_EXCLUDED_LOYALTY_POINTS`
- `RECEIPT_EXCLUDED_RECEIPT_CODE`
- `RECEIPT_EXCLUDED_PHONE_OR_HOTLINE`
- `RECEIPT_CATEGORY_HINT_ONLY`
- `RECEIPT_OCR_REQUIRED`
- `RECEIPT_MERCHANT_NOT_FOUND`

## R-OCR-2 Amount Candidate Ranker

Receipt amount extraction is label-aware and review-first.

High-priority total labels:

- `Phải thanh toán`
- `Tổng thanh toán`
- `Tổng cộng`
- `Cần thanh toán`
- `Thành tiền`
- `Tổng tiền`
- `Total`
- `Grand total`
- `Amount due`

Secondary payment labels:

- `Tiền mặt`
- `Đã làm tròn`
- `Thanh toán`
- `Khách thanh toán`

Excluded labels/contexts:

- `Tiền khách đưa`, `Khách đưa`
- `Tiền thối lại`, `Tiền trả lại`, `Trả lại`
- `Điểm sử dụng`, `Điểm tích lũy`
- `VAT`, `Thuế`, `%`
- `Số CT`, `Số chứng từ`
- `Mã tra cứu`, `Mã đơn hàng`, `Mã hóa đơn`, `mã`, `code`, `order`
- `SĐT`, `Điện thoại`, `Hotline`, `Góp ý`, `phone`, `tel`
- `QR`
- date/time text
- quantity/weight text such as `kg`, `g`, `SL`

Bách Hóa Xanh regression target:

- `Phải thanh toán: 67.463` selects primary `67463`.
- `Tiền mặt (Đã làm tròn): 65.000` remains a secondary candidate.
- `Tiền khách đưa: 200.000` is excluded as `CUSTOMER_TENDERED`.
- `Tiền thối lại: 135.000` is excluded as `CHANGE_RETURNED`.
- `Điểm sử dụng: 2.463` is excluded as `LOYALTY_POINTS`.
- `Mã tra cứu: 6359148EDC` is excluded as `RECEIPT_CODE`.
- `Góp ý: 18001067` is excluded as `PHONE_OR_HOTLINE`.

## R-OCR-3 Merchant, Date, Category Confidence

Receipt review now returns confidence and evidence for merchant, date, and category suggestions.

Merchant rules:

- High-confidence Azure structured `MerchantName` wins.
- Low-confidence structured merchant falls back to text candidates.
- Header and known merchant lines can be selected.
- Time, date, amount, receipt code, phone, hotline, VAT, staff, QR, and footer contexts are rejected.
- Bach Hoa Xanh header text normalizes to `Bách Hóa Xanh`; garbage such as `16:29 G` must not be selected.

Date rules:

- High-confidence Azure structured `TransactionDate` wins.
- Low-confidence structured date falls back to text patterns.
- Supported text formats include `dd/MM/yyyy`, `dd-MM-yyyy`, `yyyy-MM-dd`, and `dd.MM.yyyy`.
- Phone, hotline, receipt code, amount, and impossible dates are rejected.
- Far-future dates emit `RECEIPT_DATE_FUTURE_SUSPICIOUS` evidence but remain reviewable.

Category suggestion rules:

- Merchant history is checked first against posted, non-deleted expense transactions in the same workspace.
- If history has exactly one category for the merchant, that category is suggested.
- If no history applies, strong grocery merchant or item evidence can suggest a category only when the workspace has one unique active matching category.
- Multiple active matches leave `categoryId=null` with `CATEGORY_AMBIGUOUS_MATCH`.
- Archived categories and other workspace categories/history are ignored.
- No category is guessed from random OCR text.
- Shipper/delivery categories require explicit delivery evidence such as `shipper`, `giao hang`, `phi ship`, `van chuyen`, or `delivery`.

Bach Hoa Xanh expected behavior:

- Amount remains `67463`.
- Merchant is `Bách Hóa Xanh`.
- Date is `2026-07-30` when present in OCR text.
- Category remains null unless a unique active grocery/food category or merchant history exists.
- Shipper category is never selected without delivery evidence.
- `needsReview=true` when wallet/category is missing or confidence is low.

Known limitations:

- Frontend candidate picker remains R-OCR-4.
- More real receipt fixtures remain R-OCR-5.

## P11F Implemented Confirm Executor

`POST /api/workspaces/{workspaceId}/receipt-sessions/{sessionId}/drafts/{draftId}/confirm`

Request:

```json
{
  "amount": 35000,
  "currency": "VND",
  "transactionDate": "2026-08-11",
  "walletId": "00000000-0000-0000-0000-000000000000",
  "categoryId": "00000000-0000-0000-0000-000000000000",
  "note": "Hóa đơn: Quán Cà Phê Demo",
  "merchantName": "Quán Cà Phê Demo"
}
```

Response:

- `receiptSessionId`
- `confirmedDraftId`
- `draftStatus`
- `confirmedEntityType`
- `confirmedEntityId`
- `idempotentReplay`
- `warnings`
- `session.status`
- `transaction`

Validation:

- Session must belong to the workspace and authenticated member.
- Draft must belong to the session.
- Receipt confirm supports `EXPENSE` drafts only.
- Amount must be positive.
- Transaction date, wallet, and category are required.
- Wallet and category must belong to the same workspace.
- User-correctable validation returns warning responses and does not create a transaction.

Warnings:

- `RECEIPT_DRAFT_MISSING_AMOUNT`
- `RECEIPT_DRAFT_MISSING_WALLET`
- `RECEIPT_DRAFT_MISSING_CATEGORY`
- `RECEIPT_DRAFT_MISSING_DATE`
- `RECEIPT_DRAFT_ALREADY_CONFIRMED`
- `RECEIPT_DRAFT_INVALID_STATE`

Transaction creation:

- Uses `TransactionService.createWithReceiptSource(...)`.
- Creates a normal posted `EXPENSE` transaction.
- Preserves wallet/category/amount/date validation and wallet balance behavior from the transaction domain.
- Sets `sourceType=RECEIPT`.
- Stores `sourceReference=receipt-session:{sessionId}:draft:{draftId}`.
- Stores receipt evidence links on the transaction: `receiptSessionId`, `receiptSessionDraftId`.

Idempotency:

- A confirmed draft stores `confirmedEntityType=TRANSACTION`, `confirmedEntityId`, and `confirmedAt`.
- Re-confirm returns the existing transaction with `idempotentReplay=true`.
- A unique receipt draft transaction index prevents duplicate receipt confirm rows.

Status transitions:

- Successful draft confirm sets draft status to `CONFIRMED`.
- One confirmed draft among multiple drafts sets session status to `PARTIALLY_CONFIRMED`.
- All drafts confirmed sets session status to `CONFIRMED`.
- Validation failure keeps the draft in `NEEDS_REVIEW`.

No-auto-save invariant:

- Create session, upload image, OCR, and draft build still create no transaction.
- Only confirm mutates the ledger.

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

`ReceiptDraft` fields:

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

Implemented shape:

- Add `TransactionSourceType.RECEIPT`.
- Store `sourceReference=receipt-session:{sessionId}:draft:{draftId}`.
- Add nullable receipt session/draft link columns.
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

## P11D Known Limitations

- Live Azure UAT requires a real configured Azure Document Intelligence resource and was not part of automated tests.
- Session OCR depends on the stored receipt URL when image bytes are unavailable.
- Local Azure setup guide: `docs/receipt-ocr/azure-document-intelligence-setup.md`.

## P11E/P11F Known Limitations

- UI is not implemented in this phase.
- Category is hint-only; no category ID is guessed or created.
- Wallet is not guessed.
- Line-item split into multiple transactions is deferred.
- OCR drafts still require user review before ledger mutation.
- Storage disabled means transaction can save without image playback.
