# DevSecOps and Agent Governance

## 1. Purpose

This document defines how MoneyFlow decides on tooling, agent skills, security scanning, and CI/CD. The goal is practical governance: use tools that protect delivery, defer experiments until they have clear value, and keep security or reverse-engineering tools out of application runtime.

MoneyFlow development principle:

- Tinh gọn, đơn giản, thực tiễn, nhưng có chất riêng.
- Correctness and traceability are more important than flashy automation.
- Security tools protect our repo and deployment; they are not app features.
- Agent work must be scoped, validated, and merged safely.

## 2. Current Repo Workflow

MoneyFlow uses `dev` as the active integration branch.

For parallel module work:

- Create one dedicated branch and worktree per module.
- Work only inside that module worktree.
- Do not merge to `dev` from the module agent.
- Do not force push.
- Stop if tracked files are dirty before starting.
- Stop on merge conflicts or validation failures.
- Commit and push only after validation passes.

For direct dev mode:

- Use only for solo-owner local development or small fixes.
- Work directly on `dev`.
- Do not create a branch or worktree.
- Pull with `--ff-only` before editing.
- Stop if tracked files are dirty.

Before commit or push, run the narrowest validation that covers the change. For docs-only work, run `git diff --check` and the mojibake scan when Vietnamese text is touched.

## 3. Agent Role Map

| Agent | Primary scope | Boundaries |
| --- | --- | --- |
| Codex | Backend, API, domain logic, tests, docs, security review | Avoid frontend ownership unless explicitly assigned |
| Antigravity | Frontend, UI, UX, layout, i18n, PWA | Avoid backend ownership unless explicitly assigned |
| Module agent | One requested module | One module equals one branch and one worktree |

Parallel agents must not edit overlapping files. If overlap is unavoidable, stop and coordinate before continuing.

## 4. Tool Decision Matrix

| Tool | Purpose | Decision | Repo impact | Security risk | Recommended next action |
| --- | --- | --- | --- | --- | --- |
| pretext | Prompt or text workflow helper | Avoid for now | Unknown until evaluated | Unknown secrets/licensing risk | Do not add until a concrete workflow gap exists |
| gsap-skills | Animation guidance and correctness patterns | Use now | Documentation/process only | Low | Use as guidance for frontend animation work; no runtime dependency unless already needed |
| Chatwoot | Support/helpdesk | Research later | Would add service integration and support workflow | Medium, customer data and keys | Evaluate after support volume justifies it |
| img2threejs | 3D or mascot experiment tooling | Research later | Prototype assets only | Low to medium, asset provenance | Keep in experiments; do not add to app runtime now |
| Raccoon | Security or reconnaissance-oriented tool | Security research only, not app runtime | None | High if misused or unclear scope | Do not install in runtime; require written research scope |
| repo-recon-skill | Codebase understanding and search | Use now | Agent workflow only | Low if read-only | Use for scoped repo discovery when exact search is insufficient |
| AgentFlow | Agent orchestration experiments | Research later | Possible workflow config later | Medium, automation can change repo state | Prototype outside runtime; require clear safeguards before adoption |
| TruffleHog | Secret scanning | Use now | CI/tooling only later | Low; may expose findings in logs if mishandled | Add TruffleHog, Gitleaks, or equivalent CI gate in a later DevSecOps task |
| dockerscan | Container security research | Security research only, not app runtime | None now | Medium to high, depends on targets and permissions | Use only against owned images with permission |
| GhidraGPT | Reverse engineering assistance | Security research only, not app runtime | None | High, offensive/reverse-engineering misuse | Keep out of MoneyFlow runtime and CI |
| Jenkins | CI/CD orchestration | Research later | High operational overhead | Medium, credentials and build agents | Prefer GitHub Actions first; revisit only if Actions is insufficient |
| GitHub Actions | Lightweight CI/CD | Use now before Jenkins | CI config later | Medium, secrets in CI | Add minimal validation workflow in a separate task |

Avoid tools that add complexity without immediate product value, require unclear secrets or licensing, or overlap the current workflow without measurable benefit.

## 5. Secret Scanning Plan

- Add a TruffleHog, Gitleaks, or equivalent scan later.
- Run secret scan before releases.
- Add a CI gate so suspected secrets block merges.
- Never print secrets in logs.
- Never commit API keys, database URLs, tokens, or provider credentials.
- Keep Cloudinary and OpenAI keys in environment variables only.
- Redact scanner output when reporting findings so real secrets are not copied into issues, PRs, chat, or logs.

## 6. CI/CD Plan

Current lightweight validation:

- Backend docs-only changes: `git diff --check`.
- Backend code changes: narrow Maven tests for touched area, then `git diff --check`.
- Frontend changes: type-check, mojibake scan, `git diff --check`, and build when views or components changed.

Recommended order:

1. Add simple GitHub Actions first.
2. Gate backend tests for backend changes.
3. Gate frontend type-check/build for frontend changes.
4. Gate mojibake scan for touched text and UI files.
5. Gate `git diff --check`.
6. Add secret scanning before release.
7. Consider Jenkins only if GitHub Actions cannot cover required workflows.

Do not add CI config in this document-only task.

## 7. Security Boundaries

- Do not scan third-party systems, images, repositories, or services without explicit permission.
- Do not include offensive, reverse-engineering, or reconnaissance tools in app runtime.
- Do not store API keys, tokens, passwords, or private URLs in the repo.
- Use environment variables for Cloudinary, OpenAI, database, and deployment secrets.
- Do not paste raw secrets into logs, issues, PRs, docs, or agent prompts.
- Security tooling belongs in local checks, CI, or controlled research workspaces, not product features.

## 8. Agent Prompt Governance

### Direct Dev Mode

Use this mode only for solo-owner local development.

```text
IMPORTANT WORKFLOW RULE

Work directly on dev.
Do NOT create worktree.
Do NOT create a new branch.
This is a solo-owner local development session.

Before editing:

git checkout dev
git status -sb
git pull --ff-only origin dev

If tracked files are dirty, STOP and report.
Do not auto-stash.
Do not force push.
Do not edit unrelated modules.
Commit and push only after validation passes.
```

### Parallel Module Mode

Use this mode for concurrent module work.

- Create a dedicated branch and worktree.
- Work only in that worktree.
- Do not merge to `dev` from the module agent.
- Merge only after validation and human review.
- Stop on dirty tracked files, merge conflicts, or failing validation.
- Do not edit unrelated modules.

## 9. Rollout Checklist

| Phase | Scope | Exit criteria |
| --- | --- | --- |
| Phase 1 | Docs | Governance document merged |
| Phase 2 | Secret scan CI | TruffleHog, Gitleaks, or equivalent runs safely in CI |
| Phase 3 | Frontend/backend CI | Backend tests, frontend type-check/build, mojibake scan, and diff check covered |
| Phase 4 | Optional Jenkins | Only if GitHub Actions is insufficient |
| Phase 5 | Support/helpdesk experiments | Chatwoot or alternative evaluated with data/privacy review |
