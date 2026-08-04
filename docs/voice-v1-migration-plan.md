# Voice V1 PhoWhisper Migration Plan

## Current State

Current voice is transcript-first, not session-first. The frontend records audio locally and gets transcript from browser speech recognition or manual typing. Backend parse creates a `VoiceRecord`; confirm creates the transaction; only after confirm does frontend upload audio to the linked `voiceRecordId`.

This means Phase 2 must prove session lifecycle before adding PhoWhisper.

## Phase 1: Audit And Docs Only

Status: this task.

Deliverables:

- Current pipeline audit.
- Target VoiceSession architecture.
- Frontend flow spec.
- Migration plan.

No runtime code changes.

## Phase 2: VoiceSession Backend Model And Endpoints

Goal: introduce session lifecycle while keeping legacy endpoints.

Backend work:

- Add `VoiceSession` and `VoiceSessionDraft` models.
- Add endpoints:
  - `POST /api/workspaces/{workspaceId}/voice-sessions`
  - `PATCH /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcript`
  - `POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret`
  - `GET /api/workspaces/{workspaceId}/voice-sessions/{sessionId}`
- Add draft response shape with `sessionId`, `voiceRecordId` compatibility alias, `audioStatus`, `asrStatus`, `commandStatus`.
- Persist draft snapshots from the existing text interpreter.
- Keep `asrStatus=NOT_REQUESTED` for manual transcript sessions.
- Keep existing endpoints:
  - `/api/workspaces/{workspaceId}/voice-review/parse`
  - `/api/workspaces/{workspaceId}/voice-command/interpret`
  - `/api/voice-records/{voiceRecordId}/audio`

Acceptance:

- Legacy voice review still works.
- New text session can store transcript and draft snapshots without creating a transaction.
- Re-interpreting a session replaces old unconfirmed draft snapshots instead of duplicating them.
- Transaction response still exposes `voiceRecordId`, `voiceTranscript`, `playbackAvailable`, and `audioStatus` through legacy flows.

P2 implementation notes:

- Adds tables `voice_sessions` and `voice_session_drafts`.
- Adds `POST /api/workspaces/{workspaceId}/voice-sessions`.
- Adds `PATCH /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcript`.
- Adds `POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret`.
- Adds `GET /api/workspaces/{workspaceId}/voice-sessions/{sessionId}`.
- Does not add audio upload, ASR, PhoWhisper, or confirm routes.
- Session interpret uses the existing voice command rules through a preview-only path so legacy `/voice-command/interpret` behavior remains unchanged.
- Manual transcript sessions prepare for PhoWhisper by proving session status, transcript normalization, command interpretation, and persisted draft queue behavior before audio transcription exists.

Text-based session test flow:

1. Create a `TEXT` session.
2. Patch transcript.
3. Post interpret.
4. Check `asrStatus=NOT_REQUESTED`.
5. Check `commandStatus=NEEDS_REVIEW` for draft modes or `INTERPRETED` for read-only query.
6. Check persisted draft count/types.
7. Check transaction count and wallet balance are unchanged.

## Phase 3: Add `moneyflow-asr-service`

Goal: standalone PhoWhisper service.

Suggested stack:

- Python
- FastAPI
- `transformers` pipeline
- `vinai/PhoWhisper-small` for dev

Configuration:

- `PHOWHISPER_MODEL=vinai/PhoWhisper-small`
- `ASR_DEVICE=auto`
- `ASR_MAX_AUDIO_SECONDS=60`

Endpoint:

- `POST /asr/transcribe`

Acceptance:

- Transcribes short Vietnamese expense audio.
- Returns stable failure codes for short, long, empty, unsupported, timeout, and failed transcription cases.
- No financial parsing inside ASR service.

## Phase 4: Wire Java Backend To ASR

Goal: backend owns orchestration and trust boundary.

Backend work:

- Add ASR client and timeout.
- Validate audio before ASR call.
- Store ASR response on session.
- Map ASR failures to friendly API response.
- Do not create drafts if ASR transcript is empty/suspicious.

Acceptance:

- `transcribe` updates `asrStatus`.
- ASR timeout leaves session retryable.
- Backend never calls ASR for invalid MIME, empty file, too-short, too-long, or too-large audio.

P4 implementation notes:

- Adds backend config `MONEYFLOW_ASR_PROVIDER=none|mock|external_http`.
- Adds `POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe`.
- Adds backend audio validation before ASR call.
- Adds `VoiceAsrClient` providers: noop, mock, and external HTTP.
- Stores ASR transcript, normalized transcript, provider/model/language, duration, confidence, and warnings on `VoiceSession`.
- Keeps `audioStatus=NONE` because P4 transcribes uploaded bytes without session audio persistence.
- Keeps `commandStatus=NOT_REQUESTED` after transcription.
- Does not create drafts or transactions during transcription.
- Adds safe ASR runtime diagnostics without exposing service URL or secrets.

Remaining after P4:

- P5 frontend capture flow.
- P6 confirm executor/linking if not implemented.
- P7 UX quality gate and benchmarks.

## Phase 5: Update Frontend Capture Flow

Goal: audio-first review-before-save UX.

New flow:

1. Record audio.
2. Create session.
3. Upload audio.
4. Transcribe with PhoWhisper.
5. Show transcript for edit.
6. Interpret transcript.
7. Show single or multi draft review queue.
8. Confirm one draft or eligible drafts.
9. Refresh transactions.

Frontend work:

- Prefer `audio/webm;codecs=opus` when supported.
- Add mic level/waveform.
- Add too-short and too-quiet warnings.
- Replace browser Web Speech as primary source with backend ASR result.
- Keep manual transcript edit.
- Display separate upload, ASR, interpretation, and confirm states.

Acceptance:

- User can retry upload/transcribe before save.
- User can edit transcript before interpretation.
- Empty/error states never show fake drafts.
- Multi-draft queue uses backend `drafts[]`.

## Phase 6: Deprecate Direct Text-Only Legacy Path

Goal: remove ambiguity only after browser UAT passes.

Rules:

- Keep legacy path through UAT.
- Add feature flag or capability check.
- Only deprecate once supported browsers pass record, upload, transcribe, interpret, confirm, playback.
- Keep explicit manual text fallback if product wants keyboard-first input.

Acceptance:

- No regression for users without ASR service enabled.
- No loss of existing transaction audio playback.

## Phase 7: Benchmarks And UAT Set

Benchmark cases:

- Short expense: "ăn sáng hết 35 nghìn"
- Multi-intent: income + two expenses + savings in one utterance.
- Noisy audio.
- Northern accent.
- Southern accent.
- Amount formats: `35k`, `35 nghìn`, `65.000`, `1 triệu rưỡi`.
- Debt: lend, borrow, collect, repay.
- Saving/fund contribution.
- Wallet snapshot.
- Read-only query.
- Mixed query + mutation.

Metrics:

- ASR latency p50/p95.
- ASR empty transcript rate.
- Multi-intent split accuracy.
- Draft confirm rate.
- Retry rate.
- Audio upload failure rate.

## Test/UAT Implications

- Keep existing integration tests for legacy `/voice-command/interpret`, `/voice-review/parse`, confirm idempotency, and transaction audio status.
- Add session lifecycle tests in Phase 2 before ASR exists.
- Add ASR contract tests in Phase 3 with small recorded Vietnamese samples.
- Add backend ASR-client timeout/failure tests in Phase 4.
- Add frontend UAT scripts for record, retry, upload, transcribe, edit transcript, interpret, confirm, and playback.
- Do not deprecate legacy endpoints until browser UAT passes.

## Rollback

- Keep legacy parse and interpret endpoints during all phases.
- Feature flag frontend Voice V1.
- If ASR service fails, show retry/manual transcript path.
- Do not silently fall back to fake/mock data or auto-save.

## Open Decisions

- New tables versus evolving `voice_records`.
- Whether drafts persist as normalized rows or JSON payload.
- Final production model size.
- GPU hosting target.
- Retention defaults for audio and transcript.
