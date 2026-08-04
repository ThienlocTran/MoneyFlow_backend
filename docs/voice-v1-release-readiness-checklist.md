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
