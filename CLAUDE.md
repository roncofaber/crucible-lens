# Claude Code Adapter

@AGENTS.md

The shared project instructions and workflows are canonical in `AGENTS.md` and `agent-skills/`. Claude-compatible skill adapters remain under `.claude/skills/`.

`.claude/settings.json` contains Claude Code permissions and invokes `.claude/hooks/pre-commit-check.sh` before Bash-based commits. That adapter delegates the actual compile and test gate to `scripts/verify-change.sh`.
