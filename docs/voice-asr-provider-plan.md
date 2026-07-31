# Voice ASR Provider Plan

Status: Phase 0 docs spike.

MoneyFlow should use pretrained speech recognition for MVP. ASR produces transcript only. Transaction creation remains behind MoneyFlow parsing, review, user confirmation, and existing ledger commit paths.

## 1. Why Not Train ASR Now

- Training ASR is expensive and unnecessary for MVP.
- Vietnamese finance voice input needs product evidence first: real corrections, common phrases, noisy samples, accent coverage, and user review outcomes.
- The model worth training later is more likely the MoneyFlow finance parser/correction model, not speech recognition itself.
- Pretrained ASR providers already solve general speech-to-text well enough to validate the MoneyFlow voice workflow.
- Owning ASR training now would add data labeling, GPU infrastructure, model evaluation, privacy review, deployment, monitoring, and fallback complexity before product fit is proven.

## 2. Recommended Architecture

```text
Browser audio recording
-> Backend stores audio evidence if enabled
-> ASR provider returns transcript
-> MoneyFlow parser creates draft(s)
-> Voice Review lets user confirm
-> Ledger changes only after confirmation
```

Responsibilities:

- Browser records audio and sends it to the backend.
- Backend optionally stores audio evidence according to existing audio retention/storage policy.
- ASR provider transcribes audio and returns text plus optional metadata.
- MoneyFlow parser converts transcript into one or more review drafts.
- Voice Review displays parsed candidates, missing fields, warnings, and confidence.
- Existing confirm endpoints create transactions only after explicit user confirmation.

ASR must never create, modify, or confirm transactions directly.

## 3. Provider Comparison

| Provider | Best use case | Pros | Cons | Deployment complexity | Expected cost/infra burden | Privacy considerations |
| --- | --- | --- | --- | --- | --- | --- |
| OpenAI transcription API | MVP cloud transcription | Fastest integration; strong general ASR; no model hosting; simple scale-up path; supports Vietnamese language hints and vocabulary prompts | Cloud dependency; per-use cost; request latency depends on network/provider | Low: backend HTTP client plus env config | Usage-based API cost; minimal backend infra | Audio leaves MoneyFlow infrastructure; privacy copy must disclose cloud transcription provider |
| PhoWhisper self-host | Vietnamese optimization after MVP data exists | Vietnamese-focused Whisper variants; more control over model choice; can tune runtime around MoneyFlow phrases | Requires service hosting, GPU/CPU sizing, model ops, monitoring; quality must be benchmarked against real MoneyFlow audio | Medium to high: Python FastAPI service, model runtime, deployment, health checks | GPU preferred for latency; CPU possible but slower; ongoing infra cost | Audio can stay inside controlled infrastructure if hosted privately |
| faster-whisper/CTranslate2 | Faster Whisper inference for self-host | Efficient inference; good CPU/GPU performance; broad Whisper model support; practical production runtime | Still needs model hosting; Vietnamese quality depends on selected model; operational ownership remains | Medium: service wrapper plus model/runtime image | Lower inference cost than naive Whisper; still requires compute capacity planning | Audio stays within chosen host boundary; retention/logging must be controlled |
| Sherpa-ONNX Vietnamese Zipformer | Future offline/mobile prototype | Offline-capable; ONNX runtime; good fit for mobile or edge experiments; no cloud transcription dependency | Not MVP unless prototype proves quality; integration/testing effort; model quality must be validated for finance phrases | High for product integration; medium for isolated prototype | Lower cloud cost; device CPU/battery/storage tradeoffs | Strong privacy if fully local; local-device model artifacts and audio permissions still need review |
| Vosk | Lower-priority offline fallback | Mature offline ASR toolkit; simple local service possibilities; low cloud dependency | Vietnamese quality may lag newer models; less attractive for finance phrase accuracy; fallback only | Medium | Low cloud cost; local/server compute required | Good offline privacy if logs and local storage are constrained |

Recommended MVP provider: OpenAI transcription API. It minimizes non-product work while MoneyFlow learns from correction data.

## 4. Proposed Java Interface

Sketch only. Do not implement in this docs phase.

```java
public interface AsrProvider {
    AsrTranscriptionResult transcribe(AsrTranscriptionRequest request);

    AsrProviderType providerType();
}

public record AsrTranscriptionRequest(
        InputStream audio,
        byte[] audioBytes,
        String contentType,
        String filename,
        String languageHint,
        String promptContextHint,
        Duration timeout
) {
}

public record AsrTranscriptionResult(
        String transcript,
        AsrProviderType provider,
        String model,
        Optional<Double> confidence,
        List<AsrSegment> segments,
        Optional<Duration> duration,
        List<String> warnings
) {
}

public record AsrSegment(
        Duration start,
        Duration end,
        String text,
        Optional<Double> confidence
) {
}

public enum AsrProviderType {
    OPENAI,
    PHOWHISPER,
    FASTER_WHISPER,
    SHERPA_ONNX,
    VOSK,
    DISABLED
}
```

Field notes:

- `audio` or `audioBytes`: backend-owned audio input.
- `contentType`: original MIME type such as `audio/webm`.
- `filename`: optional provider filename.
- `languageHint`: default `vi`.
- `promptContextHint`: MoneyFlow vocabulary such as ví tiền mặt, chuyển khoản, ăn uống, lương, nợ, tiết kiệm.
- `transcript`: plain ASR text output.
- `provider`: selected provider.
- `model`: actual model/provider model used.
- `confidence`: optional because provider confidence support varies.
- `segments`: optional time-aligned transcript pieces.
- `duration`: optional audio duration or provider-reported duration.
- `warnings`: non-fatal provider/runtime warnings.

## 5. Env Config Proposal

```env
MONEYFLOW_ASR_PROVIDER=openai
MONEYFLOW_ASR_LANGUAGE=vi
MONEYFLOW_ASR_MODEL=<provider-model-name>
MONEYFLOW_ASR_TIMEOUT_SECONDS=30
MONEYFLOW_OPENAI_API_KEY=<openai-api-key>
MONEYFLOW_OPENAI_BASE_URL=https://api.openai.com/v1
MONEYFLOW_ASR_SERVICE_URL=<self-hosted-asr-service-url>
```

Provider selection:

- `MONEYFLOW_ASR_PROVIDER=disabled`: no transcription provider.
- `MONEYFLOW_ASR_PROVIDER=openai`: cloud MVP path.
- `MONEYFLOW_ASR_PROVIDER=phowhisper`, `faster-whisper`, `sherpa-onnx`, or `vosk`: future self-host/offline paths.

## 6. Security/Privacy Notes

- Never log `MONEYFLOW_OPENAI_API_KEY` or any provider secret.
- Never log raw signed URLs, provider object keys, or direct audio access URLs.
- Diagnostics should expose booleans only, such as provider configured, API key present, and service reachable.
- Audio may contain personal finance data, account nicknames, family names, debt details, income details, and spending habits.
- User-facing privacy copy should mention the transcription provider when a cloud ASR provider is used.
- Provider errors shown to users should be clear but should not expose secrets, signed URLs, headers, raw provider payloads, or infrastructure host details.

## 7. OpenAI MVP Rollout

- Use OpenAI transcription API as the initial provider behind `MONEYFLOW_ASR_PROVIDER=openai`.
- Backend calls the provider directly; frontend never receives provider credentials.
- Send `language=vi` by default.
- Include a MoneyFlow vocabulary prompt/context hint for Vietnamese finance terms, wallet names, category names, and common shorthand such as `35k`, `1.5tr`, `tiền mặt`, `MoMo`, `Cake`, `lương`, `nợ`.
- Convert provider failures into clear voice transcription errors.
- Preserve the existing draft-and-confirm flow: transcript feeds parser, parser creates drafts, user confirms, then ledger changes.
- Keep provider selection behind env config so self-host providers can be evaluated without changing the product contract.

## 8. PhoWhisper Self-Host Rollout

Shape:

- Python FastAPI service accepts multipart audio upload or internal object reference.
- Service returns normalized JSON:

```json
{
  "transcript": "ăn sáng 35k ví tiền mặt",
  "provider": "PHOWHISPER",
  "model": "vinai/PhoWhisper-large",
  "confidence": null,
  "segments": [],
  "durationSeconds": 4.2,
  "warnings": []
}
```

Model choices to benchmark:

- Smaller PhoWhisper model for lower latency and cheaper CPU/GPU usage.
- Larger PhoWhisper model for accuracy tests on noisy Vietnamese finance phrases.
- faster-whisper-compatible Whisper variants if runtime speed matters more than model branding.

Resource expectations:

- GPU strongly preferred for low-latency production transcription.
- CPU can work for background or low-volume usage but needs latency benchmarking.
- Service needs health checks, request size limits, timeouts, structured non-secret diagnostics, and audio retention controls.

## 9. Sherpa-ONNX/Offline Rollout

- Treat Sherpa-ONNX Vietnamese Zipformer as a future mobile/offline spike.
- Do not make it MVP unless a prototype passes MoneyFlow-specific accuracy, latency, battery, storage, and device compatibility checks.
- Best target is local transcript generation on supported mobile devices or an offline desktop/edge mode.
- The backend contract should remain provider-neutral so offline transcript can still enter the same parser/review/confirm flow.

## 10. Test Plan

- Golden audio samples for stable regression checks.
- Vietnamese finance phrases covering expenses, income, transfers, debt wording, savings wording, wallet names, and category names.
- Noisy microphone samples from normal phones and laptops.
- Regional accent samples across Vietnamese speakers.
- Short, long, interrupted, and multi-transaction utterances.
- Privacy scenarios: cloud provider disabled, missing API key, signed URL redaction, no raw audio URL in logs.
- Failure scenarios: provider timeout, invalid audio type, empty transcript, partial transcript, provider unavailable, malformed provider response.
- Review safety: ASR transcript alone must not create transactions; only confirmation can post ledger changes.

## 11. Rollout Plan

- Phase 0: docs/design spike only.
- Phase 1: OpenAI provider behind env flag, default language `vi`, provider errors surfaced as transcription failures, no direct ledger write.
- Phase 2: evaluate PhoWhisper self-host against collected MoneyFlow correction data and golden audio.
- Phase 3: prototype offline/mobile ASR with Sherpa-ONNX or another ONNX runtime path.

## Implementation Recommendation

Keep ASR as a narrow adapter layer. The domain boundary is transcript in, draft review out. Do not let provider-specific response formats leak into transaction parsing or ledger commit code.
