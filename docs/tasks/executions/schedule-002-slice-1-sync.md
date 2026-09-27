# Execution: `SCHEDULE-002` slice 1

- **Brief:** [Schedule operations in sync](../specifications/schedule-002-slice-1-sync.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude, under the maintainer's delegated goal (2026-09-27)
- **Reviewer:** independent agents (plan and completed change)
- **Branch:** `feature/schedule-002-sync`, stacked on #97
- **Updated:** 2026-09-27

## Plan

1. **Model** (`sync/domain/SyncModels.kt`).
   - `ScheduleSyncId` wraps a UUIDv4 `SyncIdentifier`; `hex` maps it to the engine's `ScheduleId`.
   - Payloads: `SchedulePut(id, name, weekdays, startMinute, endMinute, enabled)` with a redacted `toString`; `ScheduleRemove(id)`; `ScheduleSkip(id, date)` and `ScheduleOccurrenceEnd(id, date)` with the engine's `ScheduleDate`; `OptionalExtension(kind, tail)` with content equality and a redacted `toString`.
   - Limits: `MAX_SYNCHRONIZED_SCHEDULES = 10`, the name byte limit and the date years.
2. **Codec** (`sync/data/SyncOperationCodec.kt`).
   - Tags 8-11 with the amendment's layout: put = id, `u16` name length and name, `u8` mask, `u16` start, `u16` end, `u8` enabled; skip and end = id, `u16` year, `u8` month, `u8` day.
   - `ScheduleWireRules` holds the invariants shared by encode and decode: strict UTF-8 name of 1-80 bytes without C0/C1 controls; mask 1-127; minutes 0-1439; start not equal to end; circular length at least 15; enabled 0 or 1; year 2000-2100 and a real calendar date. Encode returns `null` for a payload that breaks one; decode rejects it. NFC is not checked on decode.
   - Tags 128-255 decode to `OptionalExtension(kind, tail)` where the tail is every remaining plaintext byte, and re-encode to the same bytes. Tags 12-127 and 0 stay rejected.
3. **Reducer** (`sync/domain/SyncReducer.kt`).
   - A pre-pass collects every removed schedule identifier, so removal wins in any order.
   - Puts in total order: a removed identifier is `NO_OP`; a live identifier is replaced (`APPLIED`); a new identifier at 10 live schedules is `SCHEDULE_CAPACITY` with no state change; otherwise it is added.
   - Remove: `APPLIED` for its first operation in order, `NO_OP` after.
   - Skip and end: grow-only sets of `(id, date)`, `NO_OP` for a removed identifier or a repeat.
   - `OptionalExtension`: `NO_OP`; contiguity already counts it, so it advances the author's sequence.
   - `SyncProjection` gains `schedules` (sorted by identifier), `scheduleSkips` and `scheduleEnds`, with defaults.
4. **Writer** (`sync/domain/SyncWriter.kt`): `LocalSyncMutation.PutSchedule`, `RemoveSchedule`, `SkipOccurrence` and `EndOccurrence` mapped in `toPayload`. The writer never authors an optional kind; it normalizes and validates names and dates itself (see R1-R3 below).
5. **Tests, written first** (`commonTest`, so they run on the JVM and on iOS):
   - `ScheduleOperationCodecTest`: round trips; hand-written payload bytes pinned after a header taken from a known operation, so the layout does not come from the implementation; one rejected vector per invariant for decode and encode; kinds 128 and 255 round-trip with arbitrary tails; kinds 12 and 127 rejected.
   - `SyncReducerScheduleTest`: greatest order wins; remove before or after a put in order; the cap with the 11th new identifier and a later removal freeing a slot; skip and end grow-only and ignored for removed identifiers; an optional operation between two domain operations of one author keeps the later one applied.
   - Writer or store: a remote bundle with kind 200 is accepted and survives a replica reopen; kind 12 is still `INVALID_OPERATION`.
   - Redaction: `SchedulePut` and `OptionalExtension` are covered by the redaction test family.
6. **Docs:** the record, one wiki log entry at closeout.

## High-risk plan review

- **Verdict:** `changes-required`, folded here before any code.
- **R1** Schedule mutations are validated in `LocalSyncMutation.toPayload` (like `StartSession`), so an invalid one is `INVALID_MUTATION` and never reaches `encode`, whose `null` would freeze the writer. Test: an invalid put is refused and the writer stays active.
- **R2** Slice 1 owns writer normalization: `PutSchedule` carries the typed name, which `toPayload` trims and normalizes to NFC through the existing `expect` before validating. Vectors: a decomposed name decodes and round-trips unchanged; the writer turns `e` + U+0301 into U+00E9 on the JVM and on iOS.
- **R3** `SkipOccurrence` and `EndOccurrence` carry the author's local date; `toPayload` refuses a date more than 400 days after it.
- **R4** Terminal markers are per device and never synchronized; slice 3 stores them in its local schedule store (`local_schedule_terminal`). Slice 1 adds none.
- **R5** The removal pre-pass reads only the applicable (gap-free) operations. Test: a remove behind a sequence gap leaves the schedule live.
- **R6** `SyncProjectionDigest` covers schedules, removed identifiers, skips and ends in sorted order, and the seeded permutation test gains puts, removes, a capacity overflow and facts.
- **R7** Control characters are Unicode Cc: U+0000-001F and U+007F-009F. Vectors pin 0x7F and U+0085.
- **Recommended, taken.** Capacity is decided after the pre-pass, so a later remove frees a slot in every delivery order (tested both ways); this differs from the domain cap on purpose and is stated here. The projection exposes `removedScheduleIds`. Skip, end and the schedule mutations redact dates. Boundary vectors cover both sides of every limit (80/81 bytes with a split 2-byte character, a length prefix past the end, mask 0/127/128, minutes 1439/1440, 14/15 minutes across midnight, enabled 2, years 1999/2101, Feb 29 in 2000/2024 accepted and 2023/2100 refused, Apr 31, overlong and surrogate UTF-8). `OptionalExtension` accepts only kinds 128-255, keeps an `ImmutableBytes` tail, and round-trips an empty tail and a tail at the plaintext limit; decoding stays total over 128-255 so any later meaning lives only in projection. A skip ordered before its put, and a skip for a capacity-refused identifier, stay in the set. One full plaintext vector per kind is written byte by byte, header included.
- **Optional.** Taken: `SyncFormatLimits.MAX_SYNCHRONIZED_SCHEDULES` is `ScheduleLimits.MAX_SCHEDULES`. Not taken: a separate restore test for a stored kind 12; restore decodes through the same codec, whose rejection of kinds 12-127 is tested.

## Result

- **Delivered** as planned and folded: payloads, `ScheduleWireRules`, codec tags 8-11 and 128-255, writer mapping with NFC and the 400-day rule, reducer state and `SCHEDULE_CAPACITY`, projection fields, digest.
- **Found on the way.** Strict UTF-8 decoding throws `CharacterCodingException` on the JVM, which the codec did not catch, so a malformed string in any string payload (domains included) threw instead of being rejected. It is caught now, with a test for the existing domain kind.
- **Tests.** `ScheduleOperationCodecTest` (14), `SyncReducerScheduleTest` (9), `SyncWriterScheduleTest` (4), one redaction and one domain UTF-8 test. They failed before the implementation (18 failures), except the redaction test, whose `toString` came with the model.
- **Mutations.** Ten checked; nine fail a test (gap pre-pass, cap boundary, skip for a removed schedule, C1 controls, real dates, 15-minute floor, 400-day rule, NFC, the UTF-8 catch). The enabled-byte check is equivalent: a byte of 2 decodes to `false`, re-encodes as 0, and the canonical check rejects it.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `quality` (JVM and iOS tests, detekt, ktlint) | pass | after the last correction |
| Mutation checks | pass | nine of ten killed, one equivalent (see Result) |
