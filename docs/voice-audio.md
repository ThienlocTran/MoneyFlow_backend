# Voice Audio Upload

Voice audio upload is mediated by the backend. The frontend sends recorded audio to `POST /api/voice-records/{voiceRecordId}/audio` after the voice transaction is confirmed.

Storage is disabled by default until a real provider is configured. When storage is disabled, upload and playback return `STORAGE_NOT_CONFIGURED`; the transaction and transcript remain saved. If a provider fails during upload, the voice record is marked `STORAGE_FAILED` and the linked transaction remains saved.

Provider secrets must stay on the backend. Do not expose Cloudinary, S3, storage keys, or signed playback URLs in logs.

Configuration:

```env
MONEYFLOW_AUDIO_STORAGE_PROVIDER=disabled
MONEYFLOW_AUDIO_FOLDER=moneyflow/voice
MONEYFLOW_AUDIO_MAX_BYTES=10485760
MONEYFLOW_AUDIO_ALLOWED_TYPES=audio/webm,audio/mp4,audio/mpeg,audio/wav
MONEYFLOW_CLOUDINARY_CLOUD_NAME=
MONEYFLOW_CLOUDINARY_API_KEY=
MONEYFLOW_CLOUDINARY_API_SECRET=
VOICE_AUDIO_RETENTION_DAYS=30
```

Use `MONEYFLOW_AUDIO_STORAGE_PROVIDER=cloudinary` to enable Cloudinary. Missing Cloudinary env values fail startup clearly so the app does not pretend audio storage is available.

Endpoints:

```http
POST /api/voice-records/{voiceRecordId}/audio
GET /api/voice-records/{voiceRecordId}/playback
GET /api/voice-records/{voiceRecordId}/playback-url
DELETE /api/voice-records/{voiceRecordId}/audio
```

Playback uses backend authorization first, then returns a short-lived provider URL when audio exists. Upload/delete require a workspace OWNER or EDITOR. Playback requires active workspace membership.

Retention/delete removes only stored audio objects and audio metadata. Voice transcripts and linked transactions remain in the database for transaction traceability.
