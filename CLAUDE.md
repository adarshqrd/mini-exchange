# mini-exchange — agent instructions

This repo uses **spec-driven development**. Specs are the source of truth; code follows.

## Workflow (every feature)
1. **Spec** — `specs/NNN-name/spec.md`: what & why, numbered acceptance criteria (`AC-xx`). No tech choices.
2. **Plan** — `plan.md`: how. Tech choices with a one-line "why".
3. **Tasks** — `tasks.md`: small ordered tasks, each tagged with the AC ids it covers.
4. **Implement** — one task at a time; tests first; tick the task box when green.

## Rules
- Never implement behaviour that isn't in an approved spec. If it's needed, propose a spec change first.
- If implementation reveals the spec is wrong or ambiguous, stop and update the spec (and note it in `specs/ROADMAP.md` decisions log) before changing code.
- Every test `@DisplayName` starts with the AC/NFR id it proves, e.g. `"AC-20 price-time priority"`.
- `scripts/spec-check.sh` must pass: every AC in a spec has at least one test.
- Engine core is single-threaded and deterministic: no `System.currentTimeMillis()`, no randomness, no `HashMap` iteration order dependence in the engine — use the injected `Clock`.
- Prices are `long` ticks, never `double`.

## Commands
- Tests + traceability: `scripts/test.sh` (Maven may default to an old JDK; scripts select JDK 21+)
- Spec traceability only: `scripts/spec-check.sh`
- Server / demo / replay / latency: `scripts/server.sh`, `scripts/demo.sh`, `scripts/replay.sh`, `scripts/bench.sh`
