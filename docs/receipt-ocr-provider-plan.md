# Receipt OCR Provider Plan

## Status

MoneyFlow receipt review is draft-first. `parse` and `parse-with-images` never post a transaction or mutate wallet balance.

The backend supports configurable OCR provider modes. It does not include a built-in OCR engine. Real OCR in this MVP means MoneyFlow can call a configured external HTTP OCR service and parse the returned text into a receipt expense draft.

## Endpoint

`POST /api/workspaces/{workspaceId}/receipt-review/parse-with-images`

Multipart parts:

- `images`: repeated image files, order preserved
- `rawText`: optional pasted receipt text
- `source`: optional `MANUAL_TEXT`, `OCR_TEXT`, `PHOTO_PENDING_OCR`, or `PHOTO_OCR`
- `occurredAtHint`: optional datetime
- `walletId`: optional wallet ID
- `timezone`: optional, reserved

Either `rawText` or at least one image is required.

## Provider modes

- `none`: default; no OCR call
- `mock`: deterministic test provider
- `external_http`: calls a configured OCR service

Environment variables:

- `MONEYFLOW_RECEIPT_OCR_PROVIDER=none`
- `MONEYFLOW_RECEIPT_OCR_SERVICE_URL=`
- `MONEYFLOW_RECEIPT_OCR_TIMEOUT_SECONDS=30`
- `MONEYFLOW_RECEIPT_OCR_MAX_IMAGES=5`
- `MONEYFLOW_RECEIPT_OCR_MAX_IMAGE_BYTES=5242880`
- `MONEYFLOW_RECEIPT_OCR_LANGUAGE=vi`

Production default remains `none` so MoneyFlow never calls external OCR accidentally.

## Behavior

Raw text present:

- OCR is skipped.
- Raw text is parsed into a receipt review draft.
- Response source follows request source or defaults to `MANUAL_TEXT`.

Images only with `none`:

- response status `OCR_NOT_CONFIGURED`
- source `PHOTO_PENDING_OCR`
- candidate `null`
- warnings include `RECEIPT_OCR_NOT_CONFIGURED` and `RECEIPT_TEXT_REQUIRED_WHEN_OCR_DISABLED`

Images only with `external_http` and configured URL:

- images are sent to the OCR service in upload order
- language defaults to `vi`
- successful OCR text is parsed as `PHOTO_OCR`
- candidate remains an `EXPENSE` draft
- wallet/category may still be missing

OCR empty/failure:

- empty text returns `OCR_TEXT_EMPTY` and no candidate
- timeout or provider failure returns `OCR_FAILED` and no candidate
- provider exceptions are mapped to stable MoneyFlow warnings; raw exceptions are not exposed

## Privacy

Do not log receipt image bytes, raw OCR text in errors, provider secrets, signed URLs, or storage IDs. Diagnostics expose only safe booleans and limits, not the full OCR URL.

## Frontend interpretation

- `OCR_NOT_CONFIGURED`: show paste-text fallback.
- `OCR_FAILED`: ask user to retry clearer image or paste text.
- `OCR_TEXT_EMPTY`: ask for clearer image or text.
- `NEEDS_REVIEW` with `PHOTO_OCR`: render candidate draft for user review/confirm.
