# Voice V1 Backend ASR Client Contract

Phase 4 wires Java `VoiceSession` to the standalone `moneyflow-asr-service`. It does not wire the frontend, does not run PhoWhisper inside Java, and does not confirm or post transactions.

## Config

Environment variables:

- `MONEYFLOW_ASR_PROVIDER=none|mock|external_http`
- `MONEYFLOW_ASR_SERVICE_URL=http://localhost:8092`
- `MONEYFLOW_ASR_TIMEOUT_SECONDS=60`
- `MONEYFLOW_ASR_MAX_AUDIO_SECONDS=60`
- `MONEYFLOW_ASR_MIN_AUDIO_SECONDS=0.8`
- `MONEYFLOW_ASR_MAX_FILE_BYTES=26214400`
- `MONEYFLOW_ASR_LANGUAGE=vi`
- `MONEYFLOW_ASR_RETURN_SEGMENTS=false`
- `MONEYFLOW_ASR_ALLOWED_MIME_TYPES=audio/webm,audio/ogg,audio/wav,audio/mpeg,audio/mp4,audio/x-m4a`

Defaults keep ASR disabled with `MONEYFLOW_ASR_PROVIDER=none`.

## Provider Modes

- `none`: returns `ASR_NOT_CONFIGURED`; no ASR call.
- `mock`: deterministic backend mock for tests/dev.
- `external_http`: calls `POST {MONEYFLOW_ASR_SERVICE_URL}/asr/transcribe`.

If `external_http` has no service URL, backend returns `ASR_NOT_CONFIGURED`.

## Endpoint

`POST /api/workspaces/{workspaceId}/voice-sessions/{sessionId}/transcribe`

Multipart fields:

- `audio`: required in P4
- `language`: optional, default `vi`
- `returnSegments`: optional boolean
- `normalize`: optional boolean, default `true`
- `durationMs`: optional

P4 transcribes uploaded bytes directly and records audio metadata. It does not persist session audio yet, so `audioStatus` remains `NONE`.

## Success Response

```json
{
  "sessionId": "uuid",
  "sourceType": "AUDIO",
  "status": "TRANSCRIBED",
  "audioStatus": "NONE",
  "asrStatus": "SUCCEEDED",
  "commandStatus": "NOT_REQUESTED",
  "transcript": "Hôm nay tôi ăn sáng hết 35 nghìn",
  "normalizedTranscript": "Hôm nay tôi ăn sáng hết 35 nghìn",
  "asr": {
    "provider": "MOCK",
    "model": "mock",
    "language": "vi",
    "durationMs": 1000,
    "confidence": null,
    "warnings": [
      {
        "code": "ASR_MOCK_TRANSCRIPT",
        "message": "Mock ASR transcript was returned."
      }
    ]
  }
}
```

## Status Lifecycle

On request start:

- `status=TRANSCRIBING`
- `asrStatus=TRANSCRIBING`

On success:

- `status=TRANSCRIBED`
- `asrStatus=SUCCEEDED`
- transcript fields populated
- `commandStatus=NOT_REQUESTED`

On empty transcript:

- `status=FAILED`
- `asrStatus=NO_SPEECH`
- transcript fields cleared
- `ASR_EMPTY_TRANSCRIPT`

On timeout:

- `status=FAILED`
- `asrStatus=TIMEOUT`
- `ASR_SERVICE_TIMEOUT`

On unavailable/failure:

- `status=FAILED`
- `asrStatus=FAILED`
- `ASR_SERVICE_UNAVAILABLE` or `ASR_TRANSCRIBE_FAILED`

On disabled:

- `asrStatus=NOT_REQUESTED`
- `ASR_NOT_CONFIGURED`

## Validation

Backend validates before ASR call:

- file exists
- file size > 0
- file size <= max bytes
- MIME type allowlist
- optional `durationMs` min/max

Backend does not inspect WebM duration without `durationMs` and does not add ffmpeg or heavy audio libraries.

## Transcribe Then Interpret

1. Create `AUDIO` session.
2. Call `/transcribe` with audio.
3. Inspect/edit transcript if needed.
4. Call existing `/interpret`.
5. Review persisted draft snapshots.

Transcribe never interprets automatically. Interpret never posts transactions. Confirmation remains a later phase.

## Local Run With ASR Service

ASR service mock mode:

```powershell
cd D:\MindMirror\MoneyFlow\moneyflow-asr-service
.\.venv\Scripts\Activate.ps1
$env:MONEYFLOW_ASR_MODE="mock"
uvicorn app.main:app --host 0.0.0.0 --port 8092
```

Backend:

```powershell
$env:MONEYFLOW_ASR_PROVIDER="external_http"
$env:MONEYFLOW_ASR_SERVICE_URL="http://localhost:8092"
```

Real PhoWhisper mode uses the same backend config. The ASR service owns model download/load/performance.

## Runtime Diagnostics

`/api/me/runtime-diagnostics` includes safe ASR metadata:

- provider
- enabled/configured
- service URL configured flag only
- timeout/language/size/duration limits

It does not expose the service URL, headers, audio filename, transcript, or secrets.

## Known Limits

- Frontend is not wired yet.
- Session audio is not stored in P4.
- Backend does not inspect WebM duration without `durationMs`.
- Real model latency depends on `moneyflow-asr-service`.
- Confidence can be null.
- Confirm executor/linking remains future work.
