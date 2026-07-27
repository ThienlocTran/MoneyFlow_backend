# Voice Batch Review Backend Contract

Voice batch review keeps parse and commit separated.

## Parse

Endpoint:

`POST /api/workspaces/{workspaceId}/quick-entry/parse`

Response wrapper:

`{ "success": true, "message": "Quick entry parsed", "data": QuickEntryPreviewResponse }`

`data` keeps the legacy single-preview fields:

- `rawInput`
- `normalizedInput`
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
- `note`
- `spendingScope`
- `confidence`
- `readyToConfirm`
- `missingFields`
- `warnings`
- `matchedKeyword`
- `matchedCategoryId`
- `matchedWalletText`

`data.candidates[]` is additive for batch UI:

- `candidateId`: stable server-generated ID for this parse result
- `originalText`: text segment/evidence for this candidate
- `type`: `INCOME`, `EXPENSE`, or `TRANSFER`
- `status`: `POSTED` or `PLANNED`
- `amount`
- `walletId`, `walletName`: income/expense wallet
- `categoryId`, `categoryName`: income/expense category
- `sourceWalletId`, `sourceWalletName`: transfer source wallet
- `destinationWalletId`, `destinationWalletName`: transfer destination wallet
- `transactionDate`
- `transactionTime`
- `description`
- `spendingScope`
- `confidence`
- `readyToConfirm`
- `validationStatus`: `READY` or `NEEDS_REVIEW`
- `missingFields`
- `warnings`

Parse does not create transactions, voice records, drafts, or audio objects.

## Confirm Batch

Endpoint:

`POST /api/workspaces/{workspaceId}/quick-entry/confirm-voice-batch`

Request:

```json
{
  "idempotencyKey": "voice-session-uuid-or-client-key",
  "rawInput": "an sang 35k cafe 20k",
  "durationSeconds": 8,
  "audioMimeType": "audio/webm",
  "candidates": [
    {
      "candidateId": "cand_...",
      "clientCandidateId": null,
      "selected": true,
      "type": "EXPENSE",
      "status": "POSTED",
      "amount": 35000,
      "walletId": "uuid",
      "categoryId": "uuid",
      "sourceWalletId": null,
      "destinationWalletId": null,
      "transactionDate": "2026-07-23",
      "transactionTime": null,
      "description": "An sang",
      "note": null,
      "spendingScope": null,
      "attributedPersonId": null
    }
  ]
}
```

Rules:

- `idempotencyKey` is required.
- At least one candidate must be selected.
- `selected: false` candidates are ignored.
- `candidateId` or `clientCandidateId` is required for every selected candidate.
- Frontend edits are accepted through candidate fields.
- Unsupported transaction types are rejected with `INVALID_TRANSACTION_TYPE`.
- All selected candidates are validated by the same transaction service path used by existing quick entry confirm.
- Commit is all-or-nothing inside one transaction.
- A repeated `idempotencyKey` returns existing committed transactions and does not create duplicates.

Response:

```json
{
  "success": true,
  "message": "Voice transactions created",
  "data": {
    "idempotencyKey": "voice-session-uuid-or-client-key",
    "voiceRecordId": "uuid",
    "committedCount": 2,
    "idempotentReplay": false,
    "items": [
      {
        "candidateId": "cand_...",
        "transaction": {
          "id": "uuid",
          "type": "EXPENSE",
          "status": "POSTED",
          "amount": 35000,
          "transactionDate": "2026-07-23",
          "sourceType": "VOICE",
          "voiceRecordId": "uuid"
        }
      }
    ]
  }
}
```

## Compatibility

Existing endpoints remain unchanged:

- `POST /api/workspaces/{workspaceId}/quick-entry/confirm`
- `POST /api/workspaces/{workspaceId}/quick-entry/confirm-voice`

Existing single-preview clients can continue using legacy parse fields and single confirm.

## Atomicity

Batch confirm uses all-or-nothing atomic commit because current transaction creation, wallet validation, voice record creation, and audit logging are service-side transactional flows. There is no existing partial-success quick-entry response style.

Current idempotency is stored on committed transactions through `sourceReference` with prefix:

`voice-batch:{idempotencyKey}:{candidateId}`

This avoids duplicate records for normal retry calls. A future DB-backed idempotency mechanism can replace this prefix lookup without changing the external request/response shape.
