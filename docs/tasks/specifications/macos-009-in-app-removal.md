# `MACOS-009`: Remove Posato's background helper from inside the macOS app

- **Review tier:** `high-risk`
- **Tier reason:** Removal changes system proxy ownership, deletes a custom Authorization Services right, and unregisters a root daemon; a wrong order can leave the proxy pointing at a daemon that no longer exists.
- **Dependencies:** completed `MACOS-008` (merged as `73ecb2b`), which transferred removal here in roadmap revision 18.
- **Integration group:** `PR-MAC-REMOVAL`, roadmap wave Release/R2, in parallel with `DESIGN-003` and `SYNC-017`.
- **Authority:** [MVP roadmap](../mvp-roadmap.md) (revision 18), [macOS helper ADR](../../decisions/0004-macos-helper-ownership-and-lifecycle.md) (update, repair, disablement, and removal), [browser enforcement ADR](../../decisions/0005-macos-browser-enforcement-and-coexistence.md), [DESIGN.md](../../../DESIGN.md) (This Mac), [threat model](../../security/apple-mvp-threat-model.md), [MACOS-008 record](../executions/macos-008-developer-id-distribution.md).

## Outcome

On macOS, the This Mac setup offers a removal action with a destructive confirmation. It runs the ADR 0004 removal in order, verifies every step, and then tells the person that Posato can be moved to the Trash. When a step fails, the app names the real state and its recovery instead of claiming success.

## Boundaries

- `observed` starting point: the helper already implements Disable and Remove (`main.swift`, `LifecyclePolicy.swift`), and `MacOsHelperClient` already sends both requests, but the shared `MacHelperPort` exposes only enable, recheck, and approval settings, and no UI reaches removal.
- Reuse the existing helper operations and wire protocol; add no privileged operation, shell command, or Login Items instruction. Keep the ADR 0004 order: restore and verify proxy settings, remove and verify the exact authorization right, unregister the daemon, and report the verified outcome.
- Amend DESIGN.md for the removal entry, its confirmation, progress, success, and failure copy within the existing This Mac row patterns.
- Leave `macosHelper/Sources/PosatoMacOSHelper/BlockedPage.swift` and the DESIGN.md pause page section to `DESIGN-003`.
- Non-goals: deleting Posato's local data (the privacy policy already says it stays until removed), a separate in-app Disable, an uninstaller that runs after the app is dragged to the Trash, and changes to update behavior (quit, replace, and open).

## Acceptance

- `AC-01` — The removal action is reachable from This Mac, asks for destructive confirmation, and shows progress while the helper call runs.
- `AC-02` — After a successful removal on a Developer ID package, the proxy settings match the pre-session state, the authorization right is absent, and the daemon is no longer registered, each checked independently of the app's own report.
- `AC-03` — A helper that is not enabled or awaits background approval, a lost reply, a proxy restore conflict, and a failed rule removal or unregister each end in a truthful state with a precise next action, and never report removal as complete. Removal needs no administrator prompt: the root daemon restores and removes the right, then the helper unregisters (`observed` 2026-09-15).
- `AC-04` — After removal, no Posato background item is enabled, deleting the app leaves no Posato background item at all, and reinstalling the package and enabling the helper works again.

## Verification

- Independent plan review before implementation; independent completed-change review after.
- Tests for the port and state mapping of every removal outcome; `./gradlew quality`.
- Physical run on the Developer ID package through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md), with system-side checks of proxy settings, the authorization right, and background registration; evidence stays under `build/verification/`. The Mac is shared with `DESIGN-003`, so run the physical gates one after the other.

## Decisions or blockers

- **Accepted 2026-09-15 (maintainer):** removal is refused during an active session, with a notice to end the session first.
- **Accepted 2026-09-15 (maintainer):** the entry point is the expanded This Mac row.
