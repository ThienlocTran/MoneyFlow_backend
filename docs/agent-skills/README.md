# MoneyFlow Agent Skills

## Purpose

This folder is the canonical MoneyFlow agent skill and external tooling registry. It tells Codex, Antigravity, Kiro, and future module agents which playbooks or references to read before work begins.

These entries are instructions and references only. They are not MoneyFlow backend runtime dependencies.

## How Agents Should Use This Folder

Before editing a module:

1. Read `AGENTS.md` and project instructions.
2. Read this README.
3. Read `registry.md`.
4. Read the relevant playbook:
   - `frontend-ui-playbook.md` for UI/UX work.
   - `backend-domain-playbook.md` for backend/API/domain work.
   - `security-tooling-playbook.md` for security, CI, and external tools.
5. Do scoped repo reconnaissance before editing.
6. Report which playbooks and external references were checked or applied.

## What This Folder Is NOT

- Not app runtime code.
- Not a dependency manifest.
- Not approval to install packages, clone repositories, add submodules, or vendor third-party code.
- Not approval to run security scanners or external tools.
- Not a replacement for current source code, tests, or product guardrails.

## Quick Classification Table

| Classification | Meaning | Examples |
| --- | --- | --- |
| Use now / playbook | Read and apply as agent guidance when relevant | Taste Skill, GSAP Skills, Repo Recon Skill |
| Reference now | Use for inspiration only | OriginKit |
| Security/CI candidate | Candidate for later CI or release checks | TruffleHog |
| Security research only | Research only, never runtime | Dockerscan, GhidraGPT, Raccoon |
| Research later | Keep as future reference | Pretext, AgentFlow, Chatwoot, img2threejs, Jenkins |

## Future Maintenance Rule

Add a registry row before introducing any new external tool. Classify it first, document why it matters, and get explicit module approval before installing, vendoring, or adding runtime dependencies.
