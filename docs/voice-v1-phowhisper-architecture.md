# MoneyFlow Voice V1 PhoWhisper Architecture

## Current Pipeline Audit

### 1. Frontend recording today

Evidence:

- `moneyflow-ui/moneyflow-ui/src/components/quick-entry/VoiceEntryCard.vue:205` checks `navigator.mediaDevices.getUserMedia` and `MediaRecorder`.
- `VoiceEntryCard.vue:224` opens the microphone with `{ audio: true }`.
- `VoiceEntryCard.vue:225` creates `new MediaRecorder(mediaStream)` with no explicit MIME preference.
- `VoiceEntryCard.vue:226` stores `mediaRecorder.mimeType`.
- `VoiceEntryCard.vue:227-232` collects chunks and creates a local `Blob`, defaulting to `audio/webm`.
- `VoiceEntryCard.vue:234` creates a local `blob:` URL for playback before upload.
- `VoiceEntryCard.vue:242-244` tracks duration with a one-second timer.
- `VoiceEntryCard.vue:174-202` also starts browser `SpeechRecognition` / `webkitSpeechRecognition` with `lang = 'vi-VN'`.
- `VoiceEntryCard.vue:491-620` shows timer, mic button, local audio player, editable transcript input, and live transcript. No waveform or mic-level meter exists.
- `moneyflow-ui/moneyflow-ui/src/components/voice/FastVoiceCaptureButton.vue:1-15` is presentational only; it emits recording events and does not record audio itself.

Current mechanism:

- Audio capture: `MediaRecorder`.
- Transcript capture: browser Web Speech API when supported, plus manual textarea editing.
- MIME type: browser-selected `mediaRecorder.mimeType`; blob fallback `audio/webm`.
- Duration: local elapsed seconds counter.
- Audio storage before save: only local browser blob URL.
- Waveform/mic level: none.

### 2. Audio upload today

Evidence:

- `VoiceEntryCard.vue:320-345` uploads audio after save via `uploadAudioAfterSave`.
- `VoiceEntryCard.vue:361-374` waits for review save, reads `voiceReviewDraft.value?.voiceRecordId`, then calls upload.
- `moneyflow-ui/moneyflow-ui/src/services/quick-entry.service.ts:54-61` posts multipart form data to `/voice-records/{voiceRecordId}/audio`.
- `moneyflow-backend/src/main/java/com/moneyflowbackend/voice/controller/VoiceRecordController.java:34-40` exposes `POST /api/voice-records/{voiceRecordId}/audio`.
- `moneyflow-backend/src/main/java/com/moneyflowbackend/voice/service/VoiceAudioService.java:71-115` validates, uploads, sets storage fields, then sets `VoiceRecordStatus.AUDIO_STORED`; failures set `STORAGE_FAILED`.
- `VoiceAudioService.java:238-245` requires the `voiceRecordId` already be linked to a voice transaction before audio upload.

Lifecycle today:

1. User records audio locally.
2. Browser speech recognition produces transcript.
3. Frontend sends transcript to review parse.
4. Backend creates/reuses `voiceRecordId`.
5. User reviews and confirms draft.
6. Backend creates transaction linked to `voiceRecordId`.
7. Frontend uploads audio to that already-linked `voiceRecordId`.
8. Backend changes `voice_status` to `AUDIO_STORED` or `STORAGE_FAILED`.

### 3. Transcript source today

Evidence:

- `VoiceEntryCard.vue:174-202` uses browser `SpeechRecognition` / `webkitSpeechRecognition`.
- `VoiceEntryCard.vue:599-607` exposes editable transcript text area and live transcript.
- `VoiceEntryCard.vue:270-292` sends `finalTranscript.value` to `voiceReviewService.parse`.
- `moneyflow-backend/src/main/java/com/moneyflowbackend/voice/service/VoiceReviewService.java:911-917` reads transcript from `req.transcript`, `req.text`, or `req.rawInput`.

Conclusion: no backend ASR exists today. Transcript comes from browser speech recognition or typed text.

### 4. Does frontend send `voiceRecordId` into interpret?

No.

Evidence:

- `moneyflow-ui/moneyflow-ui/src/types/voice-command.ts:3-8` defines `VoiceCommandInterpretRequest` as `text`, `timezone`, `now`, `source`; no `voiceRecordId`.
- `moneyflow-ui/moneyflow-ui/src/services/voiceCommand.service.ts:137-139` sends that request to `/workspaces/{workspaceId}/voice-command/interpret`.
- `moneyflow-backend/src/main/java/com/moneyflowbackend/voice/command/VoiceCommandInterpretRequest.java:9-14` also has no `voiceRecordId`.

### 5. Does frontend send `voiceRecordId` into confirm?

Yes for review confirm.

Evidence:

- `moneyflow-ui/moneyflow-ui/src/components/voice/VoiceReviewPanel.vue:221-230` reads `activeDraftResponse.value.voiceRecordId` and posts confirm.
- `moneyflow-ui/moneyflow-ui/src/components/voice/VoiceReviewDraftCard.vue:250-260` confirms one draft item with `props.voiceRecordId`.
- `moneyflow-ui/moneyflow-ui/src/services/voiceReview.service.ts:22-33` uses `/voice-review/{voiceRecordId}/confirm` and `/voice-review/{voiceRecordId}/drafts/{draftId}/confirm`.
- `moneyflow-backend/src/main/java/com/moneyflowbackend/voice/controller/VoiceReviewController.java:55-69` accepts `voiceRecordId` in confirm routes.

### 6. Does backend preserve `voiceRecordId` into transaction?

Yes.

Evidence:

- `VoiceReviewService.java:135-142` calls `transactionService.createWithSource(..., voiceRecordId, "voice-review:" + voiceRecordId)`.
- `VoiceReviewService.java:164-171` confirms a multi-draft item with the same `voiceRecordId` and a per-draft source reference.
- `moneyflow-backend/src/main/java/com/moneyflowbackend/transaction/service/TransactionService.java:543-547` validates the voice record and sets `tx.setVoiceRecordId(voiceRecordId)`.
- `moneyflow-backend/src/main/java/com/moneyflowbackend/transaction/dto/TransactionResponse.java:58-67` exposes `voiceRecordId`, `voiceTranscript`, audio booleans, and audio status fields.

### 7. Does transaction list expose playback availability/audio status?

Yes.

Evidence:

- `TransactionResponse.java:60-67` includes `hasVoiceAudio`, `voiceAudioAvailable`, `playbackAvailable`, `audioMimeType`, `audioSizeBytes`, `audioUploadedAt`, `audioStatus`, and `voiceAudioStatus`.
- `TransactionService.java:1133-1149` joins `VoiceRecord`, computes audio booleans from storage keys, maps audio status, and maps transcript.
- `moneyflow-ui/moneyflow-ui/src/types/transaction.ts:144-148` includes voice audio fields.
- `moneyflow-ui/moneyflow-ui/src/views/TransactionsView.vue:142-165` computes playable/transcript-only/error state.
- `TransactionsView.vue:773` streams audio from `/voice-records/{voiceRecordId}/audio`.

### 8. Why can user see transcript but not hear audio?

Because transcript and audio are separate evidence. Transcript is saved during parse; audio is local until after confirm. If upload is skipped, storage is disabled, upload fails, audio is deleted, or storage object is missing, transaction can still show `voiceTranscript` while playback is unavailable.

Evidence:

- `VoiceReviewService.java:175-190` creates a `VoiceRecord` with `originalTranscript` and `editedTranscript`.
- `VoiceEntryCard.vue:367-374` uploads audio only after transaction save.
- `VoiceAudioService.java:238-245` rejects upload until the voice record is linked to a voice transaction.
- `VoiceAudioService.java:305-319` rejects playback when storage is disabled, failed, deleted, or no key exists.
- `TransactionService.java:1136-1149` can map transcript separately from audio storage booleans.

### 9. Why can multi-intent become one UNKNOWN card?

Current parser has multi-amount candidate splitting, but it is still transcript-text based. If ASR/Web Speech produces a transcript where amounts/intents are not recognized as separate `QuickAmountParser` candidates, `buildCandidates` never runs. Then the single preview can fall back to missing type / unsupported intent and the voice command mode becomes `UNSUPPORTED`.

Evidence:

- `QuickEntryParser.java:102-123` depends on parsed amount candidates; only `amountCandidates.size() > 1` marks multiple items.
- `QuickEntryParser.java:237-279` builds response candidates from `buildCandidates`.
- `QuickEntryParser.java:701-716` returns no candidates unless there is more than one amount.
- `VoiceReviewService.java:287-297` returns multi draft rows only when `preview.getCandidates()` is non-empty.
- `VoiceCommandService.java:103-123` returns `MULTI_DRAFT_REVIEW` only when `review.getDrafts().size() > 1`; otherwise `UNKNOWN` or null becomes `UNSUPPORTED`.
- `moneyflow-backend/docs/voice-command-contract.md:76` already states unsafe low-confidence multi-intent must not collapse into one misleading `UNKNOWN` draft.

## Target Architecture

Flow:

```text
Frontend recorder
  -> Java backend VoiceSession API
  -> moneyflow-asr-service PhoWhisper
  -> Java voice command interpreter
  -> frontend review queue
  -> confirm executor
  -> transactions + retained audio/transcript evidence
```

Voice V1 principle: audio is the source evidence, ASR creates transcript, interpreter creates drafts, user confirms transactions.

## A. Frontend Capture Contract

Endpoint flow:

1. `POST /api/workspaces/{workspaceId}/voice-sessions`
2. `POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/audio`
3. `POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe`
4. `POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret`
5. Render `drafts[]` for review.
6. Confirm one draft or all eligible drafts.

Allowed MIME types:

- Prefer `audio/webm;codecs=opus` where `MediaRecorder.isTypeSupported` allows it.
- Accept `audio/webm`, `audio/mp4`, `audio/mpeg`, `audio/wav`.
- Backend canonical allow-list remains authoritative.

Duration:

- Minimum: 1.0 second hard backend minimum, 1.5 seconds frontend warning.
- Target maximum: 60 seconds for V1 UI.
- Backend hard maximum: 300 seconds until migration tightens it.

Quality warnings:

- `VOICE_AUDIO_TOO_SHORT`: duration below minimum.
- `VOICE_AUDIO_TOO_QUIET`: mic level below threshold for most frames.
- `VOICE_AUDIO_CLIPPED`: repeated near-maximum samples.
- `VOICE_AUDIO_NO_INPUT`: no usable audio samples.
- `VOICE_AUDIO_BROWSER_UNSUPPORTED`: no `MediaRecorder`.

Upload lifecycle:

- Create session before upload.
- Upload audio before ASR.
- Keep local blob URL for immediate retry/playback.
- Show `audioStatus`, `asrStatus`, and `commandStatus` separately.
- Never create drafts from empty transcript.

Retry behavior:

- Retry upload/transcribe on explicit user action.
- Reuse same `sessionId` for retry attempts unless user records new audio.
- New recording creates a new session and supersedes old unconfirmed drafts in UI.
- Manual transcript edit is allowed after ASR, but must be stored as `editedTranscript`.

## B. Java Backend VoiceSession Contract

`VoiceSession` fields:

- `id`
- `workspaceId`
- `createdByUserId`
- `source`: `VOICE_CAPTURE`, `MANUAL_TEXT`, `LEGACY_PARSE`
- `audioStatus`: `NOT_UPLOADED`, `UPLOADING`, `UPLOADED`, `AUDIO_STORED`, `AUDIO_INVALID`, `STORAGE_FAILED`, `AUDIO_DELETED`
- `asrStatus`: `NOT_REQUESTED`, `QUEUED`, `TRANSCRIBING`, `SUCCEEDED`, `FAILED`, `TIMEOUT`
- `commandStatus`: `NOT_INTERPRETED`, `INTERPRETED`, `NEEDS_REVIEW`, `PARTIALLY_CONFIRMED`, `CONFIRMED`, `REJECTED`, `FAILED`
- `audioMimeType`
- `audioSizeBytes`
- `durationMs`
- `audioStorageProvider`
- `audioStorageKey`
- `originalTranscript`
- `normalizedTranscript`
- `editedTranscript`
- `asrModel`
- `asrLanguage`
- `asrConfidence`
- `asrWarnings[]`
- `commandWarnings[]`
- `idempotencyKey`
- `createdAt`
- `updatedAt`
- `audioUploadedAt`
- `transcribedAt`
- `interpretedAt`
- `retentionUntil`
- `deletedAt`

`VoiceCommandDraft` fields:

- `id`
- `sessionId`
- `draftIndex`
- `sourceText`
- `type`
- `amount`
- `currency`
- `occurredAt`
- `walletId`
- `categoryId`
- `incomeSourceId`
- `sourceWalletId`
- `destinationWalletId`
- `counterpartyId`
- `debtId`
- `targetFundId`
- `jarId`
- `note`
- `scope`
- `affectsWalletBalance`
- `countsAsExpense`
- `countsAsIncome`
- `confidence`
- `status`: `DRAFT`, `NEEDS_REVIEW`, `READY`, `CONFIRMED`, `SKIPPED`, `UNSUPPORTED`, `FAILED`
- `canConfirm`
- `needsFields[]`
- `warnings[]`
- `suggestions[]`
- `transactionId`
- `idempotencyKey`
- `createdAt`
- `updatedAt`
- `confirmedAt`

Compatibility:

- Existing `VoiceRecord` can be evolved or wrapped by `VoiceSession`.
- Keep legacy `voiceRecordId` in responses until frontend migration completes.
- New responses should expose `sessionId` and alias `voiceRecordId = sessionId` only during compatibility phase if the same table backs it.

## C. PhoWhisper Service Contract

Service: `moneyflow-asr-service`

Endpoint:

`POST /asr/transcribe`

Request:

- `multipart/form-data`
- `audio`: file
- `language`: default `vi`
- `sessionId`: optional string
- `returnSegments`: boolean, default `false`

Response:

```json
{
  "status": "SUCCEEDED",
  "model": "vinai/PhoWhisper-small",
  "language": "vi",
  "durationMs": 4200,
  "transcript": "Hôm nay tôi ăn sáng hết 35 nghìn",
  "normalizedTranscript": "Hôm nay tôi ăn sáng hết 35 nghìn",
  "confidence": null,
  "segments": [],
  "warnings": []
}
```

Failure response:

```json
{
  "status": "FAILED",
  "code": "ASR_TRANSCRIBE_FAILED",
  "message": "Could not transcribe audio",
  "warnings": []
}
```

Error codes:

- `ASR_AUDIO_TOO_SHORT`
- `ASR_AUDIO_TOO_LONG`
- `ASR_NO_SPEECH_DETECTED`
- `ASR_TRANSCRIBE_FAILED`
- `ASR_SERVICE_TIMEOUT`
- `ASR_UNSUPPORTED_FORMAT`

Timeout:

- Java backend call timeout: 30 seconds for dev, configurable.
- ASR service internal timeout: 25 seconds for dev.
- Return `ASR_SERVICE_TIMEOUT` without creating drafts.

Model config:

- `PHOWHISPER_MODEL=vinai/PhoWhisper-small`
- `ASR_DEVICE=auto|cpu|cuda`
- `ASR_COMPUTE_TYPE=default|float16|int8`
- `ASR_MAX_AUDIO_SECONDS=60`

CPU/GPU note:

- CPU is acceptable for dev and low-volume UAT with `PhoWhisper-small`.
- GPU should be used for production-like latency and concurrent users.
- Model name and device must be environment config, not hardcoded.

## D. Command Interpretation Contract

Endpoint:

`POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/interpret`

Request:

```json
{
  "mode": "VOICE_TRANSCRIPT",
  "transcript": "Hôm nay tôi ăn sáng hết 35 nghìn",
  "editedTranscript": null,
  "timezone": "Asia/Bangkok",
  "now": "2026-08-04T12:00:00+07:00"
}
```

Response modes:

- `READ_ONLY_QUERY`
- `TRANSACTION_REVIEW`
- `INCOME_FACT_REVIEW`
- `WALLET_SNAPSHOT_REVIEW`
- `DEBT_DRAFT`
- `MULTI_DRAFT_REVIEW`
- `NEEDS_CLARIFICATION`
- `UNSUPPORTED`

Rules:

- Return `drafts[]` for every mutation-capable interpretation, including single draft.
- Return `query` only for read-only query.
- For mixed query and mutation, default to `NEEDS_CLARIFICATION`; do not save.
- For low confidence multi-intent, return no draft or non-confirmable drafts with `VOICE_MULTI_INTENT_LOW_CONFIDENCE`.
- For ASR-empty or suspicious transcript, return `ASR_TRANSCRIPT_UNUSABLE` and ask for retry/edit.
- Do not collapse multiple detected amounts into one confirmable draft.

## E. Confirm Contract

Confirm one draft:

`POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/{draftId}/confirm`

Confirm eligible drafts:

`POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/drafts/confirm-eligible`

Request:

```json
{
  "idempotencyKey": "client-generated-key",
  "candidate": {
    "type": "EXPENSE",
    "amount": 35000,
    "walletId": "uuid",
    "categoryId": "uuid",
    "occurredAt": "2026-08-04T12:00:00+07:00",
    "note": "ăn sáng"
  }
}
```

Idempotency:

- Single draft key: `voice-session:{sessionId}:{draftId}` plus optional client idempotency key.
- Confirming an already-confirmed draft returns existing transaction.
- Bulk confirm skips unsupported/incomplete/confirmed drafts and reports counts.

Transaction link:

- Every confirmed transaction stores `voiceRecordId`/`voiceSessionId`.
- Multi-draft transactions share the same session and use per-draft `sourceReference`.

Retention:

- Keep original audio, ASR transcript, normalized transcript, edited transcript, command warnings, and confirmed transaction links according to retention policy.
- Audio deletion must not delete transcript or transaction.
- Transcript deletion/redaction must be explicit and audited.

## Quality Gate

Frontend gate:

- Show recording duration.
- Show mic level/waveform.
- Warn if too quiet.
- Warn if too short.
- Disable send while no audio and no manual transcript.
- Allow retry before save.
- Allow ASR transcript edit before interpret.

Backend gate:

- Enforce min duration, max duration, max file size, allowed content types.
- If audio invalid, do not call ASR.
- Return friendly machine-readable warnings.
- Persist failed status for traceability.

ASR gate:

- Empty transcript: no draft.
- Suspicious transcript: no confirmable draft, ask retry/edit.
- Low confidence: review-only.
- Segment metadata optional in V1, but service contract should support it.

## Backend Changes Needed

- Add `VoiceSession` API around existing `VoiceRecord` evidence model or migrate `VoiceRecord` into a session aggregate.
- Add ASR client with timeout, error mapping, and no-call validation gate.
- Add session interpret endpoint that accepts `sessionId`.
- Keep legacy `voice-review/parse` and `voice-command/interpret` until UAT passes.
- Add draft persistence if current reparse-from-transcript behavior is not acceptable for auditability.
- Add integration logs/metrics for upload, ASR, interpret, confirm.

## Risks

- Browser Web Speech and PhoWhisper transcripts may differ; UAT must compare both during migration.
- Multi-intent quality depends on segmentation and amount recognition after ASR normalization.
- CPU-only ASR may be slow under concurrent use.
- Existing audio upload requires a linked transaction; Voice V1 needs pre-confirm audio storage.
- Vietnamese text must remain real UTF-8 across docs, UI, Java, and JSON.

## Open Decisions

- Reuse `voice_records` as `VoiceSession`, or add new `voice_sessions` and link legacy records.
- Store drafts as JSON on session or normalized `voice_command_drafts` rows.
- Production ASR deployment target and GPU availability.
- Final V1 max duration: 60 seconds or keep 300 seconds.
- Whether manual transcript-only remains available in the same UI or moves behind an explicit fallback.
