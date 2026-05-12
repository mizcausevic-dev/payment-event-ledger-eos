# Architecture

## Core idea

This repo models the payment correctness story that senior platform and fintech teams care about:

1. Write the payment row.
2. Write the outbox row in the same transaction.
3. Let CDC publish only committed rows.
4. Commit consumer offsets only after durable downstream work completes.
5. Block replayed payment submissions through idempotency claims.

The result is not “magic exactly-once.” The result is a system where every handoff has evidence.

## Local-first mode

The application ships with seeded in-memory data so the operator surface, APIs, screenshots, and tests all work instantly.

That keeps the repo one-shot friendly:

- no Kafka cluster required to understand the architecture
- no external database required to run the app
- no missing local services blocking verification

## Event-stack story

The compose file extends the demo toward a more realistic topology:

- `postgres` as the transaction system of record
- `redpanda` as the Kafka-compatible event backbone
- `debezium/connect` as the CDC lane

This repo does not pretend to be a full payment processor. It is a focused operator-friendly artifact for explaining:

- outbox durability
- CDC visibility
- idempotency enforcement
- consumer checkpoint discipline

## API design

`/api/dashboard/summary`
: Overview for a control room or platform operator dashboard.

`/api/payments/{paymentId}`
: Single payment lookup with risk band, partition, and current stream state.

`/api/outbox`
: Raw outbox view for debugging sequence state.

`/api/eos-verification`
: Aggregates assessment, checkpoints, idempotency claims, and CDC lanes.

`/api/payments/submit`
: Simulates accepting a new payment and appending a durable outbox record.

`/api/analyze/payment`
: Turns payment facts into stable/watch/escalate guidance.
