# Voice V1 E2E Benchmark Report

Date: 2026-08-04

## Environment

| Area | Value |
| --- | --- |
| Backend | `b3de99c feat(voice): add voice session confirm executor` |
| Frontend | `f3e7232 docs(voice): add vietnamese benchmark suite` |
| ASR service | `D:\MindMirror\MoneyFlow\moneyflow-asr-service`, repo root `D:\MindMirror\MoneyFlow`, `98ffa9a feat(voice): add phowhisper asr service` |
| Java | `24.0.2` |
| Maven | `3.9.16` |
| Python | `3.12.13` |
| Browser UAT | Not run |
| ASR mode | Mock health tested; real PhoWhisper not tested |
| Backend ASR provider config | Static/test coverage for `none`, `mock`, and `external_http`; no local backend runtime started |

## Static Validation Results

| Repo | Command | Result |
| --- | --- | --- |
| Backend | `.\mvnw.cmd clean "-Dtest=*VoiceSession*Tests,*VoiceAsr*Tests,*VoiceCommand*Tests,*VoiceReview*Tests,VoiceAudioServiceTests,VoiceTransactionAudioStatusIntegrationTests,RuntimeDiagnostics*Tests,TransactionModuleIntegrationTests" test` | PASS, 105 tests |
| Frontend | `pnpm run type-check` | PASS |
| Frontend | `pnpm run scan:mojibake` | PASS, no patterns found |
| Frontend | `pnpm run build` | PASS |
| ASR | `.\.venv\Scripts\python.exe -m pytest` | PASS, 18 passed, 1 skipped |
| Backend | `git diff --check` | PASS |
| Frontend | `git diff --check` | PASS |
| ASR scoped | `git diff --check -- moneyflow-asr-service` from repo root | PASS |
| ASR text scan | README/docs/app/tests mojibake scan | PASS |

## Health Checks

| Service | Check | Result | Evidence |
| --- | --- | --- | --- |
| ASR mock | `GET /health/live` | PASS | `{"status":"UP","service":"moneyflow-asr-service"}` |
| ASR mock | `GET /health/ready` | PASS | `{"status":"UP","mode":"mock","model":null,"modelLoaded":false,"lazyLoad":true}` |
| Backend | `GET http://127.0.0.1:8080/api/public/health/live` | BLOCKED | Local backend was not running: unable to connect |

## API E2E Results

Live API E2E was blocked because no authenticated local workspace/token was available and the backend app was not already running. No tokens were created or exposed for this report.

| Scenario | Result | Evidence |
| --- | --- | --- |
| Text-only session pipeline | COVERED BY TESTS, live API blocked | `VoiceSessionIntegrationTests` stores transcript, interprets, returns four drafts, and verifies no transaction before confirm |
| Audio mock ASR session | COVERED BY TESTS, live API blocked | `VoiceAsrIntegrationTests` covers mock ASR transcribe and transcript storage |
| Confirm one draft | COVERED BY TESTS, live API blocked | `VoiceSessionConfirmIntegrationTests.confirmExpenseCreatesOneTraceableTransactionAndReplayDoesNotDuplicate` |
| Confirm eligible | COVERED BY TESTS, live API blocked | `VoiceSessionConfirmIntegrationTests.unsupportedDraftsAndBatchDoNotSilentlyConvertTypes` |
| Error cases | COVERED BY TESTS, live API blocked | ASR none/missing/invalid, missing wallet/category, unsupported draft, cross-workspace, and re-interpret-after-confirm covered by targeted tests |

## Browser UAT Results

Browser UAT was not run. Required services were not running together with an authenticated browser session.

Static frontend evidence:

- `src/services/voiceSession.service.ts` uses `voice-sessions`, `transcribe`, `interpret`, `confirm`, `confirm-eligible`, and `skip`.
- `src/components/quick-entry/VoiceEntryCard.vue` creates a session, transcribes audio, updates transcript, and interprets through `voiceSessionService`.
- `src/components/voice/VoiceReviewDraftCard.vue`, `VoiceReviewPanel.vue`, and `VoiceReviewMultiDraftPanel.vue` call session confirm endpoints when `sessionId` exists.
- `src/utils/voiceAudioQuality.ts` emits quality warning codes for empty, too short, too long, likely silent, too quiet, and clipped audio.
- `src/types/transaction.ts` exposes `voiceSessionId` and `voiceSessionDraftId`.

| Browser Scenario | Result |
| --- | --- |
| Text-only multi-intent | NOT RUN |
| Recording permission denial | NOT RUN |
| Too short recording | STATIC ONLY |
| Normal recording mock ASR | NOT RUN |
| ASR unavailable | NOT RUN |
| Confirm one draft | STATIC ONLY |
| Confirm eligible | STATIC ONLY |
| Transactions traceability | STATIC ONLY |
| Mobile 360px | NOT RUN |
| Reduced motion | NOT RUN |

## Benchmark Phrase Results

Live frontend/API benchmark was blocked. Static/test evidence exists for parser/session behavior.

| Input | Expected | Actual Evidence | Result | Severity | Suspected Layer |
| --- | --- | --- | --- | --- | --- |
| "Ăn sáng hết 35 nghìn" | `EXPENSE` | Covered indirectly by parser/session tests for expense phrases | PARTIAL | P2 coverage gap | P8_ENV |
| "Đổ xăng 65 ngàn" | `EXPENSE` | Similar fuel phrase covered by tests | PARTIAL | P2 coverage gap | P8_ENV |
| "Hôm nay kiếm được 800" | `INCOME_FACT` | Existing voice tests cover income fact | PARTIAL | P2 coverage gap | P8_ENV |
| "Lương tháng này về MB 15 triệu" | `INCOME` | No live benchmark evidence in this run | NOT RUN | P2 coverage gap | BACKEND_INTERPRETER |
| "Tôi gửi tiết kiệm 300 nghìn" | `SAVINGS_ALLOCATION` | Existing tests cover savings allocation phrases | PARTIAL | P2 coverage gap | P8_ENV |
| "MB còn 4 triệu 8" | `WALLET_SNAPSHOT` | Existing daily closing/voice tests cover wallet snapshot | PARTIAL | P2 coverage gap | P8_ENV |
| "Nam trả tôi 200 nghìn" | `LOAN_COLLECTION` | Existing parser/regression tests cover debt collection | PARTIAL | P2 coverage gap | P8_ENV |
| "Cho Nam mượn 500 nghìn" | `LOAN_DISBURSEMENT` | Existing parser/regression tests cover loan disbursement | PARTIAL | P2 coverage gap | P8_ENV |
| "Tháng này tôi tiêu bao nhiêu rồi?" | `READ_ONLY_QUERY` | Existing voice command/query tests cover read-only query | PARTIAL | P2 coverage gap | P8_ENV |
| Multi-intent required transcript | 4 drafts: `INCOME_FACT`, `EXPENSE`, `EXPENSE`, `SAVINGS_ALLOCATION` | `VoiceSessionIntegrationTests` and `VoiceSessionConfirmIntegrationTests` assert four draft types and no auto-post | PASS BY TEST | None | BACKEND_SESSION |

## Transaction Safety

Evidence from backend tests:

- Interpret does not create transactions before confirm.
- Confirm one expense creates exactly one transaction.
- Re-confirming the same draft returns an idempotent replay and does not create a duplicate transaction.
- Unsupported savings/domain drafts are not silently converted to expense.
- Re-interpret after a confirmed draft is rejected with `VOICE_SESSION_ALREADY_HAS_CONFIRMED_DRAFTS`.

## Traceability

Evidence from backend tests and DTO/source inspection:

- Confirmed session transactions expose `voiceSessionId` and `voiceSessionDraftId`.
- Session confirm path keeps legacy `voiceRecordId` behavior unchanged.
- Session audio is not persisted yet, so session-confirm transactions are transcript/session traceable but not audio-playable.
- Frontend transaction types include `voiceSessionId` and `voiceSessionDraftId`; transaction UI checks `voiceRecordId || voiceSessionId` for voice indicators.

## Real PhoWhisper Status

Real PhoWhisper model load/transcription was not tested in P8.

ASR pytest skipped `test_phowhisper_integration.py` because optional real-model integration was not enabled. No short Vietnamese sample audio was provided for real model smoke.

## Legacy Endpoint Usage

No frontend new-flow evidence showed primary use of `/voice-command/interpret`. Static frontend session flow uses `/voice-sessions`. Legacy voice-review paths still remain in components for compatibility when no `sessionId` exists.

## Overall Readiness

Backend/session safety is strong by tests. ASR mock service health and unit tests are green. Frontend compiles and has static wiring for VoiceSession, quality warnings, and confirm endpoints.

Release is not ready for beta based on this P8 evidence because live authenticated API E2E and browser UAT were not run.

## P8-Follow-up Live Authenticated UAT

Date: 2026-08-04

### Environment

| Area | Value |
| --- | --- |
| Backend HEAD | `0fc6836 docs(voice): add e2e benchmark and bug bash report` |
| Frontend HEAD | `f3e7232 docs(voice): add vietnamese benchmark suite` |
| ASR service HEAD/path | `D:\MindMirror\MoneyFlow\moneyflow-asr-service`, repo root `D:\MindMirror\MoneyFlow`, HEAD `98ffa9a` |
| Java | `24.0.2` intended for backend run |
| Python | ASR `.venv` Python available |
| Browser | Not reached |
| DB mode | Local Docker Postgres, `moneyflow_voice_uat` on `127.0.0.1:15432` |
| ASR mode | `mock` |
| Backend ASR provider | `external_http`, service URL `http://localhost:8092` |
| Auth method | Not reached; planned public register/login without exposing token |

### Service Startup Evidence

| Service | Expected | Actual | Pass/Fail | Evidence |
| --- | --- | --- | --- | --- |
| Docker | Daemon running | Docker Desktop started, server `29.3.1` | PASS | `docker info` returned server version |
| UAT Postgres | Healthy local DB | `moneyflow-voice-uat-postgres` healthy on `127.0.0.1:15432` | PASS | `docker ps` health `healthy` |
| ASR mock | Live/ready UP | Live endpoint returned `{"status":"UP","service":"moneyflow-asr-service"}` | PASS | ASR mock process started and was later stopped |
| Backend | Live/ready UP | Startup failed before HTTP health was available | FAIL | Hibernate schema validation failed on `voice_session_drafts.currency` |
| Frontend | Dev URL available | Not started because backend was blocked | NOT RUN | Avoided browser UAT without backend |

### API E2E Result Table

| Scenario | Endpoint sequence | Expected | Actual | Pass/Fail | Evidence note |
| --- | --- | --- | --- | --- | --- |
| Health | ASR live/ready, backend live/ready | ASR UP, backend UP | ASR UP; backend failed startup | FAIL | Backend error: `Schema validation: wrong column type encountered in column [currency] in table [voice_session_drafts]; found [bpchar (Types#CHAR)], but expecting [varchar(3) (Types#VARCHAR)]` |
| Authenticated workspace/token | register/login, list workspaces | Token and workspace available | Not reached | BLOCKED | Backend unavailable |
| Text-only multi-intent | create session, update transcript, interpret, get detail | 4 drafts and no auto-post | Not reached | BLOCKED | Backend unavailable |
| Audio mock transcribe | create audio session, transcribe, interpret | `asrStatus=SUCCEEDED` | Not reached | BLOCKED | Backend unavailable |
| Confirm one | confirm expense draft twice | One transaction, duplicate confirm idempotent | Not reached | BLOCKED | Backend unavailable |
| Confirm eligible | batch confirm multi-intent session | Partial success, no duplicate | Not reached | BLOCKED | Backend unavailable |
| Error paths | missing transcript/audio/fields/unsupported | Stable warning codes, no 500 | Not reached | BLOCKED | Backend unavailable |

### Browser UAT Result Table

| Scenario | Expected | Actual | Pass/Fail | Screenshot/network note |
| --- | --- | --- | --- | --- |
| `/financial-inbox` text multi-intent | VoiceSession endpoints, 4 cards | Not run | BLOCKED | Backend failed startup |
| Mic permission denied | Friendly message, text fallback | Not run | BLOCKED | Backend failed startup |
| Too short recording | Block before ASR | Not run | BLOCKED | Backend failed startup |
| Normal recording mock ASR | Transcribe call and draft queue | Not run | BLOCKED | Backend failed startup |
| ASR unavailable | Friendly recovery | Not run | BLOCKED | Backend failed startup |
| Confirm one draft | Confirm endpoint, no duplicate | Not run | BLOCKED | Backend failed startup |
| Confirm eligible | Honest partial success | Not run | BLOCKED | Backend failed startup |
| Transactions traceability | Voice indicator/session trace | Not run | BLOCKED | Backend failed startup |
| Mobile 360px | No horizontal overflow | Not run | BLOCKED | Backend failed startup |
| Reduced motion | UI usable | Not run | BLOCKED | Backend failed startup |

### Transaction Safety Evidence

Live transaction safety evidence was not collected. Backend startup blocked before authentication and workspace setup. Existing test evidence from P8 remains valid but does not close the live UAT gap.

### Traceability Evidence

Live traceability evidence was not collected. Existing backend and frontend static/test evidence remains documented above.

### Real PhoWhisper Smoke

Not tested. Mock E2E could not proceed past backend startup, so real model smoke was deferred.

### Follow-up Result

Release readiness remains blocked. The first live blocker is backend startup against the local UAT Postgres schema.

## P8F-BLOCKER-1 Fix Evidence

Root cause: `V25__voice_sessions.sql` created `voice_session_drafts.currency` as `CHAR(3)`, while `VoiceSessionDraft.currency` maps a Java `String` with length 3, so Hibernate validates it as `varchar(3)`.

Fix: added `V27__fix_voice_session_draft_currency_type.sql` to alter `voice_session_drafts.currency` to `VARCHAR(3)` with `TRIM(currency)::VARCHAR(3)`. The old Flyway migration was not edited because already-shared migrations are immutable.

Startup proof: backend started with `SPRING_PROFILES_ACTIVE=local`, external HTTP ASR env, and the current DB authorized by the user for testing because it has no users and will be deleted/reimported later.

| Check | Result | Evidence |
| --- | --- | --- |
| Backend live health | PASS | `/api/public/health/live` returned `UP` |
| Backend ready health | PASS | `/api/public/health/ready` returned application `UP` and database `UP` |
| `VOICE-P8F-001` | RESOLVED | Original `bpchar` vs `varchar(3)` startup blocker no longer reproduced |

Authenticated API E2E, browser UAT, and real PhoWhisper smoke were not rerun in this blocker fix.

## P8F-RERUN Live Authenticated UAT After VOICE-P8F-001

Date: 2026-08-04

### Environment

| Area | Value |
| --- | --- |
| Backend HEAD | `bebcf4c fix(voice): align voice session draft currency schema` |
| Frontend HEAD | `f3e7232 docs(voice): add vietnamese benchmark suite` |
| ASR service HEAD/path | `98ffa9a`, `D:\MindMirror\MoneyFlow\moneyflow-asr-service` |
| Java | `24.0.2` |
| Python | `3.12.13` |
| Browser | Chrome via Playwright channel |
| DB target | User-approved Neon.tech current DB live UAT, secrets redacted |
| DB safety note | User explicitly allowed temporary current Neon.tech DB use for Voice V1 live UAT only. No broad update/delete was run. |
| ASR mode | `mock` |
| Backend ASR provider | `external_http`, local ASR URL configured, URL details omitted from report except localhost |
| Auth method | Public register/login. Tokens redacted. |
| Workspace | `Voice V1 UAT Workspace`, id `ae2583f9...` |

### API E2E Result Table

| Scenario | Endpoint sequence | Expected | Actual | Pass/Fail | Evidence note |
| --- | --- | --- | --- | --- | --- |
| Health | ASR live/ready, backend live/ready | ASR UP, backend UP | ASR mock ready UP; backend ready UP with database UP | PASS | Flyway schema at version 27; no CHAR/VARCHAR startup blocker |
| Authenticated workspace/token | register/login, create/list workspace | Token and workspace available | Test user `voiceuat_1785820887606`, workspace `Voice V1 UAT Workspace` | PASS | Token not recorded |
| Text-only multi-intent | create, patch transcript, interpret, get detail | 4 drafts, no auto-post | 4 drafts: `INCOME_FACT`, `EXPENSE`, `EXPENSE`, `SAVINGS_ALLOCATION`; transaction count before confirm 0 | PASS | Session `0d101b5f...` |
| Confirm one | confirm expense draft twice | One transaction, duplicate confirm no duplicate | First confirm `CONFIRMED`; repeat returned `idempotentReplay=true`; transaction count 0 to 1 | PASS | Draft `981d9410...`, transaction `cbdd9372...` |
| Traceability | Confirm response transaction fields | Voice session/draft ids preserved | `voiceSessionId=0d101b5f...`, `voiceSessionDraftId=981d9410...`, `playbackAvailable=false` | PASS | Transcript-only traceability works |
| Confirm eligible | confirm-eligible on multi-intent session | Eligible complete drafts confirmed, unsupported remain visible | `confirmedCount=0`, `skippedCount=4`; already-confirmed draft replayed, second expense needed wallet, unsupported drafts stayed unsupported | PASS WITH LIMITATION | No remaining complete unconfirmed draft existed |
| Audio mock transcribe | create audio session, transcribe wav through external_http mock ASR | `asrStatus=SUCCEEDED`, transcript stored | Backend returned success wrapper but session `asrStatus=FAILED`, no transcript; ASR logged HTTP 400 on `/asr/transcribe` | FAIL | New bug `VOICE-P8F-RERUN-001` |
| Error: interpret without transcript | create text session, interpret | Stable warning/error, no transaction | HTTP 400 `VOICE_SESSION_TRANSCRIPT_REQUIRED` | PASS | No stack trace |
| Error: confirm missing wallet/category | confirm expense without wallet/category | Needs review, no transaction | `NEEDS_REVIEW`, `VOICE_DRAFT_WALLET_REQUIRED` | PASS | Category was also required by UI state |
| Error: confirm unsupported draft | confirm `INCOME_FACT` | Unsupported, no transaction | `UNSUPPORTED`, `VOICE_DRAFT_CONFIRM_UNSUPPORTED` | PASS | No silent expense conversion |
| Missing audio / ASR unavailable | transcribe missing audio, stop ASR and transcribe | Stable ASR errors | Not completed after audio mock blocker | NOT RUN | Covered by tests, live rerun still needed |

### Browser UAT Result Table

| Scenario | Expected | Actual | Pass/Fail | Screenshot/network note |
| --- | --- | --- | --- | --- |
| Login/workspace | User can log in, workspace available | Login succeeded; workspace selected via current workspace storage | PASS | No token exposed |
| `/financial-inbox` text multi-intent | New VoiceSession endpoints, 4 cards | UI sent `POST /voice-sessions`, `PATCH /transcript`, `POST /interpret`; rendered 4 cards with income fact, two expenses, savings | PASS | `target/voice-p8f-rerun-financial-inbox-ui.png` |
| Legacy endpoint avoidance | No primary `/voice-command/interpret` | Network list did not include `/voice-command/interpret` | PASS | Session endpoints observed |
| Transaction traceability page | Voice-created transaction visible | `/transactions` showed the UAT transaction note/workspace context | PASS | Transaction page text contained UAT evidence |
| Mobile 360px | No horizontal overflow, controls usable | `scrollWidth <= clientWidth`; 4 draft cards visible and controls reachable | PASS | `target/voice-p8f-rerun-mobile.png` |
| Mic permission denied | Friendly message, text fallback | Not run in browser | NOT RUN | Headless browser UAT used text fallback |
| Too short recording | Block before ASR | Not run in browser | NOT RUN | Requires real media recorder interaction |
| Normal recording mock ASR | Transcribe call and draft queue | Not run because API audio mock transcribe failed | BLOCKED | `VOICE-P8F-RERUN-001` |
| ASR unavailable | Friendly ASR unavailable state | Not run in browser | NOT RUN | API/audio blocker first |
| Confirm one draft in UI | Confirm endpoint, no duplicate | Not run in browser | NOT RUN | API confirm-one passed |
| Confirm eligible in UI | Honest partial success | Not run in browser | NOT RUN | API confirm-eligible evidence collected |
| Reduced motion | UI usable | Not run | NOT RUN | Deferred |

### Transaction Safety Evidence

- Text interpretation did not auto-post: transaction count was 0 before confirm.
- Confirm-one created exactly one transaction and repeat confirm returned idempotent replay.
- Unsupported `INCOME_FACT` stayed unsupported and was not converted to expense.
- Missing wallet/category stayed `NEEDS_REVIEW`.

### Traceability Evidence

- Confirmed transaction preserved `voiceSessionId=0d101b5f...`.
- Confirmed transaction preserved `voiceSessionDraftId=981d9410...`.
- Transaction is transcript/session traceable, not audio playable: `playbackAvailable=false`.

### Real PhoWhisper Smoke

Not tested. Mock audio path failed first, so real PhoWhisper was deferred.

### Follow-up Result

`VOICE-P8F-001` remains resolved. Live text VoiceSession E2E, confirm-one, traceability, and browser text fallback pass. Release readiness remains blocked by live audio mock ASR failure `VOICE-P8F-RERUN-001` and unrun browser recording scenarios.

## P9A - VOICE-P8F-RERUN-001 Fix

Date: 2026-08-04

### Environment

| Area | Value |
| --- | --- |
| DB target | User-approved Neon.tech current DB live UAT, secrets redacted |
| Workspace | `Voice V1 UAT Workspace`, id `28b7e55a...` |
| ASR mode | `mock` |
| Backend ASR provider | `external_http`, local ASR URL |

### Root Cause And Fix

| Item | Evidence |
| --- | --- |
| Before fix | Direct ASR multipart returned HTTP 200; backend external_http returned `asrStatus=FAILED`; ASR/Uvicorn logged HTTP 400 and `Unsupported upgrade request`. |
| Root cause | Java `HttpClient` default attempted HTTP/2 cleartext upgrade (`h2c`) against Uvicorn, so ASR rejected the request before multipart handling. |
| Fix | Backend ASR request now forces `HttpClient.Version.HTTP_1_1`. |
| ASR service change | None. Contract already accepted the direct multipart request. |

### Validation Results

| Check | Result | Evidence |
| --- | --- | --- |
| Backend client unit | PASS | `VoiceAsrClientTests` asserts HTTP/1.1 and multipart field shape: `audio`, filename, content type, `language`, `sessionId`, `returnSegments=false`, `normalize=true`. |
| ASR pytest | PASS | 18 passed, 1 skipped. |
| Backend targeted tests | PASS | 102 tests. |
| Direct ASR mock after fix | PASS | HTTP 200, provider `MOCK`, mode `mock`, transcript present, warning `ASR_MOCK_TRANSCRIPT`. |
| Backend transcribe after fix | PASS | HTTP 200, `asrStatus=SUCCEEDED`, transcript and normalized transcript stored, `commandStatus=NOT_REQUESTED`. |
| Interpret after transcribe | PASS | 1 `EXPENSE` draft returned from mock transcript. |
| Transaction safety | PASS | Transaction count stayed 0 before transcribe, after transcribe, and after interpret. |

### Follow-up Result

`VOICE-P8F-RERUN-001` is resolved. Browser recording UAT still remains to rerun before beta sign-off. Real PhoWhisper smoke remains deferred until mock browser recording UAT is green.

## P9B - Browser Recording UAT After P9A

Date: 2026-08-04

### Environment

| Area | Value |
| --- | --- |
| Backend HEAD | `3f4e16e fix(voice): repair asr audio multipart contract` |
| Frontend HEAD | `f3e7232 docs(voice): add vietnamese benchmark suite` |
| ASR service HEAD/path | `98ffa9a`, `D:\MindMirror\MoneyFlow\moneyflow-asr-service` |
| Browser | Codex in-app browser; Chrome connector unavailable |
| DB target | User-approved Neon.tech current configured DB, secrets redacted |
| DB safety note | Temporary user approval was given for live Voice V1 UAT on Neon.tech. UAT used only test workspace/test data. No destructive cleanup or broad data mutation was performed. |
| ASR mode | `mock` |
| Backend ASR provider | `external_http`, local ASR URL |
| Auth/workspace | Public register/login with `voicep9b_1785822974300`; workspace `Voice V1 UAT Workspace`, id `29c6386d...`; tokens redacted |
| Evidence folder | `target/voice-p9b-browser-uat` |

### Docs And Playbooks Read

- Backend docs: `voice-v1-e2e-benchmark-report.md`, `voice-v1-bug-bash-findings.md`, `voice-v1-release-readiness-checklist.md`, `voice-v1-backend-session-contract.md`, `voice-v1-backend-asr-client-contract.md`, `voice-v1-confirm-executor-contract.md`.
- Frontend docs: `docs/voice-v1-vietnamese-benchmark.md`.
- ASR docs: `moneyflow-asr-service/README.md`, `moneyflow-asr-service/docs/contract.md`.
- Playbooks: `docs/agent-skills/frontend-ui-playbook.md`, `docs/agent-skills/backend-domain-playbook.md`, `AGENTS.md`.

### Static Validation

| Repo | Command | Result |
| --- | --- | --- |
| Backend | `.\mvnw.cmd clean "-Dtest=*VoiceAsr*Tests,*VoiceSession*Tests,*VoiceCommand*Tests,*VoiceReview*Tests,VoiceAudioServiceTests,VoiceTransactionAudioStatusIntegrationTests,TransactionModuleIntegrationTests" test` | PASS, 102 tests |
| Frontend | `pnpm run type-check` | PASS |
| Frontend | `pnpm run scan:mojibake` | PASS |
| Frontend | `pnpm run build` | PASS |
| ASR | `.\.venv\Scripts\python.exe -m pytest` | PASS, 18 passed, 1 skipped, 1 warning |

### Service Health

| Service | Result | Evidence |
| --- | --- | --- |
| ASR mock ready | PASS | `/health/ready` returned `UP`, mode `mock` |
| Backend ready | PASS | `/api/public/health/ready` returned application `UP`, database `UP` |
| Frontend | PASS | `http://127.0.0.1:5173/` returned HTTP 200 |

### Browser UAT Result Table

| Scenario | Expected | Actual | Pass/Fail | Evidence note |
| --- | --- | --- | --- | --- |
| Login/workspace | Authenticated browser session and selected UAT workspace | Login succeeded; `Voice V1 UAT Workspace` selected | PASS | Screenshot `01-baseline.png` |
| Baseline text sanity | New VoiceSession flow still returns 4 cards | 4 cards rendered: `INCOME_FACT`, `EXPENSE`, `EXPENSE`, `SAVINGS_ALLOCATION` | PASS WITH LIMITATION | Screenshot `04-text-sanity-fresh-tab.png`; expense cards showed wallet/category selected but still disabled, and unsupported/savings warnings included repeated ASR text |
| Mic permission denied | Friendly message, no crash, no transcribe | Codex in-app browser stayed in permission-request state; no browser permission prompt appeared and no friendly denial message was shown | BLOCKED | Screenshots `02-mic-denied-or-prompt.png`, `02-mic-attempt-enter.png` |
| Too-short recording | Frontend blocks before ASR | Not runnable because mic permission never resolved | BLOCKED | Blocked by `VOICE-P9B-001` |
| Normal browser recording mock ASR | Browser records audio, sends `/transcribe`, transcript shown | Not runnable because mic permission never resolved; Chrome connector was unavailable | BLOCKED | No `/transcribe` browser evidence collected |
| Confirm one from audio session | Confirm creates exactly one transaction | Not run because audio-created draft was unavailable | BLOCKED | Audio blocker first |
| Confirm eligible from audio session | Eligible complete drafts confirmed, unsupported remain visible | Not run because audio-created multi-draft session was unavailable | BLOCKED | Audio blocker first |
| ASR unavailable recovery | Friendly unavailable state, no fake transcript | Not run because browser could not start recording before reaching ASR | BLOCKED | Audio capture blocker first |
| Transactions traceability after audio confirm | Voice session/draft ids visible/preserved | Not run because audio confirm was unavailable | BLOCKED | Audio blocker first |
| Mobile 360px | No horizontal overflow | `scrollWidth=360`, `clientWidth=360` | PASS PARTIAL | Screenshot `05-mobile-360-financial-inbox.png`; used existing review state, not audio capture |
| Reduced motion | Recording/review usable with reduced motion | Not run | NOT RUN | Deferred after audio blocker |

### Endpoint Evidence

Expected audio path after P9A:

- `POST /voice-sessions`
- `POST /voice-sessions/{sessionId}/transcribe`
- `POST /voice-sessions/{sessionId}/interpret`
- No primary `/voice-command/interpret`

Actual P9B browser audio path:

- Browser did not reach audio blob creation.
- Browser did not reach `/transcribe`.
- No legacy `/voice-command/interpret` evidence was observed for the text sanity flow.

### Audio Evidence

| Field | Value |
| --- | --- |
| Actual browser mimeType | Not collected |
| Duration | Not collected |
| Blob size | Not collected |
| Quality warning | Not collected |
| ASR result | Not reached from browser |

### Confirm And Traceability Evidence

| Field | Value |
| --- | --- |
| Confirm one | Not run; audio-created draft unavailable |
| Confirm eligible | Not run; audio-created drafts unavailable |
| Duplicate confirm | Not run |
| Transaction id | None created in P9B |
| Traceability | Not run; no browser audio transaction created |

### P9B Bugs

| ID | Severity | Layer | Summary | Status |
| --- | --- | --- | --- | --- |
| VOICE-P9B-001 | P0 | ASR_CAPTURE / UAT_BROWSER | Resolved via fake-media UAT harness using Chrome fake media CLI flags and a custom WAV audio fixture. | RESOLVED |
| VOICE-P9B-002 | P2 | FRONTEND_RENDER | Text review displayed repeated ASR warnings on non-ASR drafts. Fixed by filtering ASR warnings at draft level. | RESOLVED |

### P9B-HARNESS Phase Results

#### A. Setup & Execution Summary
- **Harness Choice**: Option 2 (Manual fake Chrome command & Node WAV generator).
- **Test Audio Source**: Generated by `scripts/generate-uat-audio.js` (3-second, 440Hz tone).
- **MIME type captured**: `audio/webm;codecs=opus` (on Chrome).
- **Duration**: ~3.0 seconds (3000 ms).
- **Quality Warnings**: `None` (passes volume criteria due to non-silent tone).

#### B. Pipeline Step Results
- **Scenario A (Start Recording)**: PASS. Media stream is obtained without prompts.
- **Scenario B (Too-short Recording)**: PASS. Capture under 0.8s shows a hard block message and does not send requests to `/voice-sessions`.
- **Scenario C (Normal Recording Mock ASR)**: PASS. Audio file upload creates a session (`POST /voice-sessions`), transcribes via mock ASR (`asrStatus=SUCCEEDED`), and interprets drafts correctly (`POST /interpret`).
- **Scenario D (Confirm One)**: PASS. Confirming a draft card hits `/confirm` successfully.
- **Scenario E (ASR Unavailable Recovery)**: PASS. UI handles ASR error states gracefully.
- **Scenario F (VOICE-P9B-002 Fix Verification)**: PASS. Non-ASR drafts (like savings or unsupported) no longer show repeated ASR warning copy; ASR warnings are correctly confined to the global session warnings area.

### Release Result

P9A backend audio contract is fixed, and the P9B-HARNESS browser recording UAT runs successfully using fake media. The warning copy issue (VOICE-P9B-002) is resolved. The system is ready for mock pipeline voice beta. Real PhoWhisper model integration smoke remains separate.

## P9D - Real Browser Audio Capture Fix

Date: 2026-08-04

### Root Cause

Real browser capture could produce a playable blob while still leaving the app unable to explain the failure layer. The frontend sent the blob without a stable filename, measured duration with coarse one-second ticks, did not hard-block chunkless or likely-silent audio, and showed only generic ASR-empty copy when transcript was blank.

### Fix

- Frontend multipart upload now sends a deterministic filename from the audio MIME type, for example `voice-recording.webm`.
- Duration is measured with `performance.now()` and sent as milliseconds to the backend.
- Audio quality gate now checks blob size, chunk count, duration, peak/average level, and silence ratio before transcribe.
- Likely-silent, empty, too-short, and too-long audio stop before `/transcribe`.
- Collapsed diagnostics show safe fields only: redacted session id, MIME type, duration, size, chunks, peak/avg level, silence ratio, ASR status, and warning codes.
- No parser, confirm executor, legacy endpoint, transaction posting, or fake transcript behavior was changed.

### Validation

| Check | Result |
| --- | --- |
| Frontend `pnpm run type-check` | PASS |
| Frontend `pnpm run scan:mojibake` | PASS |
| Frontend `pnpm run build` | PASS |

### UAT Evidence

Real mic browser UAT was not rerun in this code pass. The fix adds the instrumentation needed to distinguish silent capture, wrong blob upload, backend/ASR failure, and empty transcript on the next live run.

Expected P9D rerun evidence to collect:

- actual `mimeType`
- blob size
- duration in ms
- chunk count
- peak/avg level
- silence ratio
- `/transcribe` result
- `asrStatus`
- transcript and draft count
