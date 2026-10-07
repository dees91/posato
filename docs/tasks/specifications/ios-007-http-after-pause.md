# `IOS-007`: Plain HTTP pages load in Safari after an iPhone pause ends

- **Review tier:** Standard
- **Tier reason:** A defect in how the iPhone leaves a pause; the expected
  change stays inside Posato's own Managed Settings store and its clear or
  reconcile path, with no new entitlement, privilege, persistence format, or
  synchronization change. Escalate to High-risk if the fix needs any of them.
- **Dependencies:** None; release 1.4, wave 1. `RELEASE-006` waits for this
  row.
- **Integration group:** PR-IOS-HTTP-AFTER-PAUSE
- **Authority:** [Release roadmap](../release-roadmap.md) revision 19, row
  `IOS-007`; the maintainer named the row on 2026-10-07.
- **Record:** [execution record](../executions/ios-007-http-after-pause.md)

## Outcome

After a pause that blocks websites ends on the iPhone, Safari loads plain
`http://` sites without Posato in front, or the behavior is shown to be an iOS
limit that Posato cannot avoid and is recorded as one.

## Boundaries

- Start from idea 31 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md): a black page for
  every `http://` site after the pause ends, HTTPS unaffected, Posato in front
  avoids it, and nil-before-`clearAllSettings()` did not help.
- Reproduce on the test iPhone with neither the `posato-control` UI driver nor
  its test runner active while Safari loads, so that the driver is ruled in or
  out as a cause.
- Compare the ways a pause ends: **End early** in the app, natural expiry with
  Posato in front, and expiry cleared by the activity monitor extension while
  Posato is suspended or closed.
- A fix keeps the accepted iPhone contracts in
  [iOS enforcement](../../wiki/topics/ios-enforcement.md): only Posato's named
  store is written or cleared, clear stays idempotent, and suspended expiry
  and schedule behavior do not change.
- Non-goals: the Mac, HTTPS behavior, what a pause blocks, a Network
  Extension or other new entitlement, and any record of browsing.
- Do not make `observe-unblocked-ios.json` pass by switching it to HTTPS or by
  bringing Posato to the front unless the limit is recorded and accepted.

## Acceptance

- `AC-01` — A reproduction on `main` without the driver, with its steps,
  build revision, and evidence directory in the execution record.
- `AC-02` — The cause, or the narrowest observed condition, is recorded with
  provenance labels in the iOS enforcement wiki page.
- `AC-03` — Fix path: with the change, the `AC-01` steps load
  `http://example.com` with Posato not in front for every end path above, and
  `observe-unblocked-ios.json` after a session passes three runs in a row.
- `AC-04` — Limit path instead of `AC-03`: the maintainer accepts the limit,
  the [limits page](../../product/limits-and-platforms.md) states it, and the
  verification feature map describes the adjusted scenario.
- `AC-05` — Blocking during a pause and the lifting of app shields at its end
  still work on the test iPhone.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Test-iPhone runs (`-t device`) of the `AC-01` steps before and after the
  change, `observe-unblocked-ios.json` three times, and a blocked-site and
  shielded-app check during a pause for `AC-05`.
- A Swift test only if a credible failure needs isolation, written failing
  first under the testing policy.

## Decisions or blockers

- Whether a recorded iOS limit is acceptable is the maintainer's decision
  when the evidence points there.
