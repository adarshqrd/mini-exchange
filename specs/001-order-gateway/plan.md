# Plan 001 — Core Order Path

Implements [spec.md](spec.md).

## Tech choices

| Choice | Why |
|---|---|
| Java 21 (runs on installed JDK 24), Maven | Dominant language in exchange systems; records + sealed types keep the domain model small |
| No runtime dependencies; JUnit 5 for tests | Small attack surface, fast build, easy to audit — appropriate for a regulated venue |
| Hand-rolled FIX tag=value codec (not QuickFIX/J) | Spec needs 4 message types and no session layer; QuickFIX/J would add session config/state we explicitly scoped out. Revisit if session layer is added |
| Properties file for config | Meets NFR-3 with zero dependencies |

## Architecture

```
TCP clients ──► Gateway (thread per connection: read line, decode FIX)
                   │  enqueue Inbound command
                   ▼
            Single-threaded Engine loop  (LMAX-style: one writer, no locks in the core)
                   │  1. audit inbound      (AC-40)
                   │  2. validate           (AC-01..05)
                   │  3. risk check         (AC-10..13)
                   │  4. match / cancel     (AC-20..32)
                   │  5. audit outbound, route reports to sessions
                   ▼
            Session writers ──► TCP clients
```

**Why single-threaded core:** gives deterministic sequencing (enables AC-41 replay),
avoids locks in the matching path, and mirrors how real matching engines are built.
Concurrency lives only at the edges (I/O).

**Determinism:** the engine never reads the wall clock. The runner stamps each inbound
message when it is sequenced, that timestamp is audited, and the engine uses it for
TransactTime. IDs come from counters. So replaying the audit log yields identical outputs (AC-41).
*(Changed during implementation: originally an injected `Clock`; taking time from the event is
simpler and makes replay exact without a fake clock.)*

## Package layout (`com.miniexchange`)

| Package | Contents | Covers |
|---|---|---|
| `fix` | `FixMessage` (tag→value map), `FixCodec` (parse/encode, SOH or `|`) | §4, NFR-2 |
| `model` | `Side`, `Order`, `Prices` (decimal ↔ long ticks) | — |
| `config` | `SymbolConfig` (refPrice, collarPct, maxQty, maxNotional), loader | NFR-3 |
| `risk` | `Validator` (AC-01..05), `RiskChecker` (AC-10..12) | AC-01..13 |
| `book` | `OrderBook` — per symbol, `TreeMap<price, ArrayDeque<Order>>` per side | AC-20..22, NFR-1 |
| `engine` | `MatchingEngine` — applies inbound, returns outbound list; `EngineRunner` — single thread + audit | AC-23, AC-30..32 |
| `audit` | `AuditLog` (append-only file: seq, ts, direction, member, raw), `Replayer` | AC-40, AC-41 |
| `gateway` | `TcpGateway`, `Session` | §4 |
| root | `Main` (start server), `DemoClient` (scripted demo) | — |

## Data structures
- Bids: `TreeMap<Long, ArrayDeque<Order>>` descending; asks ascending. Prices are `long`
  in ticks (price × 100) to avoid floating-point errors.
- `Map<memberId, Map<clOrdId, Order>>` for duplicate detection (AC-04) and ownership-scoped cancel (AC-32).

## Test strategy
- **Traceability rule:** every test's `@DisplayName` starts with the AC/NFR id it proves, e.g. `"AC-20 price-time priority"`.
- `scripts/spec-check.sh` fails if any `AC-xx` in spec.md has no matching test → spec drift is caught in CI.
- Unit tests per component; one end-to-end test over a real TCP socket.
- **Invariant test (NFR-1):** randomised order stream (fixed seed), assert book never crossed after each event.
- **Fuzz test (NFR-2):** random/garbled lines into codec + engine, assert no exception escapes.
- **Latency (NFR-4):** simple timed loop, prints p50/p99; reported in README, not a gate.

## Risks
- Time (1 day): if behind, cut the TCP gateway and demo via `DemoClient` driving the engine in-process.
