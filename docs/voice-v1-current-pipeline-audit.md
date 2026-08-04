# MoneyFlow Voice V1 Current Pipeline Audit

## Scope

This audit documents the current implementation before Voice V1/PhoWhisper work. It is intentionally docs-only. No `VoiceSession` table, endpoint, ASR service, UI behavior, parser, or business logic is implemented here.

## A. Frontend Audio Capture

1. Recorder component: `moneyflow-ui/moneyflow-ui/src/components/quick-entry/VoiceEntryCard.vue`, rendered by `FinancialInboxView.vue:12` and template usage around `FinancialInboxView.vue:326`. `FastVoiceCaptureButton.vue:1-15` states it is presentational and emits start/stop events only.

2. Recording APIs:
   - `VoiceEntryCard.vue:205-226` uses `navigator.mediaDevices.getUserMedia({ audio: true })` and `new MediaRecorder(mediaStream)`.
   - `VoiceEntryCard.vue:174-202` uses browser `SpeechRecognition` / `webkitSpeechRecognition`.
   - `VoiceEntryCard.vue:599-603` also allows manual typed transcript.
   - No custom audio library was found in the inspected voice capture path.

3. Audio MIME type:
   - `VoiceEntryCard.vue:225` creates `new MediaRecorder(mediaStream)` without requesting a MIME type.
   - `VoiceEntryCard.vue:226` stores `mediaRecorder.mimeType`.
   - `VoiceEntryCard.vue:231` creates a `Blob` with `mediaRecorder?.mimeType || 'audio/webm'`.

4. Browser dependence: yes. Because no MIME type is requested, produced MIME is browser-selected.

5. Duration: yes. `VoiceEntryCard.vue:78` stores `elapsedSeconds`; `VoiceEntryCard.vue:242-244` increments it every second; `VoiceEntryCard.vue:503-504` renders the timer.

6. File size: not measured in frontend before upload. Backend records `file.getSize()` in `VoiceAudioService.java:80`.

7. Waveform/volume/mic level: not measured. Search found no analyser, waveform, level, RMS, volume, or silence detector in the current voice capture path.

8. Silence detection: none found.

9. Audio-too-short detection: frontend has no duration gate. `FastVoiceCaptureButton.vue:46-69` only avoids accidental hold shorter than 300 ms before starting recording. Backend upload validates `durationSeconds < 1 || > 300` in `VoiceAudioService.java:273-280`.

10. Audio-too-quiet detection: none found.

11. Blob storage before upload: `VoiceEntryCard.vue:88-93` keeps `chunks` and `recordedBlob`; `VoiceEntryCard.vue:231-234` builds local blob URL; `VoiceEntryCard.vue:586-591` shows the local audio player and text that audio is temporary in the browser.

12. Retry before interpret: user can reset/discard and record again via `resetVoice` at `VoiceEntryCard.vue:377-393`, but current UI creates a draft from transcript after recording. There is no explicit upload/transcribe retry because upload/transcribe are not pre-interpret steps today.

## B. Frontend Transcript Source

1. Current source:
   - Browser speech recognition: `VoiceEntryCard.vue:174-202`.
   - User typed transcript: `VoiceEntryCard.vue:599-603`.
   - Stored draft restored from `sessionStorage`: `VoiceEntryCard.vue:413-435`.
   - Backend ASR: none found.

2. NFC normalization: yes. `src/utils/unicode.ts:6-8` normalizes input to NFC; `VoiceEntryCard.vue:462-468`, `VoiceReviewPanel.vue:189-195`, and `VoiceReviewDraftCard.vue:225-227` apply it.

3. Transcript confidence: no ASR transcript confidence exists. Draft confidence exists in backend review response (`VoiceReviewDraftResponse.java:24`) and frontend type (`voice-review.ts:109`), but it is parser/review confidence, not ASR confidence.

4. ASR segments: no segment field in current frontend voice command/review types.

5. User edit before interpreting: yes. `VoiceEntryCard.vue:599-603` binds textarea to `transcript`; `VoiceEntryCard.vue:270-292` sends `finalTranscript.value`.

6. Typed text without audio: yes. `canCreateDraft` at `VoiceEntryCard.vue:106-112` only requires workspace and transcript, not audio.

7. Shared path: yes. Audio-created transcript and typed transcript both call `voiceReviewService.parse` in `VoiceEntryCard.vue:287-292`.

## C. Frontend Upload And `voiceRecordId` Lifecycle

1. Upload service: `src/services/quick-entry.service.ts:54-61` posts `FormData` through `uploadVoiceAudio`.

2. Backend endpoint: frontend posts `/voice-records/{voiceRecordId}/audio`; backend maps this as `/api/voice-records/{voiceRecordId}/audio` in `VoiceRecordController.java:34-40`.

3. `voiceRecordId` creation: backend parse creates/reuses it. `VoiceReviewService.java:93-101` parses transcript then calls `findOrCreateDraftRecord`; `VoiceReviewService.java:175-190` persists a `VoiceRecord` with transcript.

4. Frontend keeping ID after upload: `VoiceEntryCard.vue:329` stores `storedVoiceRecordId.value = voiceRecordId` after successful upload.

5. Passing ID into `/voice-command/interpret`: no. `voice-command.ts:3-8` request has no `voiceRecordId`; backend `VoiceCommandInterpretRequest.java:9-14` also has no field.

6. Passing ID into confirm: yes. `VoiceReviewPanel.vue:221-230` confirms with `voiceRecordId`; `VoiceReviewDraftCard.vue:250-260` confirms a draft item with `props.voiceRecordId`; service routes are in `voiceReview.service.ts:22-33`.

7. Separate upload failure: yes after confirm. `VoiceEntryCard.vue:320-345` sets `audio-upload-warning` and upload-specific messages. Interpret/parse errors are handled separately in `VoiceEntryCard.vue:270-303`.

8. Confirm if audio upload failed: yes by ordering. Confirm happens before upload in `VoiceEntryCard.vue:361-374`; therefore upload cannot block confirm.

## D. Backend Voice Record/Audio Lifecycle

1. Controllers:
   - `VoiceReviewController.java:31-69` creates/reviews/confirms draft records through parse/patch/confirm routes.
   - `VoiceRecordController.java:34-69` uploads, plays, streams, and deletes audio by `voiceRecordId`.
   - There is no session controller yet.

2. Upload endpoint: `POST /api/voice-records/{voiceRecordId}/audio` in `VoiceRecordController.java:34-40`.

3. Existing statuses: `VoiceRecordStatus.java:3-13` has `DRAFT`, `UPLOADED`, `AUDIO_STORED`, `TRANSCRIBED`, `PARSED`, `CONFIRMED`, `STORAGE_FAILED`, `FAILED`, `AUDIO_DELETED`, `DELETED`.

4. Status changes:
   - Stored: `VoiceAudioService.java:82-94` uploads and sets `AUDIO_STORED`.
   - Failed: `VoiceAudioService.java:95-115` clears storage fields and sets `STORAGE_FAILED`.
   - Deleted: `VoiceAudioService.java:199-210` clears audio metadata and sets `AUDIO_DELETED`.
   - Confirmed: `VoiceReviewService.java:143-144` sets `CONFIRMED` for single-draft confirm. Multi-draft confirm at `VoiceReviewService.java:149-172` does not update the record status to confirmed in the inspected code.

5. Storage provider: abstracted by `VoiceAudioStorageService`; implementations found include disabled, S3, and Cloudinary storage services. `VoiceAudioService.java:44` depends on the abstraction.

6. Storage not configured: `VoiceAudioService.java:305-307` throws `STORAGE_NOT_CONFIGURED` for playback; tests at `VoiceAudioServiceTests.java:46-55` show upload with disabled storage leaves transcript intact and marks `STORAGE_FAILED`.

7. Transcript on storage failure: yes. `VoiceAudioServiceTests.java:124-134` asserts provider failure keeps `originalTranscript` and sets `STORAGE_FAILED`.

8. Playback endpoints:
   - `GET /api/voice-records/{voiceRecordId}/playback-url`: `VoiceRecordController.java:43-47`.
   - `GET /api/voice-records/{voiceRecordId}/playback`: `VoiceRecordController.java:49-53`.
   - `GET /api/voice-records/{voiceRecordId}/audio`: `VoiceRecordController.java:55-63`.

9. `playbackAvailable`: backend maps true when a linked `VoiceRecord` has storage key metadata. `TransactionService.java:1133-1149` computes `hasAudio` from `audioStorageKey`, `storageKey`, or `storagePublicId`, then sets `hasVoiceAudio`, `voiceAudioAvailable`, and `playbackAvailable`.

## E. Backend Transcript/ASR Status Today

1. Real backend ASR: none found in inspected backend voice/audio/command code.

2. PhoWhisper integration: none found.

3. Placeholder/mock/manual transcript path: current path accepts already-produced text. `VoiceReviewParseRequest.java:7-12` has `text`, `transcript`, `rawInput`, `durationSeconds`, and `audioMimeType`; no audio field.

4. Backend audio to transcript: no. Audio upload accepts file and stores it; it does not transcribe. `VoiceAudioService.java:71-115` only validates/stores audio.

5. Transcript persistence: `VoiceRecord.java:59-66` has `originalTranscript`, `editedTranscript`, and `idempotencyKey`; `VoiceReviewService.java:175-190` writes transcript into new records.

## F. Backend Command Interpretation

1. Canonical endpoint: `docs/voice-command-contract.md:3-7` says frontend command center should call `POST /api/workspaces/{workspaceId}/voice-command/interpret`; controller route is `VoiceCommandController.java:17-29`.

2. Request fields: `VoiceCommandInterpretRequest.java:9-14` has `text`, `timezone`, `now`, `source`.

3. Response modes: contract lists modes in `docs/voice-command-contract.md:9-18`; code returns them in `VoiceCommandService.java:39-48` and `VoiceCommandService.java:103-124`.

4. Request accepts `voiceRecordId`: no.

5. Response returns `voiceRecordId`: yes for review paths. `VoiceCommandInterpretResponse.java:23` has it; `VoiceCommandService.java:82` sets it from review.

6. Response returns `drafts[]`: yes. `VoiceCommandInterpretResponse.java:28` has `drafts`; `VoiceCommandService.java:84` sets it.

7. Interpret posts transactions: no evidence of posting. `VoiceCommandService.java:69-87` delegates review to parse; `VoiceCommandIntegrationTests.java:105-124` asserts transaction command routes to review without posting; read-only query test at `VoiceCommandIntegrationTests.java:80-101` asserts transaction/voice counts unchanged.

8. Low-confidence/multi-intent/unsupported:
   - Multi mode if `review.getDrafts().size() > 1` in `VoiceCommandService.java:103-106`.
   - Unsupported if candidate type is `UNKNOWN` or null in `VoiceCommandService.java:121-123`.
   - Mixed query+draft returns `NEEDS_CLARIFICATION` in `VoiceCommandService.java:41-47`.
   - Existing contract requires low-confidence multi-intent not to collapse to one misleading `UNKNOWN` draft in `docs/voice-command-contract.md:76`.

## G. Confirm/Executor

1. Confirm endpoints:
   - Single review confirm: `POST /api/workspaces/{workspaceId}/voice-review/{voiceRecordId}/confirm` at `VoiceReviewController.java:55-60`.
   - One draft item: `POST /api/workspaces/{workspaceId}/voice-review/{voiceRecordId}/drafts/{draftId}/confirm` at `VoiceReviewController.java:63-69`.

2. One draft support: yes. `VoiceReviewService.java:149-172` implements `confirmDraft`.

3. Batch valid drafts: no backend voice-review batch confirm endpoint found. Frontend bulk save loops client-side through each card at `VoiceReviewMultiDraftPanel.vue:93-135`.

4. Idempotency:
   - Single confirm checks existing transaction by `workspaceId + voiceRecordId + VOICE` in `VoiceReviewService.java:128-132`.
   - Draft confirm checks source reference in `VoiceReviewService.java:152-155`.
   - Tests assert no duplicate transaction in `VoiceReviewIntegrationTests.java:183-193` and draft replay in `VoiceReviewIntegrationTests.java:197-209`.

5. `voiceRecordId` preservation: yes. `VoiceReviewService.java:135-142` and `164-171` pass `voiceRecordId` to transaction creation; `TransactionService.java:543-547` sets it.

6. Transcript preservation into transaction: yes as `rawInput`. `VoiceReviewService.java:140` passes edited or original transcript to transaction service; `TransactionService.java:537` stores `rawInput`.

7. Unsupported savings/debt/snapshot: review-only/incomplete unless required fields and supported confirm path pass validation. Frontend disables unsupported/unknown confirms (`VoiceReviewPanel.vue:204-211`, `VoiceReviewDraftCard.vue:239-246`). Tests show savings allocation is not expense and not auto-confirmable in multi-draft (`VoiceReviewIntegrationTests.java:117-123`).

## H. Transaction Display

1. DTO fields: `TransactionResponse.java:58-67` exposes `voiceRecordId`, `voiceTranscript`, `hasVoiceAudio`, `voiceAudioAvailable`, `playbackAvailable`, MIME, size, upload time, and status fields.

2. Frontend use: `TransactionsView.vue:142-165` derives voice audio state; `TransactionsView.vue:773` streams audio by voice record ID.

3. Distinctions:
   - Playable: `PLAYABLE_AUDIO_STATUSES` and availability flags in `TransactionsView.vue:142-165`.
   - Transcript only: default state in `TransactionsView.vue:150-163`.
   - Storage failed: `FAILED_AUDIO_STATUSES` at `TransactionsView.vue:145-146`.
   - Audio deleted: `DELETED_AUDIO_STATUSES` at `TransactionsView.vue:147-148`.
   - No voice data: current helper returns `TRANSCRIPT_ONLY` even for rows without `voiceRecordId`; display conditions decide whether the voice badge/action appears elsewhere.

4. Transcript without audio: backend can map `voiceTranscript` even when `hasAudio` is false (`TransactionService.java:1133-1149`), and tests assert failed upload leaves transaction saved with transcript but not playable (`VoiceTransactionAudioStatusIntegrationTests.java:81-101`).

## I. Known Failure Explanation

1. Multi-intent became one `UNKNOWN` card because current pipeline is transcript-parser-first. Multi-draft rows exist only if parser finds more than one amount candidate and returns candidates. Evidence: `QuickEntryParser.java:102-123` detects amount candidates; `QuickEntryParser.java:701-716` returns no candidates when candidate count is not greater than one; `VoiceReviewService.java:287-297` maps candidates into draft rows; `VoiceCommandService.java:103-123` otherwise treats `UNKNOWN`/null candidate as unsupported. If browser transcript quality collapses or distorts amounts/intents, backend has insufficient evidence to split.

2. Backend or frontend: primarily backend/parser outcome caused by transcript quality and parsing limits. Frontend renders `MULTI` only when backend returns `mode === 'MULTI'` and `drafts.length > 0` (`VoiceReviewPanel.vue:53-60`). So frontend follows backend shape; it does not independently split transcript.

3. Audio can appear lost after saving because audio is uploaded after confirm, and upload can fail independently. Evidence: `VoiceEntryCard.vue:361-374` confirms first, uploads second; `VoiceAudioService.java:95-115` can mark storage failed; playback requires storage keys in `VoiceAudioService.java:305-319`.

4. `voiceRecordId` dropped: not in the confirmed review path. It is preserved into transaction by `VoiceReviewService.java:135-142` and `TransactionService.java:543-547`. It is not passed into `/voice-command/interpret`, because that request has no field.

5. Missing or display mapping: evidence supports both possible cases depending on status. If storage key fields are missing or storage failed/deleted, audio is actually unavailable. If backend maps status/flags incorrectly, UI may hide playback. Current transaction tests cover stored and failed mappings; no evidence found that `voiceRecordId` is dropped in confirm.

## Target State Summary

Voice V1 should be session-based:

```text
Browser MediaRecorder
  -> frontend quality gate
  -> Java backend VoiceSession
  -> audio upload/storage
  -> PhoWhisper ASR
  -> normalized transcript
  -> command interpretation
  -> review queue
  -> explicit confirm
  -> linked transaction/debt/saving/snapshot/query result
```

Interpret must never post ledger writes. Confirm must be the only executor.

## Test/UAT Implications

Current tests already cover:

- Command interpret does not post transactions.
- Multi-draft parser can produce separate rows for known transcript strings.
- Voice confirm idempotency.
- One-draft confirm idempotency.
- Stored audio is exposed on transaction detail/list.
- Failed audio upload leaves transcript and transaction but disables playback.
- Audio validation rejects invalid MIME, empty file, oversized file, disabled storage, missing object, and deleted audio.

Voice V1 needs new tests for:

- Session create/upload/transcribe/interpret lifecycle.
- Pre-confirm audio upload.
- ASR failure without draft creation.
- Low-confidence transcript review.
- Frontend quality gate UAT.
- Multi-intent real audio samples with PhoWhisper output.
- Audio playback after session-based confirm.
