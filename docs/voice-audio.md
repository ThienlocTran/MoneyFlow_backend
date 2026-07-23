# Voice Audio Upload

Voice audio upload is mediated by the backend. The frontend sends recorded audio to `POST /api/voice-records/{voiceRecordId}/audio` after the voice transaction is confirmed.

Storage is disabled by default until a real provider is configured. When storage is disabled, upload and playback return `STORAGE_NOT_CONFIGURED`; the transaction and transcript remain saved. If a provider fails during upload, the voice record is marked `STORAGE_FAILED` and the linked transaction remains saved.

Provider secrets must stay on the backend. Do not expose Cloudinary, S3, storage keys, or signed playback URLs in logs.

Configuration:

```env
VOICE_AUDIO_STORAGE_ENABLED=false
VOICE_AUDIO_STORAGE_PROVIDER=disabled
VOICE_AUDIO_S3_BUCKET=moneyflow-voice-audio
VOICE_AUDIO_S3_REGION=auto
VOICE_AUDIO_S3_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
VOICE_AUDIO_S3_ACCESS_KEY=
VOICE_AUDIO_S3_SECRET_KEY=
VOICE_AUDIO_S3_PATH_STYLE_ACCESS=true
VOICE_AUDIO_MAX_BYTES=10485760
VOICE_AUDIO_ALLOWED_MIME_TYPES=audio/webm,audio/mp4,audio/mpeg,audio/wav
VOICE_AUDIO_RETENTION_DAYS=30
```

Use `VOICE_AUDIO_STORAGE_ENABLED=true` and `VOICE_AUDIO_STORAGE_PROVIDER=r2` or `s3` to enable Cloudflare R2 through the S3-compatible API. Missing S3/R2 env values fail startup clearly so the app does not pretend audio storage is available.

Endpoints:

```http
POST /api/voice-records/{voiceRecordId}/audio
GET /api/voice-records/{voiceRecordId}/audio
GET /api/voice-records/{voiceRecordId}/playback
GET /api/voice-records/{voiceRecordId}/playback-url
DELETE /api/voice-records/{voiceRecordId}/audio
GET /api/workspaces/{workspaceId}/voice/audio-storage/status
```

Playback uses backend authorization first, then returns a short-lived provider URL when audio exists. Upload/delete require a workspace OWNER or EDITOR. Playback requires active workspace membership.

Retention/delete removes only stored audio objects and audio metadata. Voice transcripts and linked transactions remain in the database for transaction traceability.
