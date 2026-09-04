# Execution: `QUALITY-004`

- **Brief:** [Remove the avoidable manual steps from a verification run](../specifications/quality-004-unattended-verification.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** agent
- **Reviewer:** independent agent
- **Branch:** `feature/quality-004-unattended-verification`
- **Worktree:** `~/Projects/Polyglot/posato-quality-004`
- **Updated:** 2026-09-04

## Plan

1. Measure where the helper's panel lives and whether its pid is resolvable
   under the staged-bundle containment rule.
2. Add the process selector with its refusals and tests, default path unchanged.
3. Drive the panel with `⌘⇧G` and an absolute path, prove one selection and
   removal, and rewrite the manual step in the feature file.
4. Extend `doctor` into the provisioning gate, one named check and remedy per
   one-time condition.
5. Measure the iOS selection store on the device and state the resulting rule.
6. Run `./gradlew quality`, complete the independent review, rerun affected
   checks, and close this record in the closeout commit.

## Result

- `--process <name|pid>` reaches every desktop element command.
  `ProcessTargeting` decides over processes whose executable resolves, through
  `toRealPath()`, inside the staged bundle; the tracked application must be
  running and every other selector is a refusal. A null selector is unchanged.
- `observed`: the helper presents its panel with `NSOpenPanel.runModal()` and
  never runs an `NSApplication` event loop, so it owns a real window while
  exposing no accessibility server; `AXUIElementCopyAttributeValue` fails at
  once with `kAXErrorCannotComplete` and neither `AXManualAccessibility` nor
  `AXEnhancedUserInterface` changes it. `snapshot` therefore reports
  `PROCESS_NOT_INSPECTABLE`, and key events reach the panel through the session
  tap after the bridge brings that process forward, re-checking the front
  before every character. The fallback is armed only when a process was
  addressed, so no default path changed; `AC-01` was amended and accepted.
- The picker is driven end to end (`⌘⇧G`, a typed absolute path, `--submit`,
  one more `Return`), so the manual step is gone from the feature file.
- `doctor` is the provisioning gate. `DoctorCheck` gained a three-valued
  `state`; `DoctorReport.ok` is unchanged, so an `unknown` condition is visible
  without blocking, and `error` is reserved for a configuration packaging
  rejects, so ad-hoc worktrees stay `ok: true`. `GradleStager` now passes the
  new `posato.macos.syncProvisioningProfile`, without which the driver could
  not stage a development-signed package.
- `observed`: `./gradlew quality` runs the packaging tasks without the signing
  properties and silently restages ad-hoc, removing the picker; `desktop.staged`
  now names that case and its remedy.
- `AC-04` is answered. `devicectl device copy from|to` captures and restores
  `Library/Application Support/Posato/ApplicationMappings/mappings-v1.json`;
  after a full uninstall, reinstall and restore the section reported
  `Applications selected: 1`. Seeding removes the picker from a rerun but not
  the consent alert, which resets on every reinstall. Whether the restored
  tokens still enforce stays `open` for `IOS-001`; the path needs no rewrite
  there beyond the domain, since `devicectl` supports `appGroupDataContainer`.

## Completed-change review

- **Verdict:** 0 Critical, 3 Required, 3 Recommended, 2 Optional.
- **Critical or Required findings:** `awaitWindow` swallowed every precondition
  refusal, so `wait --process` timed out at exit 4 instead of refusing at exit
  3; the frontmost gate for session-tap typing ran once per call rather than
  per character, so losing the front mid-string would have typed the rest into
  another window; `AC-01` was not met as written and carried no amendment.
- **Resolution:** all three corrected. `WindowWait` now tolerates only
  `PROCESS_NOT_ALLOWED` while polling, reports every other refusal at once, and
  ends a never-resolving selector as its own refusal; `typeText` re-checks the
  front per character on the session path; `AC-01` was amended and accepted.
  The driven macOS run was repeated after the last correction.
- **Advisory findings:** all three Recommended and both Optional were applied,
  each a one-line correctness or accuracy fix inside the diff: the session
  fallback is armed only when a process was addressed; the screenshot layer
  rule keys off the resolved pid, not the option; dead imports removed; the pid
  rule is ASCII-only; and a README sentence overstating what `doctor` prints was
  corrected. None expanded scope.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `:posato-control:test` | pass, 69 tests (was 30) | selector and wait refusals, containment and symlink escape, doctor inventory, the ad-hoc `ok: true` regression, profile decode, redaction |
| Driven macOS run: panel selection and removal | pass | run `20260904-163447-f56b`: count 0, 1, 0 with four screenshots |
| `--process` refusals on the real host | pass | foreign pid, dead pid, unknown name, no tracked application, and `wait --process` all exit 3 |
| Device run: iOS selection-store measurement | pass | runs `20260904-1617*`, `1621*`, `1622*`: capture, reinstall, restore, `Applications selected: 1` |
| `./gradlew quality` | pass, no new suppression | rerun after the last correction |
| `git diff --check`, private-data scan | pass | no team, identity, profile path, device identifier or token in a tracked file; 22 transcript redactions |

## Blockers and accepted risks

- The macOS run needs the staged development package and the existing
  Accessibility and Screen Recording grants; the device run needs the iPhone.
- Accepted limit: the administrator authentication for each Apply stays a human
  step by ADR 0004, and the Screen Time consent alert stays one too. Seeding a
  captured selection removes the picker from a rerun, but a reinstall returns
  authorization to not determined and the alert belongs to SpringBoard.
- Accepted risk: this checkout was switched to Apple Development signing to
  prove `AC-02`, changing machine state the concurrent checkouts share. The
  identity is per-checkout in the ignored `local.properties`, so they keep
  staging ad-hoc, but the packaging verifier branches its entitlements on
  `signingIdentity == "-"` and `keychain-access-groups` exists only in the
  team-identity branch, so a Keychain item written under one signature can read
  back as absent under the other, and `SMAppService` registration binds to the
  signature, so a restage can require a fresh System Settings approval. Treat
  either symptom as this cause before calling it a defect.
- Accepted limit: the addressed process must stay frontmost for the panel keys;
  the driver brings it forward and re-checks per character, but a person
  clicking elsewhere mid-recipe aborts the run with a refusal.
- `open`: a count read-back does not settle whether a restored iOS selection
  still enforces; `IOS-001` owns that question.

## Final

- **Status:** `done`
- **Outcome:** On a provisioned Mac a verification run needs no human step for
  the application picker, and `doctor` names every one-time provisioning
  condition with a remedy before a run reaches a feature recipe. On a
  provisioned iPhone the picker can be replaced by seeding a captured
  selection; the consent alert remains the one human step, by platform design.
