# Prompt Blocks

## 1. Universal Skill Usage Block

```text
================================================================================
SKILL / PLAYBOOK USAGE RULE
================================================================================

Before editing:
1. Read AGENTS.md / project instructions if present.
2. Read docs/agent-skills/README.md.
3. Read docs/agent-skills/registry.md.
4. Read the relevant playbook for this module.
5. Do repo reconnaissance before editing.
6. Do not install external tools or dependencies unless explicitly approved.
7. If a referenced skill/tool is unavailable, continue with project conventions and mention it in the final report.

Final report must include:
- Skills/playbooks checked
- Skills/playbooks applied
- External links referenced
- Tools intentionally not installed
- Runtime dependency changes
```

## 2. Frontend UI Module Skill Block

```text
Read docs/agent-skills/frontend-ui-playbook.md before editing.
If available, read Taste Skill before major UI design/polish work.
Use OriginKit only as inspiration.
Do not add GSAP or animation dependencies unless explicitly approved.
No fake runtime financial data.
Preserve accessibility, mobile 360px, and real Vietnamese UTF-8.
```

## 3. Backend Domain Module Skill Block

```text
Read docs/agent-skills/backend-domain-playbook.md before editing.
Use exact rg search for routes, DTOs, services, repositories, and error codes.
Preserve MoneyFlow ledger rules and API compatibility.
Add focused tests for changed domain behavior.
Do not change unrelated financial modules.
```

## 4. Security / Tooling Module Skill Block

```text
Read docs/agent-skills/security-tooling-playbook.md before editing.
Security tools protect MoneyFlow repo/deploy only.
Do not scan third-party targets.
Do not expose secrets in logs or reports.
Do not add offensive or research tools to app runtime.
```

## 5. Animation / GSAP Block

```text
If animation is touched, read the GSAP Skills reference if available.
Use motion only when it improves clarity.
Respect prefers-reduced-motion.
Kill tweens, timelines, listeners, and observers on unmount.
Do not delay financial workflows with decorative motion.
Do not add GSAP unless already installed or explicitly approved.
```

## 6. External Tool Installation Safety Block

```text
External tools from docs/agent-skills/registry.md are references by default.
Do not install packages.
Do not clone or vendor entire repositories.
Do not add git submodules.
Do not add CI workflows or runtime config unless this module explicitly requests it.
If a tool seems useful, document the recommendation and ask for a separate approval task.
```
