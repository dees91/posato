# `QUALITY-004`: Remove the avoidable manual steps from a verification run

- **Review tier:** `standard`
- **Tier reason:** Developer tooling with no product behavior, but it widens
  the driver's process reach beyond the application it launched and changes
  the maintained skill contract, so one independent completed-change review
  applies.
- **Dependencies:** completed `QUALITY-002`, `TARGETS-003`, `TARGETS-005`
- **Integration group:** `PR-VERIFICATION-PROVISIONING`
- **Authority:** `QUALITY-004` in MVP roadmap revision 9, the verification
  skill and its feature map under `.agents/skills/verify-posato/`,
  [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md)
  for the helper and the Apply authorization boundary, and the repository
  rules on device data in tracked files

## Outcome

On a Mac and an iPhone that were provisioned once, a verification run needs no
human step except the administrator authentication that ADR 0004 requires for
each Apply: `doctor` reports every one-time provisioning condition as a named
check with a remedy, the desktop commands can address the macOS helper's own
window, and the iOS application selection is either restored without the
picker or reported as unreachable with the measured reason.

## Boundaries

- Add process targeting to the desktop element commands (`snapshot`, `find`,
  `tap`, `type`, `press`, `wait`, `screenshot`). The bridge is already
  parameterized by pid; only the Kotlin side, which always passes the tracked
  application pid, needs the selector. Resolution is restricted to a process
  whose executable lives inside the staged `Posato.app`, the tracked
  application must be running, and every other pid is refused as a
  precondition. Default behavior of every command stays unchanged.
- Drive the helper's open panel deterministically: `⌘⇧G`, a typed absolute
  path, `Return`. No coordinates and no localized labels. Prove one
  `/Applications` selection end to end and rewrite the manual step in
  `features/macos-application-mappings.md`.
- Extend `doctor` into the provisioning gate: one named check per one-time
  condition, each `ok`, `missing`, or `unknown` with one remedy sentence.
  Host-observable conditions are reported directly; a state only the
  application can read, such as the helper background approval or Screen Time
  authorization, is reported as `unknown` with the one command that reveals
  it, never guessed.
- Settle the iOS selection question by measurement: whether a captured
  selection store can be placed into the App Group container with `devicectl`
  and whether it survives a reinstall. If it does, the seeding path uses an
  untracked local artifact kept beside `local.properties`; if it does not, the
  picker stays manual and the feature file says so with the observed reason.
- Never grant a permission by any means: no write to a TCC database, no SIP
  change, no MDM or PPPC profile, no relaxation of the Apply authorization
  rule, and no product-code change. The tool reports what is missing.
- Exclusive write surface while `MACOS-004`, `IOS-001`, and `SYNC-008` run:
  `tools/posato-control/**`, `.agents/skills/verify-posato/**`, and
  `docs/tasks/mvp-roadmap.md`, which no wave-4 task touches. Shared by rebase:
  `docs/wiki/log.md`. Do not touch `shared/**`, `desktopApp/**`, `iosApp/**`,
  `macosHelper/**`, `macosSyncCompanion/**`, or the Gradle build files.

## Acceptance

- `AC-01` — With the helper's panel open, the process selector makes
  `snapshot` return that window's tree and `press` and `type` reach it; a pid
  outside the staged bundle and a run with no tracked application are refused
  with exit code 3, and every command without the selector behaves as before.
- `AC-02` — The macOS mapping recipe selects an application from
  `/Applications` without a human, the mapping row and its `Remove` button
  appear, and the feature file contains no manual step or names the exact
  residual one.
- `AC-03` — `doctor` lists every one-time provisioning condition for both
  targets with its state and remedy; a missing condition fails closed and
  cannot be mistaken for a broken feature.
- `AC-04` — The iOS selection question is answered by a recorded observation
  with the exact command and result, the skill states the resulting rule, and
  no token bytes, device identifier, or personal path enters a tracked file.
- `AC-05` — `./gradlew quality` passes with no new suppression, and
  `:posato-control:test` covers the selector's refusals and the doctor check
  inventory.

## Verification

- JVM tests for bundle containment, refusal paths, the unchanged default, and
  the doctor check list.
- One driven macOS run for `AC-01` and `AC-02` with evidence under
  `build/verification/`, and one device run for the `AC-04` measurement.
- `./gradlew quality`, `git diff --check`, suppression and private-data scans,
  and one independent completed-change review.

## Decisions or blockers

- Decided: permission dialogs stay provisioning, not driver steps. macOS
  refuses synthetic events on TCC dialogs by design, and a check that passes
  only because a control was removed proves nothing.
- Decided: the Apply authentication stays exactly as ADR 0004 defines it.
  `acquireApplyGrant()` destroys its rights after each Apply and the rule is
  non-shared, so every Apply prompts; `MACOS-004` splits its proof instead of
  relaxing the rule, and this task does not touch it.
- Open: whether the helper hosts its panel in process. The helper is not
  sandboxed, so an in-process `NSOpenPanel` is expected; if a system panel
  service owns the window, resolve that pid through the same containment rule.
- Open (`hypothesis`): iOS application tokens may be bound to the current
  installation or authorization, so a captured selection may not survive a
  reinstall. One measurement decides `AC-04`.
