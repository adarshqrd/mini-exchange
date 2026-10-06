# mini-exchange Constitution

## Core Principles

### I. Traceable Acceptance Criteria (NON-NEGOTIABLE)

- Every requirement in a spec MUST carry a stable numbered ID: `AC-xx` for acceptance criteria,
  `NFR-x` for non-functional requirements.
- Every such ID MUST be proven by at least one test whose `@DisplayName` starts with that ID
  (e.g. `"AC-20 price-time priority"`).
- `scripts/spec-check.sh` MUST pass before any change is merged; a failing check is spec drift
  and blocks the change.

**Rationale**: Requirements without a proving test are claims, not guarantees. A mechanical check
keeps specs and code from drifting apart silently.

### II. Spec Before Code

- Specs (`specs/NNN-name/spec.md`) are the source of truth. Behaviour not described in an approved
  spec MUST NOT be implemented; it requires a spec change first.
- If implementation reveals a spec is wrong or ambiguous, work on the code MUST stop until the spec
  is updated and the decision is recorded in the `specs/ROADMAP.md` decisions log.
- Each feature follows spec → plan → tasks → implement. Tests are written first for each task.

**Rationale**: A regulated venue must be able to show why the system behaves as it does; the spec
is that record, so it must lead the code, never trail it.

### III. Deterministic Single-Threaded Engine (NON-NEGOTIABLE)

- The engine core MUST run on a single thread with no locks; concurrency lives only at the edges
  (gateway, session writers) and hands work to the core through a sequenced inbound queue.
- The only source of time in the engine is the timestamp of the sequenced inbound event. The
  engine MUST NOT call `System.currentTimeMillis()`, `System.nanoTime()`, `Instant.now()` or any
  other wall-clock source.
- The engine MUST NOT use randomness or depend on unordered-collection iteration order
  (e.g. `HashMap`/`HashSet` iteration).
- Replaying the audited inbound sequence into a fresh engine MUST reproduce identical outbound
  messages.

**Rationale**: Determinism makes replay, audit reconstruction and debugging exact. Any hidden
input (clock, RNG, hash order) breaks that guarantee.

### IV. Integer Prices

- Prices MUST be represented as `long` ticks everywhere in the domain, engine and wire codec.
- `double`/`float` MUST NOT be used for prices or price arithmetic. Conversion to a decimal
  representation is permitted only at display boundaries.

**Rationale**: Floating point cannot represent most decimal prices exactly; rounding errors in
matching or risk checks are correctness and fairness bugs.

### V. Zero Runtime Dependencies

- Production code MUST have no third-party runtime dependencies beyond the JDK.
- Any proposed runtime dependency MUST be justified in the feature's `plan.md` (what it provides,
  why hand-rolling is worse, what it costs in attack surface) before it is added.
- Test-scope dependencies (e.g. JUnit) are permitted.

**Rationale**: A small dependency surface keeps the build fast, the system auditable, and the
security exposure appropriate for a regulated venue.

### VI. Audit Before Send (NON-NEGOTIABLE)

- Every inbound message MUST be appended to the audit log before it is processed.
- Every outbound message MUST be appended to the audit log before it is sent to any client.
- Audit entries MUST carry a monotonic sequence number and the event timestamp.

**Rationale**: If a client can observe a message the audit log does not contain, the venue cannot
reconstruct what happened. Audit-first ordering makes the log the authoritative record.

## Engineering Constraints

- Language and build: Java 21+ with Maven; scripts under `scripts/` select a JDK 21+ toolchain.
- Configuration via plain properties files unless a plan justifies otherwise (see Principle V).
- External integrations (e.g. messaging, dashboards) MUST sit behind interfaces at the edge so the
  engine core stays dependency-free and deterministic.

## Development Workflow & Quality Gates

- Workflow per feature: `spec.md` (what & why, numbered ACs, no tech choices) → `plan.md` (how, each
  tech choice with a one-line "why") → `tasks.md` (small ordered tasks tagged with AC ids) →
  implement one task at a time, tests first, ticking the task when green.
- Gates before merge:
  - `scripts/test.sh` passes (unit tests + traceability).
  - `scripts/spec-check.sh` passes.
  - Reviewer confirms no engine-core violation of Principles III, IV and VI.

## Governance

- This constitution supersedes other practices. `CLAUDE.md` is the runtime guidance file for
  agents and MUST stay consistent with it.
- Amendments are made via `/speckit-constitution`, recorded with a dated entry in the
  `specs/ROADMAP.md` decisions log, and reflected in `CLAUDE.md` where relevant.
- Versioning follows semantic versioning: MAJOR for removing or redefining a principle, MINOR for
  adding a principle or materially expanding guidance, PATCH for clarifications.
- Every plan MUST include a constitution check; every review MUST verify compliance. Any deviation
  MUST be justified in the plan and approved before implementation.

**Version**: 1.0.0 | **Ratified**: 2026-10-06 | **Last Amended**: 2026-10-06
