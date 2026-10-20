# Execution: `SESSION-010`

- **Brief:** [session-010-adopted-pause-items.md](../specifications/session-010-adopted-pause-items.md)
- **Status:** `done`
- **Review tier:** Standard
- **Implementer:** Claude Code agent, under the maintainer's 2026-10-20
  mandate to finish release 1.5 unattended
- **Reviewer:** independent agent
- **Branch:** `session-010-adopted-pause-items`
- **Updated:** 2026-10-20

## Plan

1. A failing exchange test: a device links during a peer's pause with a
   website set and must enforce those websites in the same pass.
2. Capture the adopted session's websites from the converged projection.
3. Device runs: a Linux clone and a Mac VM each join an Android pause.

## Result

- `SessionProjectionReconciler.apply` wraps the capture: when the session's
  set is live in the projection, the adopted frozen set takes its websites
  from there and keeps this device's own capture for the apps. Both
  adoption paths go through it, the session phase and the replica snapshot.
  A session started on this device never passes through adoption, and
  repeated delivery keeps the stored set as before.
- Two existing assertions expected the receiver's whole fixed capture. They
  now expect the workspace set's websites (none in those tests) with the rest
  of the receiver's capture.
- **Deviations** (`agent-delegated` under the mandate): `AC-02` has no new
  test. The fix leaves the pass order alone, and an accepted end is still
  committed in the session phase before the policy phase. The two-peer
  fixture cannot produce a capacity refusal: the authoring peer stops at the
  unknown-author bundle bound first. For `AC-03`, the Mac joined Android's
  folder workspace instead of an iCloud workspace. It runs the same exchange
  pass (`AppleSync.runExchange`), and iCloud's Development environment
  delivers in 1 to 10 minutes.
- The folder-sync feature map now names the guest's real folder,
  `/Users/posato/PosatoSync`.

## Completed-change review

- Independent agent, 2026-10-20: no Critical, one Required. A workspace
  set may hold up to 2,048 websites, but a stored frozen set must meet the
  local limit of 1,024, so adopting a larger set left the session store
  unreadable. Resolved test first (`SessionAdoptionCaptureTest`, red with a
  failed read): such a set keeps the local capture, which matches the
  policy phase's own refusal. Two Recommended were taken: the adoption
  comment, and an assertion that this device's app count survives.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| The new exchange test on `main` `ee7f9c59` | Failed as expected: `expected:<[first.example]> but was:<[]>` | `build/verification/e2e/session-010-red.txt` |
| `:shared:jvmTest`, Detekt, and ktlint with both fixes | Passed | gradle run of the session |
| Galaxy XCover7 (Android 14) starts a 60-minute pause with `example.org`; the Linux clone links and joins with the code during it | Linux wrote the block 6 s after joining and showed "Restrictions active" with 1 website. After the early end on the phone, it cleared in 67 s | `build/verification/e2e/session-010-folder.log`, runs `20261020-203041-4bf2`, `20261020-203100-6946`, `20261020-203608-4a69` |
| A Mac VM joins the phone's pause the same way | "Items at session start: 1 website"; after **Retry** `example.org` was blocked | `build/verification/e2e/session-010-mac.log`, runs `20261020-204510-5ccd`, `20261020-204709-5a6c` |
| `./gradlew quality` on the head | See the pull request's Verified comment | |

## Blockers and accepted risks

- On the Mac, an adopted pause still waits for **Retry**, or **Resume
  restrictions**, as `DESIGN.md` requires. `MACOS-029` (candidate for 1.6)
  revisits this.
