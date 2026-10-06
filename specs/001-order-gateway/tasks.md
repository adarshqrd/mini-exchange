# Tasks 001 — Core Order Path

Implements [plan.md](plan.md). Do in order; each task ends with tests green.

- [x] **T1** Project skeleton: Maven `pom.xml`, JUnit 5, package dirs, `scripts/spec-check.sh` (traceability check)
- [x] **T2** `fix` codec: parse/encode, SOH and `|` delimiters, malformed input → error not exception — *NFR-2*
- [x] **T3** `model` + `config`: records, symbol config loader from `config/exchange.properties` — *NFR-3*
- [x] **T4** `Validator` — *AC-01, AC-02, AC-03, AC-04, AC-05*
- [x] **T5** `RiskChecker` — *AC-10, AC-11, AC-12*
- [x] **T6** `OrderBook` + matching — *AC-20, AC-21, AC-22, NFR-1*
- [x] **T7** `MatchingEngine`: wire validate → risk → match, exec reports, cancel — *AC-13, AC-23, AC-30, AC-31, AC-32*
- [x] **T8** `AuditLog` + `Replayer` — *AC-40, AC-41*
- [x] **T9** `TcpGateway` + `Session` + `Main`; end-to-end socket test — *§4*
- [x] **T10** `DemoClient` scripted scenario; latency measurement — *NFR-4*
- [x] **T11** README: how to run, demo script, SDD workflow explanation; `spec-check.sh` passes
