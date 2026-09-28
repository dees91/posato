# `SCHEDULE-002` slice 1: Schedule operations in sync

- **Review tier:** `high-risk`
- **Tier reason:** It adds four mandatory operation kinds to the signed, encrypted format-1 wire and changes how every replica accepts, stores and reduces operations, including the new optional-kind range.
- **Dependencies:** slice 2 (#97, the engine's plan and date types), `SCHEDULE-001` (#93: the ADR 0006 amendment and the integration contracts).
- **Integration group:** `PR-SCHEDULE-DELIVERY`, milestone `1.2.0`.
- **Authority:** [ADR 0006 schedule amendment](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md) (`proposed`; implemented here under the maintainer's delegated goal of 2026-09-27, taking effect when the maintainer merges #93), [schedule rules](../../product/schedules-decisions.md), [SCHEDULE-002 brief](schedule-002-shared-schedules.md).

## Outcome

Replicas encode, decode, validate, store and reduce kinds 8-11 exactly as the amendment specifies, so a schedule saved on one device reaches every linked device with the same effective plans, skips and ends. Kinds 128-255 are accepted, retained and ignored in projection; kinds 12-127 stay rejected.

## Boundaries

- **Wire only.** This slice adds the payloads, the codec, validation, the reducer's schedule state and capacity outcome, the optional-kind rule, and writer mutations that author the new kinds. The local schedule store, the reconciler, authoring from the UI, and the hosts are slices 3-5.
- **Writer normalization.** Names are normalized to NFC by the writer through a small platform function (`java.text.Normalizer` on the JVM, `precomposedStringWithCanonicalMapping` on iOS). Decoders check UTF-8 and control characters only.
- **Golden vectors.** Canonical byte vectors for every payload invariant live in `commonTest`, so the JVM and iOS targets run the same vectors.
- **Non-goals:** new transport, key or envelope changes; a format-2 wire.

## Acceptance

- `AC-01` — Isolated tests written failing first: each payload round-trips canonically; each invariant (identifier, name bytes and control characters, weekday mask, minute range, equal times, 15-minute circular floor, enabled byte, date range and validity) rejects its violating vector; a non-canonical encoding is rejected.
- `AC-02` — Reducer tests: greatest total-order put wins; remove wins in any order; the 10-schedule cap yields `SCHEDULE_CAPACITY` without eviction and is recomputed from scratch; skip and end sets are grow-only and ignored for removed identifiers.
- `AC-03` — An optional-kind operation (128-255) is accepted, advances its author's sequence, and leaves the projection unchanged; kinds 12-127 are still rejected.
- `AC-04` — The same vectors pass on the JVM and on iOS (`iosSimulatorArm64Test`).

## Verification

- `./gradlew quality` (JVM and iOS tests), mutation checks on the invariants, an independent plan review before implementation and a completed-change review.
- No user-visible change; no E2E or screenshots in this slice.
