# Auto-Memory Protocol

This knowledge base automatically tracks feature implementations, bug fixes, patterns, and discoveries across all development sessions.

## Update Protocol

After every significant work unit, append to the relevant file in this directory:

### Features
**When to update**: After implementing a new feature or completing a functional work unit.
```
### [YYYY-MM-DD] — [Feature Name]
[Brief description of approach and key decisions]
**Files changed**: [List of modified files]
**Gotchas**: [Known issues or edge cases]
---
```

### Bug Fixes
**When to update**: After fixing a bug that took more than 15 minutes or affects reliability.
```
### [YYYY-MM-DD] — [Bug Title]
**Symptom**: [What failed]
**Root cause**: [Why it happened]
**Fix**: [Solution implemented]
**Prevention**: [How to avoid in future]
---
```

### Sessions
**When to update**: At the end of each work session.
```
### [YYYY-MM-DD] — [Session Title]
**Work done**: [What was accomplished]
**Discoveries**: [New learnings]
**Blockers**: [Unresolved issues]
**Next**: [Recommended next steps]
---
```

### Patterns
**When to add**: When a pattern is discovered and confirmed across 2+ scenarios.
```
### [Pattern Name]
**Context**: [When and where this applies]
**Pattern**: [Description of the pattern]
**Example files**: [Where this pattern is implemented]
---
```

## Commit Convention

All memory updates must be committed with:
```
docs(memory): update [filename] — [brief description]
```

Example:
```
docs(memory): update features.md — add UITreeReader implementation notes
docs(memory): update bugs.md — fix AccessibilityService crash on API 30
docs(memory): update sessions.md — week 1 sprint completion
```

## File Descriptions

| File | Purpose | Updated by |
|------|---------|-----------|
| `features.md` | Feature implementation notes, approaches, decisions | Developer after feature completion |
| `bugs.md` | Bug fixes, root causes, prevention strategies | Developer after debugging |
| `patterns.md` | Reusable coding patterns discovered in codebase | Developer when pattern confirmed 2+ times |
| `compatibility.md` | Device quirks, API level issues, OEM workarounds | Developer when discovering OEM issues |
| `performance.md` | Benchmark results, optimization discoveries, latency data | Developer after perf testing |
| `dependencies.md` | Upgrade paths, known issues, version pins | Developer when updating dependencies |
| `tech_radar.md` | Latest updates on project-relevant technologies | Daily startup agent daily |

## Related Files

- `../changelog/sessions.md` — Session summaries (append-only)
- `../reliability/scores.json` — Component reliability scores
- `../reliability/test_history.md` — Test pass/fail trends
- `../lessons_learned.md` — Failed approaches and architectural decisions

## Search Tips

When looking for context:
1. Check `features.md` for implementation approaches
2. Check `bugs.md` for known issues and prevention
3. Check `patterns.md` for reusable code patterns
4. Check `compatibility.md` for device-specific issues
5. Check `performance.md` for optimization opportunities
6. Check `tech_radar.md` for latest tool/library updates

All files are version-controlled. Use `git log --all --oneline -- memory/` to see update history.
