# MoneyFlow Voice Spec

Last consolidated: 2026-07-23.

## Voice Product Goal

Voice input should let a busy user speak Vietnamese money events, review parsed candidates, edit uncertain fields, and commit only after confirmation. Voice is a draft-and-confirm interface, not an autonomous bookkeeper.

## Audio Lifecycle

- Parse/confirm can save transcript and transaction data without storing audio.
- Audio upload is mediated by backend endpoints under `/api/voice-records/{voiceRecordId}`.
- Storage is disabled by default.
- If storage is disabled, upload/playback return `STORAGE_NOT_CONFIGURED`; the transaction and transcript remain saved.
- If provider upload fails, voice record is marked storage failed; linked transaction remains saved.
- Playback requires active workspace membership and returns a short-lived provider URL when storage exists.
- Upload/delete require writable membership.
- Retention/delete removes stored audio object and audio metadata only; transcript and linked transactions remain for traceability.

Voice audio endpoints:

- `POST /api/voice-records/{voiceRecordId}/audio`
- `GET /api/voice-records/{voiceRecordId}/playback`
- `GET /api/voice-records/{voiceRecordId}/playback-url`
- `DELETE /api/voice-records/{voiceRecordId}/audio`

## Supported Committable Intents

Currently committable through quick-entry transaction service:

- `TRANSACTION_EXPENSE`
- `TRANSACTION_INCOME`
- `TRANSACTION_TRANSFER`

These map to transaction types:

- `EXPENSE`
- `INCOME`
- `TRANSFER`

Commit endpoints:

- Single voice confirm: `POST /api/workspaces/{workspaceId}/quick-entry/confirm-voice`
- Batch voice confirm: `POST /api/workspaces/{workspaceId}/quick-entry/confirm-voice-batch`

## Draft-Only Intents

The parser can identify or classify these, but they are not confirmed as their target module action through the voice batch transaction commit path unless code later adds explicit support:

- `WALLET_BALANCE_SNAPSHOT`
- `DEBT_PAYMENT`
- `DEBT_CREATE`
- `SAVINGS_GOAL_CONTRIBUTION`
- `SINKING_FUND_CONTRIBUTION`
- `EMERGENCY_FUND_CONTRIBUTION`
- `RECURRING_OBLIGATION_PAYMENT`

Future support must define module-specific confirmation contracts before committing these actions.

## Unsupported Behavior

- `UNKNOWN_UNSUPPORTED` means the parser could not safely map the command.
- Unsupported candidates must be visible as unsupported or needs-review, not silently committed.
- Unsupported transaction types are rejected on confirm with `INVALID_TRANSACTION_TYPE`.
- Missing amount, wallet, category, transfer source, or transfer destination should block ready-to-confirm when required by the transaction type.

## Vietnamese Command Examples

Expense:

- `ăn sáng 35k`
- `cà phê 20 nghìn`
- `đổ xăng 70k ví tiền mặt`
- `mua đồ ăn 120k hôm qua`

Income:

- `nhận lương 8 triệu vào ngân hàng`
- `mẹ cho 500k`
- `thu freelance 1.5tr`
- `hoàn tiền 100k`

Transfer:

- `chuyển 1 triệu từ Cake sang MoMo`
- `rút 500k từ ngân hàng sang tiền mặt`
- `MB sang MoMo 200k`

Needs review or draft-only:

- `Bảo trả nợ 750k`
- `cho Chị Nga mượn 3 triệu`
- `bỏ 500k vào quỹ khẩn cấp`
- `đóng tiền wifi 200k`

## Candidate Response Contract

Parse endpoint:

`POST /api/workspaces/{workspaceId}/quick-entry/parse`

Response wrapper:

```json
{
  "success": true,
  "message": "Quick entry parsed",
  "data": {
    "rawInput": "...",
    "normalizedInput": "...",
    "type": "EXPENSE",
    "status": "POSTED",
    "amount": 35000,
    "walletId": "uuid",
    "walletName": "Tiền mặt",
    "categoryId": "uuid",
    "categoryName": "Ăn uống",
    "transactionDate": "2026-07-23",
    "confidence": "HIGH",
    "readyToConfirm": true,
    "missingFields": [],
    "warnings": [],
    "candidates": []
  }
}
```

Batch candidates include:

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

Parse must not create transactions, voice records, drafts, or audio objects.

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
- Batch source references use the prefix `voice-batch:{idempotencyKey}:{candidateId}`.

## Storage And Privacy Rules

- Provider secrets stay backend-only.
- Do not expose Cloudinary/S3 keys or signed playback URLs in logs.
- Log redaction must hide secrets, auth tokens, storage public IDs, and raw provider URLs where applicable.
- Audio storage can be disabled without breaking transaction confirmation.
- Deleting audio must not delete transactions or transcripts.
- Retention cleanup must delete only eligible stored audio objects and audio metadata.
