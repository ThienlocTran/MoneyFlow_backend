# Receipt OCR Provider Plan

## Current Status

MoneyFlow supports receipt review as a draft-first backend flow. The existing JSON endpoint still accepts manual `rawText`. The multipart endpoint accepts one or more receipt images plus optional text, but receipt image storage is not configured yet.

OCR is disabled by default. When images are sent without text and OCR is disabled, the API returns `OCR_NOT_CONFIGURED` instead of pretending extraction worked.

## Multi-Image Endpoint

`POST /api/workspaces/{workspaceId}/receipt-review/parse-with-images`

Multipart parts:

- `images`: optional repeated image files, upload order preserved.
- `rawText`: optional pasted receipt text.
- `source`: optional `MANUAL_TEXT`, `OCR_TEXT`, `PHOTO_PENDING_OCR`, or `PHOTO_OCR`.
- `occurredAtHint`: optional datetime.
- `walletId`: optional wallet ID.
- `timezone`: optional, reserved for future parsing behavior.

Either `rawText` or at least one image is required.

## Provider Modes

- `NONE`: default. OCR is disabled and returns `RECEIPT_OCR_NOT_CONFIGURED`.
- `MOCK`: deterministic test provider only.
- `EXTERNAL_HTTP`: placeholder for a future external OCR service. No outbound OCR call is made in this module.

Environment variables:

- `MONEYFLOW_RECEIPT_OCR_PROVIDER=none`
- `MONEYFLOW_RECEIPT_OCR_MAX_IMAGES=5`
- `MONEYFLOW_RECEIPT_OCR_MAX_IMAGE_BYTES=5242880`
- `MONEYFLOW_RECEIPT_OCR_TIMEOUT_SECONDS=30`
- `MONEYFLOW_RECEIPT_OCR_SERVICE_URL=`

## OCR Disabled Behavior

Images without text return `OCR_NOT_CONFIGURED` / `PHOTO_PENDING_OCR`. The response includes accepted attachment metadata and OCR warnings. The user can paste text to create a draft.

## Future OCR Options

- Self-host OCR service for receipt images.
- Cloud OCR provider behind backend-only credentials.
- Vision model provider behind backend-only credentials.

All future options need request size limits, timeout handling, privacy review, and no secret exposure.

## Privacy Notes

Receipt images and text can contain financial data. Do not log image bytes, raw OCR text at error level, provider secrets, signed URLs, or storage IDs. The parse flow remains draft-first; only explicit confirm endpoints should affect ledger balances.
