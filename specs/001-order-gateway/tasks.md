# Tasks 001 — Core Order Path

Implements [plan.md](plan.md). Do in order; each task ends with tests green.

- [ ] **T1** Project skeleton: Maven `pom.xml`, JUnit 5, package dirs, `scripts/spec-check.sh` (traceability check)
- [ ] **T2** `fix` codec: parse/encode, SOH and `|` delimiters, malformed input → error not exception — *NFR-2*
- [ ] **T3** `model` + `config`: records, symbol config loader from `config/exchange.properties` — *NFR-3*
- [ ] **T4** `Validator` — *AC-01, AC-02, AC-03, AC-04, AC-05*
- [ ] **T5** `RiskChecker` — *AC-10, AC-11, AC-12*
- [ ] **T6** `OrderBook` + matching — *AC-20, AC-21, AC-22, NFR-1*
- [ ] **T7** `MatchingEngine`: wire validate → risk → match, exec reports, cancel — *AC-13, AC-23, AC-30, AC-31, AC-32*
- [ ] **T8** `AuditLog` + `Replayer` — *AC-40, AC-41*
- [ ] **T9** `TcpGateway` + `Session` + `Main`; end-to-end socket test — *§4*
- [ ] **T10** `DemoClient` scripted scenario; latency measurement — *NFR-4*
- [ ] **T11** README: how to run, demo script, SDD workflow explanation; `spec-check.sh` passes
