# Quick Entry

Quick Entry parses user text into a transaction draft. It never saves a transaction until the user confirms.

## Amount Formats

Supported examples:

- `35k` -> `35000`
- `35 nghìn` / `35 ngàn` -> `35000`
- `500k` -> `500000`
- `1tr` / `1 triệu` -> `1000000`
- `1.5tr` / `1,5tr` -> `1500000`

If the amount is missing, zero, negative, or ambiguous, the API returns a draft with `AMOUNT` in `missingFields`.

## Dates

If no date is provided, the parser uses today from the injected `Clock` and the workspace timezone.

Supported phrases:

- `hôm nay` -> today
- `hôm qua` -> today minus one day
- `ngày mai` -> planned transaction for tomorrow

## Intent

Expense intent examples: `ăn`, `uống`, `cafe`, `cà phê`, `xăng`, `gửi xe`, `mua`, `trả`, `đóng tiền`, `thanh toán`.

Income intent examples: `lương`, `thưởng`, `mẹ cho`, `ba cho`, `được cho`, `nhận`, `thu`, `hoàn tiền`.

Transfer examples: `chuyển`, `chuyển khoản`, `từ ví A sang ví B`, `MB sang MoMo`.

If text intent conflicts with the matched category type, the parser leaves category missing instead of forcing a wrong category.

## Category And Keyword Matching

The parser only loads active categories and active keywords from the current workspace.

Matching rules:

- normalize lowercase Vietnamese text safely
- prefer exact phrase keyword matches
- prefer longer keywords over shorter keywords
- prefer matching category type over non-matching type

If two active keywords tie for different categories, the draft returns `AMBIGUOUS_CATEGORY` and requires user confirmation.

## Wallet Suggestion

Wallet names and acronyms are matched from active wallets in the current workspace only.

When no wallet is typed:

- suggest the user's most recent active wallet for the same transaction type
- ignore historical Excel migration rows and rows that do not affect wallet balance
- fallback to the active default wallet
- leave wallet missing if no safe wallet exists

Transfers require source and destination wallets. If either side is unclear, the draft requires confirmation.

## No Auto Save

Quick text, voice, and quick action categories produce drafts first in the UI. Transaction creation happens only through confirm.

Source types are preserved:

- Quick text confirm -> `QUICK_TEXT`
- Voice confirm -> `VOICE`
- Manual transaction -> `MANUAL`
- Quick button confirm -> `QUICK_BUTTON` when the quick button endpoint is used
