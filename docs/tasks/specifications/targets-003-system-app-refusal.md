# `TARGETS-003` follow-up: Refuse system-critical applications in the macOS picker

- **Review tier:** `standard`
- **Tier reason:** Small reversible validation guard mirroring the already-reviewed MACOS-005 enforcement refusal; no new privilege, protocol surface, or durable-state shape. Recorded path taken because the change adds validation behavior on an exposed trust boundary.
- **Dependencies:** completed `TARGETS-003` and `MACOS-005`
- **Integration group:** `PR-MAC-MAPPING` follow-up on `chore/targets-003-system-app-refusal`
- **Authority:** Maintainer-approved plan `.agents/plans/2026-09-08-targets-003-system-app-refusal.md`, ADR 0004, threat model `T-08`

## Outcome

A person choosing Finder, Dock, or another system-critical application in the native macOS picker gets a truthful refusal, the whole batch is rejected, and every prior mapping is preserved.

## Boundaries

- Refuse in `ApplicationSelectionService.select()` before signature inspection, sharing one refusal source inside the `PosatoMacOSHelper` target; enforcement adopts it unchanged.
- One new outcome code (`7`), one new shared rejection, one new UI string; `SELF`, `INVALID_OR_UNSIGNED`, and iOS-owned `UNSUPPORTED` are not reused.
- Operation `10`, capability bits `1|2`, deadlines, and daemon rejection of operation `10` stay unchanged; no iOS, sync, DI, or Gradle changes.
- Correct the stale picker-follow-up comment and wiki sentence; extend the driver recipe's `mapping-errors` line. No migration of stored system mappings; no PoC copy.

## Acceptance

- `AC-01` — Typing `/System/Library/CoreServices/Finder.app` into the driven picker shows the new refusal copy and preserves prior mappings; `/Applications/Safari.app` maps, lists, persists, and removes.
- `AC-02` — Swift and Kotlin tests prove identifier refusal, CoreServices-path refusal, nil-identifier inspection, the `app.posato.macosx` near-miss control, whole-batch rejection, and byte-`7` decoding.
- `AC-03` — `./gradlew quality`, `git diff --check`, and the scoped private-data scan are clean; evidence sits under `build/verification/`.

## Verification

- Focused Swift/Kotlin suites, aggregate `./gradlew quality`, driver refusal plus control rows via `posato-control`, manual keyboard and VoiceOver pass, one independent completed-change review.

## Decisions or blockers

- Copy accepted: "System components such as Finder cannot be added to this group." No blocker is known before implementation.
