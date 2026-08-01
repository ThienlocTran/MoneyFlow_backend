# Voice Command Contract

## Canonical endpoint

Frontend command center should call:

`POST /api/workspaces/{workspaceId}/voice-command/interpret`

This endpoint routes one transcript into one of these modes:

- `READ_ONLY_QUERY`: answer-only finance question. No voice draft. No transaction.
- `TRANSACTION_REVIEW`: one ledger draft.
- `INCOME_FACT_REVIEW`: income fact draft, no guessed wallet, no wallet balance effect.
- `WALLET_SNAPSHOT_REVIEW`: wallet balance evidence draft, not income or expense.
- `DEBT_DRAFT`: debt movement draft, not normal income or expense.
- `MULTI_DRAFT_REVIEW`: multiple draft rows in `drafts[]`.
- `NEEDS_CLARIFICATION`: unsafe mixed query/mutation or unclear command. No save.
- `UNSUPPORTED`: unsupported single command. No transaction.

`POST /api/workspaces/{workspaceId}/voice-review/parse` remains the legacy review endpoint. It can create/reuse a `voiceRecordId` for review and audio evidence, but it is still parse-only. It must not post transactions.

## Response shape

Command responses expose:

- `mode`
- `status`
- `commandType`
- `text`
- `voiceRecordId`
- `review`
- `query`
- `drafts[]`
- `warnings[]`

Frontend should render `drafts[]` when present. For single-draft modes, `review.candidate` is the primary candidate and `review.drafts[0]` is the editable row.

## Draft types

Known draft types include:

- `EXPENSE`
- `INCOME`
- `TRANSFER`
- `INCOME_FACT`
- `WALLET_SNAPSHOT`
- `SAVINGS_ALLOCATION`
- `SINKING_FUND_CONTRIBUTION`
- `EMERGENCY_FUND_CONTRIBUTION`
- `LOAN_DISBURSEMENT`
- `LOAN_COLLECTION`
- `BORROWING_RECEIPT`
- `BORROWING_REPAYMENT`
- `DEBT`
- `UNKNOWN`

Savings, funds, wallet snapshots, and debt drafts are review-only until their module-specific confirm contracts exist.

## Multi-draft behavior

When multiple money intents are detected, backend returns `MULTI_DRAFT_REVIEW` with separate `drafts[]` rows. Example:

`Hôm nay đã kiếm được 800 Tôi ăn hết 50 Cái đổ xăng hết 65.000 Ta gửi tiết kiệm hết 35`

Expected draft types:

- `INCOME_FACT`, amount `800000`, `walletRequired=false`, `affectsWalletBalance=false`
- `EXPENSE`, amount `50000`
- `EXPENSE`, amount `65000`, fuel category when matched
- `SAVINGS_ALLOCATION`, amount `35000`, `countsAsExpense=false`

Stable warning:

- `VOICE_MULTI_INTENT_DETECTED`: multiple rows found; user should review each row before save.

If backend cannot split safely, it should return `NEEDS_CLARIFICATION` with `VOICE_MULTI_INTENT_LOW_CONFIDENCE`. It must not return one misleading `UNKNOWN` draft using only the first amount.

## Audio lifecycle

Voice transcript and voice audio are separate evidence.

Parse/interpret:

- may create or reuse `voiceRecordId`
- must not post transactions
- preserve provided audio metadata when a voice record already exists

Confirm:

- links created transaction to `voiceRecordId`
- is idempotent for the same voice record or draft source reference
- must not fake audio availability

Transaction list/detail should expose:

- `voiceRecordId`
- `voiceTranscript`
- `voiceAudioStatus`
- `voicePlaybackAvailable` or `playbackAvailable`
- `voiceAudioAvailable` or `hasVoiceAudio`

`playbackAvailable=true` only when stored audio metadata exists and backend can serve it. Transcript-only, failed, and deleted audio states remain distinguishable through `voiceAudioStatus`.

Frontend should use `voicePlaybackAvailable`/`playbackAvailable` plus `voiceAudioStatus` for the play button. It should not infer audio availability from transcript alone.
