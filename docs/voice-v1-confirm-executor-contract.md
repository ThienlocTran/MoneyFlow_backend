# Voice V1 Confirm Executor Contract

VoiceSession interpretation is preview-only. Ledger state changes only happen when a draft is confirmed.

## Endpoints

Base path:

`/api/workspaces/{workspaceId}/voice-sessions/{sessionId}`

### Confirm One Draft

`POST /drafts/{draftId}/confirm`

Request fields are optional overrides over the persisted draft:

```json
{
  "amount": 50000,
  "walletId": "uuid",
  "categoryId": "uuid",
  "occurredAt": "2026-08-04T09:58:00+07:00",
  "note": "Ăn sáng",
  "confirmClientRequestId": "optional-idempotency-key"
}
```

Behavior:

- Loads the session by workspace and the draft by session.
- Replays an already confirmed draft without creating another entity.
- Confirms only supported transaction drafts with required fields.
- Marks unsupported drafts as `UNSUPPORTED`.
- Marks drafts missing required fields as `NEEDS_REVIEW`.
- Creates ledger entries through `TransactionService.createWithSource(...)`.
- Stores `confirmedEntityType=TRANSACTION`, `confirmedEntityId`, and `confirmedAt` on the draft.

Success response shape:

```json
{
  "sessionId": "uuid",
  "draftId": "uuid",
  "draftStatus": "CONFIRMED",
  "confirmedEntityType": "TRANSACTION",
  "confirmedEntityId": "uuid",
  "idempotentReplay": false,
  "warnings": [],
  "confirmStatus": "PARTIALLY_CONFIRMED"
}
```

Validation failure response shape:

```json
{
  "sessionId": "uuid",
  "draftId": "uuid",
  "draftStatus": "NEEDS_REVIEW",
  "confirmedEntityType": null,
  "confirmedEntityId": null,
  "warnings": [
    {
      "code": "VOICE_DRAFT_WALLET_REQUIRED",
      "message": "Cần chọn ví trước khi lưu khoản này."
    }
  ],
  "confirmStatus": "NOT_CONFIRMED"
}
```

Unsupported response shape:

```json
{
  "sessionId": "uuid",
  "draftId": "uuid",
  "draftStatus": "UNSUPPORTED",
  "confirmedEntityType": null,
  "confirmedEntityId": null,
  "warnings": [
    {
      "code": "VOICE_DRAFT_CONFIRM_UNSUPPORTED",
      "message": "Loại lệnh này chưa hỗ trợ lưu tự động. Hãy xử lý thủ công."
    }
  ],
  "confirmStatus": "NOT_CONFIRMED"
}
```

### Confirm Eligible Drafts

`POST /confirm-eligible`

```json
{
  "confirmClientRequestId": "optional-idempotency-key"
}
```

Behavior:

- Attempts every draft in the session.
- Confirms supported drafts that have required fields.
- Continues after unsupported or incomplete drafts.
- Returns per-draft results.
- Counts new confirmations only. Already confirmed drafts return `idempotentReplay=true`.

```json
{
  "sessionId": "uuid",
  "confirmedCount": 2,
  "skippedCount": 2,
  "confirmStatus": "PARTIALLY_CONFIRMED",
  "results": []
}
```

### Skip Draft

`POST /drafts/{draftId}/skip`

Behavior:

- Marks a non-confirmed draft as `SKIPPED`.
- Does not create a transaction.
- Updates the session confirm status.
- Already confirmed drafts replay as confirmed and are not skipped.

## Support Matrix

| Draft type | Confirm support | Output entity | Affects wallet | Required fields |
| --- | --- | --- | --- | --- |
| `EXPENSE` | Yes | `TRANSACTION` | Yes | `amount`, `walletId`, `categoryId` |
| `INCOME` | Yes | `TRANSACTION` | Yes | `amount`, `walletId` |
| `INCOME_FACT` | No in P5 | None | No | Manual handling |
| `SAVINGS_ALLOCATION` | No in P5 | None | Depends on future domain executor | Manual handling |
| `SINKING_FUND_CONTRIBUTION` | No in P5 | None | Depends on future domain executor | Manual handling |
| `EMERGENCY_FUND_CONTRIBUTION` | No in P5 | None | Depends on future domain executor | Manual handling |
| `WALLET_SNAPSHOT` | No in P5 | None | No transaction conversion | Manual handling |
| debt movement types | No in P5 | None | No transaction conversion | Manual handling |
| `READ_ONLY_QUERY` | No | None | No | Never saved as transaction |

Unsupported draft types are not silently converted to expense or income.

## Idempotency

Confirmed drafts store their confirmed entity link. Reconfirming the same draft returns that stored entity and `idempotentReplay=true`; it does not call transaction creation again.

`confirmClientRequestId` is accepted for client compatibility but P5 idempotency is draft-status based.

## Traceability

P5 adds nullable transaction trace columns:

- `voice_session_id`
- `voice_session_draft_id`

Transactions created through VoiceSession confirm also keep the existing voice source fields:

- `sourceType=VOICE`
- `sourceReference=voice-session:{sessionId}:draft:{draftId}`
- note from request override or draft source text

Session audio is not persisted in P5. `voiceRecordId` remains available for legacy voice flows and remains null for this new session-confirm path until pre-confirm audio persistence is added.

## No Auto-Post Rule

These operations do not create transactions:

- create session
- update transcript
- transcribe
- interpret

Only confirm endpoints create ledger/domain state.

## Re-Interpret Rule

Re-interpreting a session after any draft is confirmed is rejected with `VOICE_SESSION_ALREADY_HAS_CONFIRMED_DRAFTS`. This preserves confirmed draft history and prevents duplicate transaction creation.
