# Voice V1 Backend Session Contract

## Current State

Legacy voice remains available:

- `POST /api/workspaces/{workspaceId}/voice-command/interpret`
- `POST /api/workspaces/{workspaceId}/voice-review/parse`
- `POST /api/workspaces/{workspaceId}/voice-review/{voiceRecordId}/confirm`

Phase 2 adds a new session foundation for text/manual transcript interpretation. It does not add ASR, PhoWhisper, audio upload, or confirm endpoints.

## Data Model

Tables:

- `voice_sessions`
- `voice_session_drafts`

Session statuses:

- `status`: `CREATED`, `AUDIO_UPLOADED`, `TRANSCRIBING`, `TRANSCRIBED`, `INTERPRETED`, `NEEDS_REVIEW`, `PARTIALLY_CONFIRMED`, `CONFIRMED`, `FAILED`, `CANCELLED`
- `audioStatus`: `NONE`, `UPLOADING`, `STORED`, `STORAGE_FAILED`, `DELETED`
- `asrStatus`: `NOT_REQUESTED`, `PENDING`, `TRANSCRIBING`, `SUCCEEDED`, `LOW_CONFIDENCE`, `NO_SPEECH`, `FAILED`, `TIMEOUT`
- `commandStatus`: `NOT_REQUESTED`, `INTERPRETED`, `NEEDS_CLARIFICATION`, `NEEDS_REVIEW`, `UNSUPPORTED`, `FAILED`
- `confirmStatus`: `NOT_CONFIRMED`, `PARTIALLY_CONFIRMED`, `CONFIRMED`

For P2 text sessions, `asrStatus` stays `NOT_REQUESTED`.

## Endpoints

Base path:

`/api/workspaces/{workspaceId}/voice-sessions`

### Create Session

`POST /api/workspaces/{workspaceId}/voice-sessions`

Request:

```json
{
  "sourceType": "TEXT",
  "clientTimezone": "Asia/Ho_Chi_Minh",
  "clientStartedAt": "2026-08-04T09:50:00+07:00",
  "clientMetadata": {
    "mimeType": null,
    "durationMs": null
  }
}
```

Response:

```json
{
  "success": true,
  "message": "Voice session created",
  "data": {
    "sessionId": "uuid",
    "sourceType": "TEXT",
    "status": "CREATED",
    "audioStatus": "NONE",
    "asrStatus": "NOT_REQUESTED",
    "commandStatus": "NOT_REQUESTED",
    "confirmStatus": "NOT_CONFIRMED"
  }
}
```

### Update Transcript

`PATCH /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcript`

Request:

```json
{
  "transcript": "Hôm nay đã kiếm được 800 Tôi ăn hết 50 Cái đổ xăng hết 65.000 Ta gửi tiết kiệm hết 35"
}
```

Behavior:

- Trims and normalizes transcript to NFC.
- Stores both `transcript` and `normalizedTranscript`.
- Keeps session `status=CREATED`.
- Does not interpret automatically.

### Interpret

`POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret`

Behavior:

- Requires non-blank `normalizedTranscript`.
- Uses existing voice command interpretation logic through a preview-only path.
- Persists current draft snapshots into `voice_session_drafts`.
- Re-interpret replaces prior draft snapshots for this session.
- Does not create transactions.
- Does not create `VoiceRecord`.
- Does not call ASR.

Response includes session fields, `mode`, `query` when read-only, `commandWarnings`, and persisted `drafts`.

### Get Detail

`GET /api/workspaces/{workspaceId}/voice-sessions/{sessionId}`

Returns the session, statuses, transcript fields, warnings, and drafts sorted by `draftIndex`.

## Multi-Intent Example

Transcript:

```text
Hôm nay đã kiếm được 800 Tôi ăn hết 50 Cái đổ xăng hết 65.000 Ta gửi tiết kiệm hết 35
```

Expected P2 outcome:

- mode: `MULTI_DRAFT_REVIEW`
- 4 draft snapshots
- draft 1: `INCOME_FACT`
- draft 2: `EXPENSE`
- draft 3: `EXPENSE`
- draft 4: `SAVINGS_ALLOCATION`
- warning includes multi-intent warning when the existing interpreter emits it
- no transaction is posted

## No Auto-Post Rule

Session interpret is read/draft only. It may create or replace session draft snapshots. It must not create:

- transaction
- debt payment
- savings ledger entry
- emergency fund ledger entry
- wallet snapshot

Confirm routes are intentionally absent in P2.

## Future Notes

Phase 3 adds `moneyflow-asr-service`.

Phase 4 wires Java to ASR.

Later phases add audio upload to sessions and confirm execution that links posted entities back to the session.
