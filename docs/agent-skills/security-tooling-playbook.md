# Security Tooling Playbook

## Security Principles

- Do not expose secrets in logs, docs, PRs, issues, or prompts.
- Do not add offensive tools into app runtime.
- Do not scan third-party targets without permission.
- Use security tools to protect the MoneyFlow repo and deployments only.
- Redact scanner findings before reporting.

## Tool Guidance

- TruffleHog: candidate for secret scanning in CI or release checklist.
- Dockerscan: research-only for owned Docker images.
- GhidraGPT: reverse-engineering research only; not app runtime.
- Raccoon: research-only until use case is verified.
- Jenkins: CI server candidate later, not now.
- Chatwoot: support/helpdesk platform research only.
- img2threejs: visual/mascot research only.

## Recommended Future Phases

1. Keep this docs registry current.
2. Add a secret scan checklist.
3. Add GitHub Actions or equivalent frontend/backend validation.
4. Add a TruffleHog/Gitleaks-like CI gate if approved.
5. Consider Jenkins only if lightweight CI is insufficient.

## External Tool Safety

Do not install, clone, vendor, run, or configure external security tooling unless a module explicitly approves the exact action and scope.
