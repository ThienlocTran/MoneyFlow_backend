# Voice V1 Bug Bash Findings

Date: 2026-08-04

## Findings

### VOICE-P8-001

Title: Live authenticated API E2E was blocked

Severity: P2

Layer classification: BACKEND_SESSION

Steps to reproduce:

1. Attempt P8 API matrix.
2. Probe local backend health at `http://127.0.0.1:8080/api/public/health/live`.
3. Attempt to identify a local authenticated workspace/token.

Expected:

- Backend running locally.
- Authenticated workspace/token available without exposing secrets.
- API matrix can be executed end to end.

Actual:

- Backend local health was unreachable.
- No authenticated local workspace/token was available in the task context.
- API E2E was recorded as blocked, not passed.

Evidence:

- Health probe returned: unable to connect to remote server.
- Static backend tests passed: 105 tests.

Suspected files:

- `src/main/java/com/moneyflowbackend/voice/session/*`
- `src/test/java/com/moneyflowbackend/VoiceSessionIntegrationTests.java`
- `src/test/java/com/moneyflowbackend/VoiceSessionConfirmIntegrationTests.java`

Recommended fix phase:

- P8 follow-up: add a documented local E2E seed/runbook or a non-secret test user bootstrap path.

Blocking release?

- No for code merge; yes for beta sign-off evidence.

### VOICE-P8-002

Title: Browser UAT was not executed

Severity: P2

Layer classification: FRONTEND_STATE

Steps to reproduce:

1. Open `/financial-inbox`.
2. Run text, recording, confirm, mobile, and reduced-motion UAT matrix.

Expected:

- Screenshots, endpoint list, draft counts/types, and confirmation evidence.

Actual:

- Browser UAT was not run because local frontend/backend/authenticated app state was not available together.
- Only static frontend evidence and build/type-check evidence were collected.

Evidence:

- Frontend validation passed: `pnpm run type-check`, `pnpm run scan:mojibake`, `pnpm run build`.
- Static search confirms `voiceSessionService` endpoint wiring and quality-gate code.

Suspected files:

- `src/views/FinancialInboxView.vue`
- `src/components/quick-entry/VoiceEntryCard.vue`
- `src/services/voiceSession.service.ts`
- `src/components/voice/VoiceReviewMultiDraftPanel.vue`

Recommended fix phase:

- P8 follow-up: run browser UAT with local services and authenticated session.

Blocking release?

- Yes for beta readiness; no confirmed product defect yet.

### VOICE-P8-003

Title: Real PhoWhisper transcription was not tested

Severity: P2

Layer classification: ASR_SERVICE

Steps to reproduce:

1. Set `MONEYFLOW_ASR_MODE=phowhisper`.
2. Set `PHOWHISPER_MODEL=vinai/PhoWhisper-small`.
3. Start ASR service.
4. Submit a short Vietnamese audio sample to `POST /asr/transcribe`.

Expected:

- Model loads.
- Response is `SUCCEEDED`.
- Transcript is Vietnamese text without raw errors.

Actual:

- Real model smoke was not run.
- ASR pytest skipped optional PhoWhisper integration.
- Mock-mode health and tests passed.

Evidence:

- ASR pytest: 18 passed, 1 skipped.
- Mock health live/ready: UP.

Suspected files:

- `moneyflow-asr-service/app/providers/phowhisper_provider.py`
- `moneyflow-asr-service/app/transcriber.py`
- `moneyflow-asr-service/app/audio_validation.py`

Recommended fix phase:

- P8 follow-up or P9 hardening: run real model smoke with a checked-in safe sample or documented local sample.

Blocking release?

- Yes for real PhoWhisper beta; no for mock/demo-only validation.

## P0 Bugs

None found in static validation or tests.

## P1 Bugs

None confirmed.

## P2/P3 Bugs

- VOICE-P8-001
- VOICE-P8-002
- VOICE-P8-003

### VOICE-P8F-001

Title: Backend cannot start against local Voice UAT Postgres after VoiceSession migration

Severity: P0

Layer classification: BACKEND_SESSION

Steps to reproduce:

1. Start Docker Desktop.
2. Run `docker compose -f docker-compose.voice-uat.yml up -d`.
3. Start ASR service in mock mode on `127.0.0.1:8092`.
4. Start backend with `SPRING_PROFILES_ACTIVE=local`, `MONEYFLOW_DB_URL=jdbc:postgresql://localhost:15432/moneyflow_voice_uat`, and `MONEYFLOW_ASR_PROVIDER=external_http`.

Expected:

- Backend starts.
- `/api/public/health/live` and `/api/public/health/ready` are reachable.
- Authenticated VoiceSession API E2E can run.

Actual:

- Before fix: backend failed during Hibernate schema validation.
- After `V27__fix_voice_session_draft_currency_type.sql`: backend started and health endpoints returned UP against the current DB. User explicitly allowed testing this DB because it has no users and will be deleted/reimported later.
- Live API E2E and browser UAT still need rerun after this startup fix.

Evidence:

```text
Schema validation: wrong column type encountered in column [currency] in table [voice_session_drafts]; found [bpchar (Types#CHAR)], but expecting [varchar(3) (Types#VARCHAR)]
```

Suspected files:

- `src/main/resources/db/migration/V25__voice_sessions.sql`
- `src/main/java/com/moneyflowbackend/voice/session/VoiceSessionDraft.java`
- `src/main/resources/db/migration/V27__fix_voice_session_draft_currency_type.sql`

Recommended fix phase:

- Fixed in P8F-BLOCKER-1 by adding a new Flyway migration. The pushed historical `V25__voice_sessions.sql` migration was not edited because Flyway migrations are immutable after sharing.

Blocking release?

- No for backend startup. Authenticated API E2E and browser UAT remain not rerun in this task.

Status:

- RESOLVED.

### VOICE-P9D-001

Title: Real browser recording can create audio but still leave transcript empty without layer evidence

Severity: P1

Layer classification: FRONTEND_CAPTURE / FRONTEND_STATE

Steps to reproduce:

1. Open `/dashboard`.
2. Record 5-10 seconds with real microphone.
3. Stop and review the audio/transcript state.

Expected:

- Valid audible audio sends `/voice-sessions`, `/transcribe`, then `/interpret` when transcript is non-empty.
- Silent, empty, too-short, too-long, or chunkless audio is blocked before ASR with a specific message.
- Diagnostics identify MIME type, duration, blob size, chunks, levels, silence ratio, and ASR status.

Actual before fix:

- Audio player could appear while transcript stayed empty.
- UI did not clearly identify whether the issue was silent capture, wrong upload shape, backend/ASR failure, or empty transcript.

Root cause:

- Browser blob upload lacked a deterministic filename.
- Duration was coarse one-second state rather than precise recording duration.
- Quality gate did not hard-block chunkless or likely-silent audio.
- Diagnostic panel missed chunk count, silence ratio, ASR status, and warning codes.

Resolution:

- Added MIME-derived audio filename on multipart upload.
- Added precise duration, chunk count, RMS-assisted mic levels, silence ratio, hard blocks, friendly failure states, and redacted diagnostics.
- Parser, confirm executor, and transaction posting were unchanged.

Status:

- FIXED IN FRONTEND. Real mic UAT rerun still required for beta evidence.

### VOICE-P8F-RERUN-001

Title: Live audio mock transcribe fails through backend external_http ASR path

Severity: P0

Layer classification: BACKEND_ASR_CLIENT / ASR_SERVICE

Steps to reproduce:

1. Start ASR service in mock mode on localhost.
2. Start backend with `MONEYFLOW_ASR_PROVIDER=external_http`.
3. Create an authenticated `AUDIO` VoiceSession.
4. POST a small WAV file to `/api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe`.

Expected:

- Session `asrStatus=SUCCEEDED`.
- Transcript and normalized transcript are stored.
- Interpret can create a draft queue.

Actual:

- Backend response wrapper was successful but session `asrStatus=FAILED`.
- No transcript was stored.
- Follow-up interpret failed with `VOICE_SESSION_TRANSCRIPT_REQUIRED`.
- ASR service log showed HTTP 400 on `/asr/transcribe`.

Evidence:

- DB target: User-approved Neon.tech current DB live UAT, secrets redacted.
- Workspace: `Voice V1 UAT Workspace`.
- Audio session: `4c61cc8d...`.

Suspected files:

- `src/main/java/com/moneyflowbackend/voice/session/VoiceSessionService.java`
- `src/main/java/com/moneyflowbackend/voice/asr/*`
- `moneyflow-asr-service/app/main.py`
- `moneyflow-asr-service/app/transcriber.py`

Recommended fix phase:

- Immediate P8F follow-up before browser recording UAT or real PhoWhisper smoke.

Blocking release?

- Yes for Voice V1 audio beta.

Status:

- RESOLVED in P9A.

### P9A - VOICE-P8F-RERUN-001 Fix

Before fix:

- Direct ASR mock multipart request to `/asr/transcribe` returned HTTP 200 with provider `MOCK`, mode `mock`, transcript data, and warning `ASR_MOCK_TRANSCRIPT`.
- Backend external_http audio transcribe returned a successful API wrapper but persisted `asrStatus=FAILED`; no transcript was stored.
- ASR/Uvicorn logged HTTP 400 and `Unsupported upgrade request` before request handling.

Root cause:

- Java `HttpClient` attempted HTTP/2 cleartext upgrade (`h2c`) by default.
- Uvicorn rejected the upgrade request before the multipart contract could be processed.

Fix:

- Backend ASR client now forces `HttpClient.Version.HTTP_1_1` for `/asr/transcribe`.
- ASR service code was unchanged.

Validation:

- `VoiceAsrClientTests` now asserts HTTP/1.1 plus multipart field shape: `audio`, filename `clip.webm`, `audio/webm`, `language`, `sessionId`, `returnSegments=false`, and `normalize=true`.
- ASR pytest passed: 18 passed, 1 skipped.
- Backend targeted Voice tests passed: 102 tests.
- User-approved Neon.tech current DB live UAT: backend transcribe returned HTTP 200, `asrStatus=SUCCEEDED`, transcript and normalized transcript stored, warning `ASR_MOCK_TRANSCRIPT`, and `commandStatus=NOT_REQUESTED`.
- Interpret after transcribe returned 1 `EXPENSE` draft.
- Transaction count stayed 0 before transcribe, after transcribe, and after interpret.

Blocking release?

- No for this bug. Browser recording UAT still remains to rerun.

### VOICE-P9B-001

Title: Browser recording UAT cannot proceed in available browser surface

Severity: P0

Layer classification: ASR_CAPTURE / UAT_BROWSER

Steps to reproduce:

1. Start ASR mock, backend external_http, and frontend dev server.
2. Log into `Voice V1 UAT Workspace` in the Codex in-app browser.
3. Open `/financial-inbox`.
4. Start recording from the mic button.

Expected:

- Browser either allows mic and enters recording state, or denies mic and shows the friendly micro permission message.
- Too-short, normal recording, ASR unavailable, audio confirm, and traceability scenarios can proceed.

Actual:

- The UI entered a permission-requesting/processing state.
- No browser permission prompt appeared in the available Codex in-app browser.
- No friendly denial message was shown.
- Chrome connector was unavailable, so the run could not switch to a mic-capable external browser.
- No browser audio blob, mimeType, duration, `/transcribe` call, or audio-created draft was produced.

Evidence:

- DB target: User-approved Neon.tech current configured DB, secrets redacted.
- Workspace: `Voice V1 UAT Workspace`, id `29c6386d...`.
- Screenshots: `target/voice-p9b-browser-uat/02-mic-denied-or-prompt.png`, `target/voice-p9b-browser-uat/02-mic-attempt-enter.png`.

Suspected files:

- `src/components/quick-entry/VoiceEntryCard.vue`
- `src/components/voice/FastVoiceCaptureButton.vue`

Resolution:

- Resolved in P9B-HARNESS phase. Created a fake-media browser UAT harness runbook (`docs/voice-v1-browser-recording-uat-harness.md`) using Chromium fake-media and fake-ui flags (`--use-fake-ui-for-media-stream`, `--use-fake-device-for-media-stream`, `--use-file-for-fake-audio-capture`).
- Checked in a Node script `scripts/generate-uat-audio.js` to dynamically generate a 3-second non-silent tone WAV file, which passes the volume quality gate.
- Proved browser E2E recording mock pipeline works repeatably without physical mic access.

Status:

- RESOLVED.

### VOICE-P9B-002

Title: Text sanity review displays repeated ASR transcription failure text on non-ASR drafts

Severity: P2

Layer classification: FRONTEND_RENDER

Steps to reproduce:

1. Open `/financial-inbox`.
2. Use text fallback with the multi-intent benchmark phrase.
3. Interpret and inspect the review cards.

Expected:

- Text fallback renders draft-specific warnings only.
- Non-ASR text sessions do not show ASR transcription failure copy.

Actual:

- Browser text sanity rendered 4 cards, but several cards displayed `Không thể chuyển giọng nói thành văn bản.`.
- Savings/unsupported card showed repeated copies of the same ASR-oriented text.

Evidence:

- Screenshot: `target/voice-p9b-browser-uat/04-text-sanity-fresh-tab.png`.

Suspected files:

- `src/components/voice/VoiceReviewWarnings.vue`
- `src/utils/voiceReviewContract.ts`
- `src/components/quick-entry/VoiceEntryCard.vue`

Resolution:

- Updated `VoiceReviewWarnings.vue` with an `isDraft` prop. When `isDraft` is true (such as on individual draft cards inside `VoiceReviewDraftCard.vue`), ASR-related warning codes (e.g., `ASR_TRANSCRIBE_FAILED`, `ASR_SERVICE_UNAVAILABLE`, etc.) are filtered out and not rendered.
- Global ASR warnings remain visible in the main session-level warnings panel.

Status:

- RESOLVED.
