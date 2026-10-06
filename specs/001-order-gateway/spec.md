# Spec 001 — Core Order Path

Status: APPROVED
Owner: Adarsh
Next specs: see [ROADMAP](../ROADMAP.md)

## 1. Problem

Members of an exchange send orders electronically. Before an order reaches the
order book, the exchange must stop orders that would harm the market or the member
(fat-finger prices, oversized orders). Orders that pass must be matched fairly, and
every decision must be auditable because the venue is regulated.

This spec is the **thinnest end-to-end slice** of that path:
**order in → validate → risk check → match → execution reports out → audit trail.**

## 2. Users

| Actor | Need |
|---|---|
| Member trader / algo | Submit and cancel limit orders; receive accurate execution reports |
| Exchange risk officer | Bad orders are stopped before they reach the book |
| Exchange ops / auditor | Reconstruct exactly what happened to any order, and why |

## 3. User stories

- **US-1** As a member, I submit a limit order and am told whether it was accepted or rejected, and why.
- **US-2** As a member, my orders trade under fair price-time priority.
- **US-3** As a member, I can cancel an order that is still resting.
- **US-4** As a risk officer, orders breaching basic limits are rejected before they reach the book.
- **US-5** As an auditor, every inbound and outbound message is recorded in order.

## 4. Interface contract

Members connect over TCP and exchange **FIX 4.4 tag=value** messages, one message per
line, fields delimited by SOH (`\u0001`) or `|` (accepted for readability in demos).

| MsgType (35) | Direction | Purpose |
|---|---|---|
| `D` NewOrderSingle | in | New limit order |
| `F` OrderCancelRequest | in | Cancel a resting order |
| `8` ExecutionReport | out | New / Rejected / Trade / Canceled |
| `9` OrderCancelReject | out | Cancel could not be applied |

Required fields on `D`: SenderCompID(49), ClOrdID(11), Symbol(55), Side(54: 1=Buy, 2=Sell),
OrderQty(38), OrdType(40), Price(44).
Required fields on `F`: SenderCompID(49), ClOrdID(11), OrigClOrdID(41).

## 5. Acceptance criteria

### Validation
- **AC-01** Given a well-formed limit order that passes all checks, then an ExecutionReport ExecType=New (150=0) is sent.
- **AC-02** Given a missing required field or invalid value (qty ≤ 0, price ≤ 0, unknown side), then ExecType=Rejected (150=8) is sent with Text(58) naming the problem.
- **AC-03** Given OrdType(40) other than 2 (Limit), then reject with Text "Unsupported order type".
- **AC-04** Given a ClOrdID already used by the same member, then reject with Text "Duplicate ClOrdID".
- **AC-05** Given an unknown Symbol, then reject with Text "Unknown symbol".

### Pre-trade risk
- **AC-10** Given qty above the symbol's max order qty, then reject with Text "Max order qty exceeded".
- **AC-11** Given notional (qty × price) above the symbol's max notional, then reject with Text "Max notional exceeded".
- **AC-12** Given price more than the symbol's collar % away from its reference price, then reject with Text "Price outside collar".
- **AC-13** A rejected order never reaches the book and never trades.

### Matching
- **AC-20** An incoming order matches the best-priced opposite orders first; at equal price, earliest-arrived first (price-time priority).
- **AC-21** Trades execute at the resting order's price.
- **AC-22** If an incoming order is only partly matched, the remainder rests on the book.
- **AC-23** For every trade both sides receive ExecType=Trade (150=F) with LastQty(32), LastPx(31), CumQty(14), LeavesQty(151), and OrdStatus(39) = 1 (partial) or 2 (filled).

### Cancel
- **AC-30** Given a resting order, an OrderCancelRequest referencing its OrigClOrdID removes it and sends ExecType=Canceled (150=4).
- **AC-31** Given a cancel for an unknown, filled or already-cancelled order, then OrderCancelReject (35=9) is sent and the book is unchanged.
- **AC-32** A member cannot cancel another member's order (treated as unknown → AC-31).

### Audit
- **AC-40** Every inbound and outbound message is appended to an audit log with a monotonic sequence number and timestamp.
- **AC-41** Replaying the audit log's inbound messages into a fresh engine produces identical outbound messages (determinism).

### Clarifications (found during implementation)
- **C-1 (AC-04)** "Already used" means a ClOrdID of a previously *accepted* order. A rejected order's ClOrdID may be reused, so a member can fix and resend a rejected order. ClOrdIDs are scoped per member.
- **C-2 (§4)** Member identity is bound to the TCP connection from the SenderCompID of its first message; all later messages on that connection are attributed to that member.
- **C-3 (§4)** Outbound messages use `|` as delimiter for readability. BodyLength(9) and CheckSum(10) are not computed (FIX session layer, out of scope).

## 6. Non-functional requirements

- **NFR-1 Book integrity**: after every event the book is never crossed (best bid < best ask).
- **NFR-2 Robustness**: malformed input never crashes the process or affects other members.
- **NFR-3 Configuration**: symbols, reference prices, collars and limits load from a config file.
- **NFR-4 Latency**: inbound→ack latency is measured and the p50/p99 reported (no hard target in v1).

## 7. Out of scope (for this spec)

Kill switch, credit limits, dashboard, metrics, event streaming (→ later specs).
Market/IOC/FOK orders, amends (35=G), self-trade prevention, auctions, persistence
across restarts, FIX session layer (logon/heartbeat/resend), authentication.
