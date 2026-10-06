# mini-exchange

A minimal exchange order path — **FIX order entry → validation → pre-trade risk → price-time
matching → execution reports → replayable audit trail** — built with **spec-driven development**
and AI coding agents.

```
TCP members ──► Gateway ──► queue ──► Engine thread ──────────────────────────► reports to members
 (FIX 4.4)     (thread per   │         1 audit IN
               connection)   │         2 validate   (AC-01..05)
                             │         3 risk       (AC-10..13)
                             │         4 match/cancel (AC-20..32)
                             │         5 audit OUT, flush, then send (AC-40)
                             ▼
                      audit/*.log ──► replay ──► identical output (AC-41)
```

## Quick start

Requires JDK 21+ and Maven. Scripts pick a JDK 21+ automatically.

```bash
scripts/test.sh       # 47 tests + spec traceability check
scripts/server.sh     # terminal 1: start exchange on :9878
scripts/demo.sh       # terminal 2: scripted demo of every acceptance criterion
scripts/replay.sh     # after stopping the server: prove the audit log replays identically
scripts/bench.sh      # engine latency
```

## How it was built: spec-driven development

Every feature goes **spec → plan → tasks → code**, and each step is a reviewable file in git.

| Step | File | Who |
|---|---|---|
| What & why, numbered acceptance criteria | [`specs/001-order-gateway/spec.md`](specs/001-order-gateway/spec.md) | Agent drafted, **human decided** scope & open questions |
| How, with a reason for each choice | [`plan.md`](specs/001-order-gateway/plan.md) | Agent drafted, human reviewed |
| Ordered tasks tagged with ACs | [`tasks.md`](specs/001-order-gateway/tasks.md) | Agent |
| Code + tests | `src/` | Agent, verified by tests + CI check |
| Rules for agents | [`CLAUDE.md`](CLAUDE.md) | Human |
| Incremental roadmap + decisions log | [`specs/ROADMAP.md`](specs/ROADMAP.md) | Human |

**Traceability.** Every test's name starts with the requirement it proves
(`@DisplayName("AC-20 best price matches first…")`). [`scripts/spec-check.sh`](scripts/spec-check.sh)
fails the build if any requirement in an approved spec has no test — so spec drift is caught
mechanically, and agents can't silently skip a requirement.

**Spec changes before code changes.** When implementation exposed ambiguity (e.g. *can a rejected
order's ClOrdID be reused?*), the spec got a clarification (C-1..C-3) and a test, before the code.

**Rescoping.** The timeline dropped from a week to one day; instead of cutting corners, the scope was
split into incremental specs (001 core → 002 risk controls → 003 dashboard → 004 Kafka).

## Key design decisions

| Decision | Why |
|---|---|
| **Single-threaded engine**, concurrency only at the I/O edge | No locks in the hot path; a total order of events; enables exact replay. Same idea as the LMAX architecture. |
| **Deterministic core** — time comes from the sequenced inbound event, IDs from counters | Replaying the audit log reproduces every outbound byte (AC-41). Useful for incident investigation and regulators. |
| **Prices as `long` ticks**, never `double` | No floating-point error on money; exact integer collar & notional maths. |
| **Audit before send** | A member can never receive a report that isn't in the audit trail. |
| **Hand-rolled FIX codec**, not QuickFIX/J | Spec needs 4 message types and no session layer; zero runtime dependencies is easier to audit. Revisit when the session layer is in scope. |
| **Graceful shutdown drains the queue** | No accepted message is lost on stop. |

## Results

- **47 tests**, all 22 spec requirements covered (`scripts/spec-check.sh`)
- Book never crossed across a 20,000-event randomised stream (NFR-1)
- 50,000 fuzzed messages, zero exceptions escaped (NFR-2)
- Engine latency, laptop, in-process: **p50 ≈ 4 µs, p99 ≈ 35 µs** (NFR-4; excludes network & audit I/O)

## Not production-ready yet (honest gaps)

FIX session layer (logon, heartbeats, sequence numbers, resend), authentication of members,
persistence/recovery on restart (the audit log is the natural basis: replay to rebuild state),
bounded inbound queue / back-pressure, metrics & alerting (spec 003), kill switch & credit limits
(spec 002), event streaming (spec 004).

## Layout

```
specs/                     spec → plan → tasks, roadmap, decisions log
src/main/java/com/miniexchange/
  fix/       FIX tag=value codec
  config/    symbol limits from config/exchange.properties
  risk/      Validator (AC-01..05), RiskChecker (AC-10..12)
  book/      OrderBook — price-time priority
  engine/    MatchingEngine (pure, deterministic), EngineRunner (single thread + audit)
  audit/     AuditLog, Replayer
  gateway/   TcpGateway
  Main, DemoClient, Latency
scripts/     test, server, demo, replay, bench, spec-check
```
