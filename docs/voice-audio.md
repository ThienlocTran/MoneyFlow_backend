# Voice Audio Upload

Voice audio upload is mediated by the backend. The frontend sends recorded audio to `POST /api/voice-records/{voiceRecordId}/audio` after the voice transaction is confirmed.

Storage is disabled by default until a real provider is configured. When storage is disabled, upload and playback return `STORAGE_NOT_CONFIGURED`; the transaction and transcript remain saved.

Provider secrets must stay on the backend. Do not expose Cloudinary, S3, or other storage credentials to the frontend.

Configuration placeholders for a future provider:

```env
VOICE_AUDIO_STORAGE_ENABLED=false
VOICE_AUDIO_MAX_SIZE_MB=10
VOICE_AUDIO_RETENTION_DAYS=30
# CLOUDINARY_CLOUD_NAME=
# CLOUDINARY_API_KEY=
# CLOUDINARY_API_SECRET=
# CLOUDINARY_VOICE_FOLDER=moneyflow/voice
```

Playback should use `GET /api/voice-records/{voiceRecordId}/playback-url`, which returns a backend-authorized playback URL when audio exists and the provider supports it.

Retention deletes only stored audio objects and audio metadata. Voice transcripts remain in the database for transaction traceability.
