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
