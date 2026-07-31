# Frontend UI Playbook

## Product Principle

Tinh gọn, đơn giản, thực tiễn, nhưng có chất riêng.

MoneyFlow UI should feel warm, practical, trustworthy, and calm. It should explain financial state without clutter.

## Design System

- Cream: `#FFF9E8`
- Deep Blue: `#172554`
- Soft Gold: `#D4A72C`
- Slate: `#334155`
- Body font: Be Vietnam Pro
- Headings and numbers: Manrope
- Money numbers should be tabular where possible.

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

## Taste Skill Usage

Read Taste Skill before major UI design or polish tasks if available. Apply intentional hierarchy, spacing, typography, and non-generic composition. Do not blindly copy external aesthetics; adapt ideas to the MoneyFlow design system.

## GSAP Usage

Use GSAP only when motion improves clarity. Respect `prefers-reduced-motion`, kill tweens and listeners on unmount, and avoid motion that delays financial tasks. Do not add GSAP if it is not already installed unless the module explicitly approves it.

## OriginKit Usage

Use OriginKit as inspiration for interactions and effects. Do not copy or install without approval.
