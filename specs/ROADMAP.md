# Roadmap — incremental specs

Each spec is a shippable slice. A spec is written and approved before its plan and code.

| # | Spec | Status | Notes |
|---|---|---|---|
| 001 | Core order path | APPROVED | FIX in → validate → risk → match → cancel → audit |
| 002 | Risk controls | IDEA | Kill switch (operator command + auto after N rejects in M sec, cancels resting orders); per-member credit limit (cumulative open notional) |
| 003 | Live dashboard | IDEA | Web view of order book, trades, rejects; counters + latency histogram |
| 004 | Event streaming | IDEA | `EventPublisher` interface, in-memory impl, Kafka adapter |

## Decisions log
- 2026-10-06: Timeline cut to 1 day. Original single spec split into 001–004; 001 is the thinnest end-to-end slice.
- 2026-10-06: No Docker on dev machine → Kafka deferred to 004 behind an interface.
