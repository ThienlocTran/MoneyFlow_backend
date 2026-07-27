# MoneyFlow Voice Spec

Last synced: 2026-07-23.

## Product Goal

Voice lets a busy user speak Vietnamese money events, review parsed candidates, edit uncertain fields, then commit only after confirmation. Voice is a draft-and-confirm interface, not an autonomous bookkeeper.

Recognized does not mean committable.

## Required Flow

```text
Quick/Voice parse -> draft -> user confirm -> save
```

Parse must not create transactions, voice records, drafts, debts, payments, funds, obligations, wallet snapshots, or audio objects.

## Audio Lifecycle

- Parse/confirm can save transcript and transaction data without storing audio.
- Audio upload happens only after confirm.
- Audio upload is mediated by backend endpoints under `/api/voice-records/{voiceRecordId}`.
- Storage is disabled by default.
- If storage is disabled, upload/playback return `STORAGE_NOT_CONFIGURED`; the transaction and transcript remain saved.
- If provider upload fails, voice record is marked storage failed; linked transaction and transcript remain saved.
- Playback requires active workspace membership and returns a short-lived provider URL when storage exists.
- Upload/delete require authorized writable membership.
- Retention/delete removes stored audio object and audio metadata only; transcript and linked transactions remain for traceability.

## Audio Endpoints

- `POST /api/voice-records/{voiceRecordId}/audio`
- `GET /api/voice-records/{voiceRecordId}/playback`
- `GET /api/voice-records/{voiceRecordId}/playback-url`
- `DELETE /api/voice-records/{voiceRecordId}/audio`

## Provider And Privacy Rules

- Provider secrets stay backend-only.
- Do not expose Cloudinary/S3 keys, storage public IDs, signed playback URLs, auth tokens, or DB URLs in logs.
- Audio storage can be disabled without breaking transaction confirmation.
- Deleting audio must not delete transactions or transcripts.
- Voice audio may contain private financial information; keep retention bounded.

## Current Committable Intents

Current batch contract safely supports only:

- `INCOME`
- `EXPENSE`
- `TRANSFER`

Commit endpoints:

- `POST /api/workspaces/{workspaceId}/quick-entry/confirm-voice`
- `POST /api/workspaces/{workspaceId}/quick-entry/confirm-voice-batch`

## Draft-Only Intents For Phase 2

These may be recognized or shown as candidates, but must remain draft-only until safe backend commit is implemented and tested:

- debt create;
- debt payment;
- savings goal contribution;
- sinking fund allocation/release;
- emergency fund allocation/release;
- wallet snapshot;
- recurring obligation create;
- recurring obligation payment/occurrence confirmation.

Future support must define module-specific confirmation contracts before committing these actions.

## Unsupported Behavior

- Unsupported candidates must be visible as unsupported or needs-review, not silently committed.
- Unsupported transaction types are rejected on confirm with `INVALID_TRANSACTION_TYPE`.
- Missing amount, wallet, category, transfer source, or transfer destination must block ready-to-confirm when required.
- No bank/e-wallet action.
- No guessed wallet/source/person/category for financial impact.
- No fake fallback result when parser or backend fails.

## Candidate Response Contract

Parse endpoint:

`POST /api/workspaces/{workspaceId}/quick-entry/parse`

Candidate fields:

- `candidateId`
- `originalText`
- `type`
- `status`
- `amount`
- `walletId`, `walletName`
- `categoryId`, `categoryName`
- `sourceWalletId`, `sourceWalletName`
- `destinationWalletId`, `destinationWalletName`
- `transactionDate`
- `transactionTime`
- `description`
- `spendingScope`
- `confidence`
- `readyToConfirm`
- `validationStatus`: `READY`, `NEEDS_REVIEW`, or `UNSUPPORTED`
- `missingFields`
- `warnings`

## Confirm And Idempotency Rules

Single voice confirm:

- `idempotencyKey` is required.
- Repeating the same key for the same workspace/user returns the prior committed result instead of creating a duplicate.

Batch voice confirm:

- `idempotencyKey` is required.
- At least one candidate must be selected.
- `selected=false` candidates are ignored.
- Each selected candidate must have `candidateId` or `clientCandidateId`.
- Frontend edits are accepted through candidate fields.
- All selected candidates validate through transaction service rules.
- Commit is all-or-nothing.
- Repeated `idempotencyKey` returns existing committed transactions and marks replay behavior.

Current persistence:

- Voice idempotency is stored on `voice_records.idempotency_key`.
- Batch source references use `voice-batch:{idempotencyKey}:{candidateId}`.

## Vietnamese Examples

Committable transaction examples:

- `ăn sáng 35k`
- `cà phê 20 nghìn`
- `đổ xăng 70k ví tiền mặt`
- `nhận lương 8 triệu vào ngân hàng`
- `mẹ cho 500k`
- `chuyển 1 triệu từ Cake sang MoMo`

Draft-only examples:

- `Bảo trả nợ 750k`
- `cho Chị Nga mượn 3 triệu`
- `bỏ 500k vào quỹ khẩn cấp`
- `đóng tiền wifi 200k`

## Phase 2 Implementation Plan

1. Keep current commit path limited to `INCOME`, `EXPENSE`, `TRANSFER`.
2. Add draft candidate types for debt, savings, funds, wallet snapshot, and recurring obligations.
3. Add UI review states for draft-only candidates.
4. Add module-specific backend confirm contracts one module at a time.
5. Add tests for idempotency, authorization, storage-disabled behavior, and upload-failure transaction preservation before enabling commits.
