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
