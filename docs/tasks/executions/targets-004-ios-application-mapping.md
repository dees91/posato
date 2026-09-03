# Execution: `TARGETS-004`

- **Brief:** [Associate opaque device-local iOS application selections](../specifications/targets-004-ios-application-mapping.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** independent Codex reviewer
- **Branch:** `feature/targets-004-ios-application-mapping`
- **Updated:** 2026-09-03

## Plan

1. Update the shared mapping and authorization contracts while preserving the
   macOS behavior and keeping native labels and tokens in Swift.
2. Implement the injected iOS adapter, native picker coordinator, protected
   app-private store, development entitlement, and focused automated tests.
3. Run local quality, XCTest, unsigned Debug device, and Release Simulator
   builds while hosted CI remains paused.
4. Stop for the maintainer's local team/provisioning action, then run the
   physical authorization, restart, revoke, reinstall, and mapping checklist.
5. Complete the independent review, affected reruns, durable documentation,
   and any maintainer-requested hosted review under the repository budget.

## High-risk plan review

- **Verdict:** `approved`
- **Critical or Required findings:** none after maintainer corrections and
  independent re-review
- **Resolution:** The plan reads live authorization state, uses Foundation's
  atomic write directly, adds the manual provisioning gate and local CI-pause
  matrix, and removes duplicate unavailable and presentation representations.

## Result

- Implemented the shared opaque mapping and live authorization contract, the
  cancellable Kotlin/Swift adapter, the native Family Controls picker, and the
  bounded app-private protected store.
- Enabled Family Controls only for Debug device builds. Simulator and Release
  builds expose an explicit unavailable state and retain valid local mappings.
- Signed physical-device verification is complete with the development Family
  Controls capability. Selection save, cancellation, clearing, process restart,
  authorization revocation and recovery, and reinstall behavior matched the
  accepted contract.
- Rebased-branch verification was rerun at `013f616` through the
  `verify-posato` driver: losing Screen Time access while the picker is open
  retains the selection and reports the access state truthfully instead of
  failing. The verification map now carries an iOS mappings recipe and the
  boundary between drivable and maintainer-only steps.

## Completed-change review

- **Verdict:** `approved after corrections`
- **Critical findings:** none
- **Required findings:** picker termination races; unsupported picker input
  replacing the canonical set; incomplete authorization/error categorization;
  insufficient native persistence boundary tests; missing device and Release
  CI compile gates; missing numbered-mapping preview coverage
- **Resolution:** Added exactly-once cancellation and swipe termination,
  authorization rechecks and closed error mapping, application-only selection
  validation, normalized corruption and stricter persisted-token validation,
  native metadata and lifecycle tests, the two CI build gates, and numbered
  preview data. A physical regression check confirmed swipe/reopen and rejection
  of a category without losing the prior application selection. Correction
  re-review also removed post-Xcode-26.3 SDK symbols and added a typed retained-
  snapshot access-change result with native-boundary and ViewModel tests.
- **Final re-review:** no remaining Critical or Required findings
- **Follow-up review at `ffdbc06`:** found two small Required corrections: an
  authorization race returned a successful selection outcome, and five iOS
  Kotlin tests used nonconforming names. The maintainer also accepted removal
  of speculative persisted and shared slot numbers before merge.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | passed | 112 tasks; Xcode 26.6 local environment |
| iOS Simulator XCTest | passed | Xcode 26.6, iPhone 17 Pro / iOS 26.5 |
| Unsigned Debug Simulator build | passed | Xcode 26.6 |
| Unsigned Debug device build | passed | Xcode 26.6; Family Controls source path compiled |
| Unsigned Release Simulator build | passed | Xcode 26.6; capability unavailable by design |
| Signed Debug device build and install | passed | Xcode 26.6; automatic signing supplied only as a local command parameter |
| Initial authorization and picker presentation | passed | Screen Time consent preceded the native picker |
| Save, cancel, and clear selection | passed | Full-set save persisted; cancellation retained the prior set; clear removed the set atomically |
| Process restart | passed | Authorization and the selected application remained visible without another prompt |
| Revoke and restore authorization | passed | Revocation produced authorization-required state while retaining the selection; reauthorization reopened the picker with the prior selection |
| Uninstall and reinstall | passed | App-private group and selection were removed; consent was required again and the picker reopened empty |
| Post-review picker regressions | passed | Swipe dismissal allowed immediate reopen; unsupported category save was rejected while retaining the prior application |
| Physical iPhone native store XCTest | passed | Eight tests, including complete file protection and backup exclusion metadata |
| Correction review (`b8764a1`) | approved | Independent completed-change review; no Critical or Required findings |
| Correction scope and privacy scan | passed | 12 files; `git diff --check` clean; no Team, device, profile, or personal paths; `swiftc -parse` clean on both Swift files |
| Push and PR #16 refresh | passed | `push --force-with-lease` moved the remote branch to `1f51172`; PR #16 reports `MERGEABLE`/`CLEAN` |
| `./gradlew quality` at `013f616` | passed | Xcode 26.6; 129 actionable tasks including `shared:iosSimulatorArm64Test` |
| Simulator XCTest at `013f616` | passed | Xcode 26.6, iPhone 17 Pro / iOS 26.5; 16 cases (9 mapping store, 7 CryptoKit) |
| Credential-free Debug Simulator build at `013f616` | passed | Xcode 26.6; matches the CI gate command |
| Credential-free Debug device build at `013f616` | passed | Xcode 26.6; Family Controls source path compiled |
| Credential-free Release Simulator build at `013f616` | passed | Xcode 26.6; capability unavailable by design |
| Scope and privacy scan at `013f616` | passed | `git diff --check` clean; no team, device, profile, or personal-path values in the diff |
| PR #16 head at `013f616` | passed | PR head matches local `HEAD`; `MERGEABLE`/`CLEAN`; hosted CI remains manual-only |
| Device driver run through `posato-control` | passed | Signed Debug device build, install, launch, group creation, and the access and empty states rendered as specified |
| Physical authorization loss before `Save` | passed | Picker reopened with one selection, Screen Time access revoked in Settings while it stayed open, then `Save`: the picker closed, `Applications selected: 1` was retained, the access sentence returned, and the action became `Allow and review applications` with no failure message |
| Relaunch read-back after the access change | passed | The retained selection and the access sentence both survived a process restart with authorization revoked |
| Clear and group removal after the access change | passed | `Clear selection` reached `No applications chosen on this device.` and group removal reached `No application group yet.`, restoring the device to its post-install state |
| `./gradlew quality` after review corrections | passed | Xcode 26.6; 126 tasks including `shared:iosSimulatorArm64Test` with the new outcome-mapping tests |
| Build matrix and Simulator XCTest after review corrections | passed | Xcode 26.6; Debug Simulator, Debug device, and Release Simulator all built and XCTest succeeded; the corrected Swift branch compiles in every configuration |

## Local review after the hosted budget became unavailable

- **Verdict:** `approved after corrections`
- **Reviewer:** independent local reviewer (hosted `@codex review` is
  unavailable for several days, so the maintainer directed a local pass)
- **Critical findings:** none
- **Required findings:** transient Family Controls errors were mapped to the
  compile-time `unavailable` outcome, producing a sticky and factually wrong
  "unavailable in this build" state with no visible retry; the shared empty
  mapping string was renamed in this change while four assertions in the macOS
  verification page, two of them executable driver commands, still quoted the
  old copy; `DESIGN.md` still described the pre-change copy for the
  unavailable state.
- **Resolution:** Unenumerated authorization errors now return the picker
  failure outcome, which keeps the capability available, preserves the prior
  selection, and leaves the retry action visible. The macOS and application
  group verification pages quote the current copy. `DESIGN.md` now separates
  "no producer in this build" from "nothing chosen yet". Added iOS adapter
  tests pinning the cancelled, capacity, invalid, picker, storage, and
  unavailable selection outcomes.
- **Recommended and Optional findings** were recorded and not actioned; they do
  not expand scope automatically.

## Blockers and accepted risks

- The signed device build used the maintainer's local Xcode account and an
  ephemeral team parameter. No private signing value is recorded.
- On the tested device, revocation and reinstall both returned authorization to
  the not-determined state; an ordinary process restart preserved approval.
- Hosted CI is manual-only through 2026-09-05. Local Xcode 26.6 verification is
  the active gate; the workflow retains Xcode 26.3 compatibility coverage.
- The rebased correction is reviewed and approved. Its `./gradlew quality`,
  Simulator XCTest, and three-configuration build matrix were rerun at
  `013f616` and passed.
- The corrected transient-authorization-error branch is covered by compilation
  and adapter tests but was not reproduced on the device, because it needs a
  Screen Time service failure that cannot be induced reliably.
- Losing authorization while the picker is open is not an error state: the
  selection is retained, the access sentence returns, and the action becomes
  `Allow and review applications`. Revocation again left authorization
  not determined rather than denied on the tested device.
- The Screen Time consent alert belongs to SpringBoard and the Family Controls
  picker list is rendered out of process, so neither appears in the driver's
  accessibility tree. Only the app's own `Save` and `Cancel` toolbar buttons
  and the resulting screen state are drivable; granting access, selecting an
  application, and revoking access in Settings stay maintainer steps. The
  verification map records this limit.
