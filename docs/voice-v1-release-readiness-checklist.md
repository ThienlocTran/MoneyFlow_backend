# Voice V1 Release Readiness Checklist

## Must Pass Before Voice Beta

- Backend VoiceSession create, transcript update, transcribe, interpret, confirm-one, confirm-eligible, skip tests pass.
- Frontend `type-check`, mojibake scan, and production build pass.
- ASR service pytest passes in mock mode.
- ASR mock health live/ready passes.
- Authenticated API E2E matrix runs against local backend and records evidence.
- Browser UAT runs on `/financial-inbox` with screenshots or network evidence.
- Real PhoWhisper smoke runs with `vinai/PhoWhisper-small` or the chosen beta model.
- Confirm creates exactly one transaction and duplicate confirm does not duplicate.
- Interpret/transcribe never auto-post.
- Voice-created transaction exposes `voiceSessionId` and `voiceSessionDraftId`.
- Unsupported domain intents are not silently converted to expense.
- Mobile 360px capture/review/confirm flow has no horizontal overflow.

## Nice To Have

- Checked-in non-sensitive sample audio for ASR smoke.
- Scripted local E2E runbook that creates a temporary user/workspace.
- Browser network capture checklist.
- Reduced-motion automated visual check.
- Latency p50/p95 capture for ASR and interpret.

## Known Limitations Acceptable For Beta

- Session-confirm transactions are transcript/session traceable but not audio-playable until pre-confirm audio persistence exists.
- `INCOME_FACT`, savings/funds, wallet snapshot, debt movement, and read-only query drafts require manual/domain-specific handling.
- `confirmClientRequestId` is accepted, while P5 idempotency is draft-status based.

## Not Acceptable

- Transaction creation during create/update/transcribe/interpret.
- Duplicate transaction on repeated confirm.
- Unsupported command silently saved as expense or income.
- Raw warning codes shown as primary user-facing text.
- Fake financial rows, fake wallets, fake transactions, or fallback mock responses in runtime UI.
- Mojibake in touched source/docs/UI text.

## P8-Follow-up Status

| Gate | Status | Evidence |
| --- | --- | --- |
| Docker local DB | PASS | `moneyflow-voice-uat-postgres` healthy on `127.0.0.1:15432` |
| ASR mock health | PASS | `/health/live` returned UP |
| Backend startup | PASS | P8F-BLOCKER-1 added `V27__fix_voice_session_draft_currency_type.sql`; live/ready returned UP against current DB with user permission |
| Authenticated API E2E | PARTIAL | Text multi-intent, confirm-one, confirm-eligible limitation, and traceability passed; audio mock transcribe failed with `VOICE-P8F-RERUN-001` |
| Browser UAT | PARTIAL | `/financial-inbox` text fallback rendered 4 cards and used VoiceSession endpoints; recording scenarios not run because audio mock API path failed |
| Real PhoWhisper smoke | NOT RUN | Deferred until mock live E2E can run |

Beta readiness: NO. `VOICE-P8F-001` and `VOICE-P8F-RERUN-001` are resolved, but browser recording UAT and real PhoWhisper smoke still remain.

## P9A Status

| Gate | Status | Evidence |
| --- | --- | --- |
| Direct ASR mock multipart | PASS | HTTP 200, provider `MOCK`, transcript present, warning `ASR_MOCK_TRANSCRIPT` |
| Backend external_http audio transcribe | PASS | User-approved Neon.tech current DB live UAT returned `asrStatus=SUCCEEDED`, transcript stored, normalized transcript stored |
| Interpret after transcribe | PASS | 1 `EXPENSE` draft returned |
| Transaction safety | PASS | No transaction before confirm: count stayed 0 before transcribe, after transcribe, and after interpret |
| Browser recording UAT | NOT RUN | Rerun still required after P9A fix |
| Real PhoWhisper smoke | NOT RUN | Still deferred |

P9A root cause: Java `HttpClient` attempted HTTP/2 cleartext upgrade (`h2c`) against Uvicorn, which returned HTTP 400 before multipart handling. Backend ASR requests now force HTTP/1.1.
