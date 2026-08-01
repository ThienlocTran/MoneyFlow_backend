# Frontend UI Playbook

## Product Principle

Tinh gọn, đơn giản, thực tiễn, nhưng có chất riêng.

MoneyFlow UI should feel warm, practical, trustworthy, and calm. It should explain financial state without clutter.

## Design System

Canonical palette (use these exact values — do NOT substitute):

- Ivory background: `#FBF7EE` (`bg-app-new`)
- Surface/card: `#FFFFFF` (`bg-surface-primary`)
- Navy (primary text/button): `#10213A` (`text-primary-brand`, `btn-premium-primary`)
- Gold (accent): `#C89211` (`text-accent-brand`, `btn-premium-accent`)
- Gold soft (hover bg, surface-warm): `#FFF4D6` (`bg-surface-secondary`)
- Border: `#D8D3C4` (`border-new`)
- Slate (secondary text): `#334155`
- Muted text: `#64748B`
- Body font: Be Vietnam Pro
- Headings (h1-h6): Manrope — do NOT add `font-sans` to heading tags, it overrides Manrope
- Money numbers: use `tabular-nums` class, NOT `font-mono`

Common off-palette mistakes to avoid:
- `#172554` is Tailwind blue-950, NOT MoneyFlow navy — use `#10213A`
- `#D4A72C` is NOT MoneyFlow gold — use `#C89211`
- `#FFF9E8` / `#FFFDF7` are NOT MoneyFlow ivory — use `#FBF7EE` or `bg-surface-primary`
- `rgba(23,37,84,...)` is Tailwind raw — use `border-new` token
- `bg-rose-500` for errors — use `bg-mf-danger/10` and `text-mf-danger`
- `text-[#15803d]` for success — use `text-mf-success`

## UI Rules

- Avoid AI-purple gradients.
- Avoid generic glassmorphism everywhere.
- Avoid three identical bland cards when a richer layout better explains the flow.
- Avoid huge guidance blocks by default; use quiet `?` help.
- Preserve accessibility and mobile `360px`.
- Show no raw i18n keys.
- Add no fake runtime financial data.
- Empty backend data means empty state.
- Backend failure means error state.

## Taste Skill Usage (MANDATORY)

**Read Taste Skill BEFORE editing any `.vue` file with visual layout.** This is not optional.

Workflow:
1. Read `https://www.tasteskill.dev/` (or `docs/agent-skills/registry.md` for local reference)
2. Apply to MoneyFlow design system — do NOT copy external aesthetics literally
3. Check for: intentional hierarchy (one dominant element per section), spacing rhythm (4/8/16/24px), typography scale (not walls of `text-[10px]`), non-generic card composition

Known issues Taste Skill prevents:
- `select-none` on root divs → users cannot copy financial amounts
- `font-mono` on numbers → use `tabular-nums` (Manrope tabular, not Courier)
- `grid-cols-2` date/form inputs → use `grid-cols-1 sm:grid-cols-2` for 360px safety
- 3+ identical cards with no visual rhythm → add hierarchy or accent variation
- `font-sans` on `h1`/`h2` tags → removes Manrope, reverts to system font

## GSAP Usage

Use GSAP only when motion improves clarity. Respect `prefers-reduced-motion`, kill tweens and listeners on unmount, and avoid motion that delays financial tasks. Do not add GSAP if it is not already installed unless the module explicitly approves it.

## OriginKit Usage

Use OriginKit as inspiration for interactions and effects. Do not copy or install without approval.
