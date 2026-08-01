# Agent Skill And Tooling Registry

Runtime dependency is `No` for every item in this module.

| Name | URL | Category | Status | Use now? | Runtime dependency? | When to use | Do not do | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Taste Skill | https://github.com/Leonxlnx/taste-skill | Design / frontend agent skill | Playbook reference | Yes, for UI work | No | Read before UI redesign, page polish, visual hierarchy, component design | Do not blindly copy external aesthetics | Apply taste principles through MoneyFlow design system |
| Taste Skill Docs | https://www.tasteskill.dev/ | Design reference | Design reference | Yes, for UI work | No | Reference for anti-generic UI guidance | Do not treat docs as dependency | Pair with local `frontend-ui-playbook.md` |
| GSAP Skills | https://github.com/greensock/gsap-skills.git | Animation agent skill | Playbook reference | Yes, only when animation is touched | No | Read before GSAP animation, page transitions, drag/drop motion, onboarding motion | Do not add GSAP unless explicitly approved or already installed | Respect reduced motion and cleanup on unmount |
| OriginKit | https://www.originkit.dev/ | UI interaction reference | Design reference | Reference now | No | Reference for category jar UI, micro-interactions, cards, drag/drop feel | Do not copy blindly or install without approval | Inspiration only |
| Pretext | https://github.com/chenglou/pretext.git | Prompt/spec workflow reference | Research only | No | No | Research better agent instructions when a concrete gap exists | Do not install until use case is clear | Keep outside runtime |
| Repo Recon Skill | https://github.com/marpla78/repo-recon-skill.git | Repo reconnaissance agent skill | Playbook reference | Yes, for large or unclear changes | No | Read before large repo changes, feature integration, refactors | Do not replace exact `rg` search or current file inspection | Agents must inspect current contracts before coding |
| AgentFlow | https://github.com/lupantech/AgentFlow.git | Agent workflow/orchestration reference | Research only | No | No | Research multi-agent module planning | Do not add runtime dependency | Needs safeguards before adoption |
| Chatwoot | https://github.com/chatwoot/chatwoot.git | Product/support tool | Research only | No | No | Research future support/help center/chat support | Do not install into MoneyFlow now | Customer data/privacy review required later |
| img2threejs | https://github.com/img2threejs/img2threejs.git | Visual experiment reference | Research only | No | No | Research future Flo mascot or 3D visuals | Do not install now | Not core finance functionality |
| TruffleHog | https://github.com/trufflesecurity/trufflehog.git | Security / DevSecOps | CI candidate | Later, if approved | No | Candidate for secret scanning in CI or release checklist | Do not add as app runtime dependency | Redact findings in reports |
| Dockerscan | https://github.com/cr0hn/dockerscan.git | Security research | Not for runtime | No | No | Research own Docker image scanning only | Do not scan third-party systems | Permission required before use |
| GhidraGPT | https://github.com/weirdmachine64/GhidraGPT.git | Reverse-engineering research | Not for runtime | No | No | Security research only | Do not install into MoneyFlow app/runtime | Not relevant to normal product delivery |
| Raccoon | https://github.com/evyatarmeged/Raccoon.git | Security research | Not for runtime | No | No | Research only after exact use case is verified | Do not install without explicit approval | Higher misuse risk |
| Jenkins | https://www.jenkins.io/ | CI/CD | Research only | No | No | Consider only if GitHub Actions or current validation is insufficient | Do not add Jenkins config now | Prefer lightweight CI first |
