# Changelog

All notable changes to this project are documented here.

## [1.0.0] - 2026-05-12

### Released
- Published **payment-event-ledger-eos** as the fintech event-correctness flagship in the portfolio.
- Packaged the Outbox Pattern, Debezium CDC story, idempotency handling, and exactly-once verification surfaces into a reviewable system.
- Positioned the repo around the dual-write problem rather than around generic "Kafka experience."

### Why this mattered
- Payment systems fail noisily when duplicates show up, but they fail expensively when reconciliation becomes ambiguous.
- Existing messaging demos rarely make replay confidence or business evidence legible to operators.
- This release made the repo useful to platform, fintech, and RevOps audiences evaluating event integrity.

## [0.1.0] - 2026-02-21

### Shipped
- Locked the first internal model for payment events, outbox records, replay verification, and idempotency evidence.
- Added a verification surface that explained event integrity in business terms rather than in broker jargon.

## [Prototype] - 2025-04-24

### Built
- Prototyped the payment event flow around dual-write failure, ordering ambiguity, and downstream settlement questions.
- Used the prototype to test whether event correctness could be narrated clearly for non-specialists.

## [Design Phase] - 2024-03-19

### Designed
- Anchored the system around exactly-once pressure, not just stream throughput.
- Chose operator-visible evidence paths over opaque transactional claims.
- Framed the repo so business-critical event trust was as visible as transport mechanics.

## [Idea Origin] - 2023-06-06

### Observed
- The idea came from repeated cases where event-driven systems were technically sophisticated but operationally hard to trust after failure.
- The missing product was a payment-event control layer that could make correctness reviewable.