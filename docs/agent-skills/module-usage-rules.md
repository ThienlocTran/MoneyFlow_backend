# Module Usage Rules

## Skill / Playbook Usage Rule

Every module prompt should include:

```text
SKILL / PLAYBOOK USAGE RULE

Before editing:
1. Read AGENTS.md / project instructions if present.
2. Read docs/agent-skills/README.md.
3. Read docs/agent-skills/registry.md.
4. Read the relevant module playbook:
   - frontend-ui-playbook.md for UI/UX
   - backend-domain-playbook.md for backend/API/domain
   - security-tooling-playbook.md for security/CI/tools
5. Do repo reconnaissance before editing.
6. Do not install external tools unless explicitly approved.
7. Final report must include:
   - Skills/playbooks checked
   - Skills/playbooks applied
   - External links referenced
   - Tools intentionally not installed
   - Runtime dependency changes
```

## Direct Dev Mode

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

## Parallel Module Mode

```text
IMPORTANT PARALLEL MODULE RULE

One module = one branch + one worktree.

Before editing:
1. Start from dev.
2. Confirm tracked files are clean.
3. Pull dev with --ff-only.
4. Create the module branch and worktree.
5. Work only inside that module worktree.

Before reporting:
1. Validate on the module branch.
2. Commit only scoped files.
3. Return to the main dev working directory.
4. Pull latest dev with --ff-only.
5. Merge the module branch into dev with --no-ff.
6. Validate dev again.
7. Push dev.
8. Report once after dev push.

Stop on dirty tracked files, validation failure, merge conflict, or unexpected overlapping edits.
Do not auto-stash.
Do not force push.
Do not edit unrelated modules.
```
