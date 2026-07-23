# Voice Audio Upload

Voice audio upload is mediated by the backend. The frontend sends recorded audio to `POST /api/voice-records/{voiceRecordId}/audio` after the voice transaction is confirmed.

Storage is disabled only when the provider is `disabled`. When storage is disabled, upload returns `STORAGE_NOT_CONFIGURED`; the transaction and transcript remain saved. If a provider fails during upload, the voice record is marked `STORAGE_FAILED` and the linked transaction remains saved.

Provider secrets must stay on the backend. Do not expose Cloudinary public IDs, secure URLs, signed URLs, S3 keys, API keys, or secrets in frontend DTOs, CSV export, logs, or `TransactionResponse`.

Configuration:

```env
MONEYFLOW_AUDIO_STORAGE_PROVIDER=cloudinary
MONEYFLOW_AUDIO_FOLDER=moneyflow/prod/voice
MONEYFLOW_AUDIO_MAX_BYTES=10485760
MONEYFLOW_AUDIO_ALLOWED_TYPES=audio/webm,audio/mp4,audio/mpeg,audio/wav
VOICE_AUDIO_RETENTION_DAYS=30
MONEYFLOW_CLOUDINARY_CLOUD_NAME=<cloudinary-cloud-name>
MONEYFLOW_CLOUDINARY_API_KEY=<cloudinary-api-key>
MONEYFLOW_CLOUDINARY_API_SECRET=<cloudinary-api-secret>
```

Cloudinary audio uses the existing Cloudinary credential env names. Audio uploads use the Cloudinary video API with authenticated delivery and store assets under:

```text
moneyflow/prod/voice/YYYY-MM/YYYY-MM-DD/<random-audio-key>
```

The day folder is created by Cloudinary only when the first upload for that day succeeds.

S3/R2 remains optional through the S3-compatible API:

```env
MONEYFLOW_AUDIO_STORAGE_PROVIDER=r2
VOICE_AUDIO_S3_BUCKET=<bucket>
VOICE_AUDIO_S3_REGION=auto
VOICE_AUDIO_S3_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
VOICE_AUDIO_S3_ACCESS_KEY=<access-key>
VOICE_AUDIO_S3_SECRET_KEY=<secret-key>
VOICE_AUDIO_S3_PATH_STYLE_ACCESS=true
```

Endpoints:

```http
POST /api/voice-records/{voiceRecordId}/audio
GET /api/voice-records/{voiceRecordId}/audio
GET /api/voice-records/{voiceRecordId}/playback
GET /api/voice-records/{voiceRecordId}/playback-url
DELETE /api/voice-records/{voiceRecordId}/audio
GET /api/workspaces/{workspaceId}/voice/audio-storage
```

Playback uses backend authorization and streams bytes through `GET /api/voice-records/{voiceRecordId}/audio`. `playback` and `playback-url` return that backend URL, not a provider signed URL. Upload/delete require a workspace OWNER or EDITOR. Playback requires active workspace membership.

Retention/delete removes only stored audio objects and audio metadata. Voice transcripts and linked transactions remain in the database for transaction traceability.
