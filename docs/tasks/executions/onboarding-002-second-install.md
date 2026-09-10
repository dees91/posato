# Execution: `ONBOARDING-002`

- **Brief:** [Second install joins and waits](../specifications/onboarding-002-second-install.md)
- **Status:** `planned`; implementation pending
- **Review tier:** `high-risk`
- **Implementer:** pending handoff to an implementing agent
- **Reviewer:** independent revised-plan review approved; completed-change
  reviewer assigned after implementation
- **Branch:** `feature/onboarding-002-second-install`
- **Updated:** 2026-09-10

## Plan

1. Apply the accepted D1 amendment to ADR 0007 before changing behavior;
   update DESIGN.md for D2. Confirm the fresh-join, candidate, and established
   waiting paths against the code. Retain the existing consent/bootstrap
   protocol and the state-specific recovery routes outside the fresh join.
2. Add a bounded fresh-join continuation with process-memory account binding
   and anchor context from the consented attempt. Recheck local state and
   account under serialization; use the brief's outcome table. While waiting,
   read only. On a valid item, commit the established row before requesting
   normal exchange. Cover both forbidden effects and successful adoption.
3. Route manual Check again and foreground through that continuation,
   coalescing overlapping opportunities and rejecting stale continuations.
   Keep no-consent foreground and linked exchange behavior intact. Test
   account/workspace changes, loss, delayed delivery, store failures,
   cancellation, candidate/linked recovery, and one adoption/exchange.
4. Apply D2: Continue primary, Check again secondary for the fresh join;
   short waiting status and separate explanation; distinct local-save and
   sync facts in the summary; collapsed Session iCloud row. Verify manual
   progress/completion through the existing Mac announcement bridge, with
   narrowly scoped application/Session callback wiring; avoid repeated
   automatic wait announcements.
5. Update the verification recipes and wiki synthesis with the implemented
   behavior and D3's SYNC-011 limit. Run focused checks, full quality,
   unchanged first-install Simulator fixtures, and attended physical joins
   in both directions. Record unobserved waiting separately from successful
   immediate joining and get agreement for optional settings changes.
6. Obtain the independent completed-change review, resolve Required findings,
   rerun affected checks, and complete this record. Append one wiki-log entry
   at PR closeout; no per-correction logs or new execution records.

## High-risk plan review

- **Initial verdict:** `changes-required` (independent reviewer, 2026-09-10).
  Initial findings were addressed in `d2d7635`; the revised plan below
  supersedes its foreground-only continuation and original button hierarchy.
- **Critical or Required findings:** the join-only re-check was undefined
  for a persisted candidate and would have deleted the own key item on an
  automatic path; zone-missing, retryable, and account outcomes were
  undefined, so a foreground could publish retryable over a true wait; the
  binding check had no referent for a store-`None` device, leaving an
  account switch able to join the new account's workspace without a press;
  `AC-01` promised Mac database evidence in the direction where the waiting
  device is the iPhone; the verification asked for rendering and label
  tests that the quality contract forbids.
- **Resolution:** the fresh-join continuation is scoped to store `None`,
  retains the consented account and anchor in memory, and uses a defined
  outcome table. Candidate/established recovery remains separate. Evidence
  names the actual waiting device; tests target behavior, not static copy.
- **Maintainer correction, accepted 2026-09-10:** manual Check again shares
  the bounded continuation; no creating bootstrap hides behind that label.
  Reads while waiting are distinguished from established persistence and
  exchange after adoption. Continue is primary; Session keeps disclosures;
  local saves and pending sync remain separate. Both physical device orders
  are required, but unobserved waiting is an evidence limit. No particular
  Apple prompt is assumed. D1–D4 are accepted; implementation is pending.
- **Revised-plan verdict:** `approve` (independent reviewer, 2026-09-10);
  zero open Critical/Required and no optional findings. One Required gap in
  the accessibility write surface was resolved by allowing narrow callback
  wiring to the existing Mac announcement bridge.
- **Review evidence:** brief continuation/outcomes (lines 95–151), UI/write
  surface (152–202), acceptance/verification (215 onward), execution plan,
  and both changed wiki topics; checked against AppleSync, coordinator,
  anchor/item phases, store mapping, UI wiring, ADR 0007, and DESIGN.md.
  `git diff --check` passed. No builds/runtime tests: documentation-only
  plan review; implementation verification remains pending below.

## Result

- Pending implementation.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `BootstrapCoordinatorTest` join-only re-check cases | pending | |
| Manual/foreground continuation, stale context, and no-consent tests | pending | |
| Holder routing and native waiting presentation/accessibility | pending | |
| `./gradlew quality` | pending | |
| Simulator `first-install.json` and `first-install-skip.json` | pending | |
| Physical direction A: Mac establishes, iPhone joins fresh | pending | |
| Physical direction B: iPhone establishes, Mac joins fresh | pending | |
| Post-join permission and local selection, Mac counts unchanged | pending | |
| Threat-model closeout statement | pending | |

## Blockers and accepted risks

- D1–D4 and the revised plan are accepted; no preparation blocker remains.
  The ADR amendment is the first implementation step.
- Automatic checks do not repeat unchanged waiting announcements. Manual
  progress/completion must remain observable even with the same outcome.
- Waiting is not persisted; relaunch requires explicit consent again. This
  iteration accepts that weaker UX without dismissing durable continuation.
- The waiting window depends on Apple-timed iCloud Keychain propagation; the
  brief names the attended fallback and the recorded limit if it is declined.
- Synced domains, policies, and applications stay invisible on the joining
  device until `SYNC-011`; this task claims nothing about them.

## Final

- **Status:** pending
- **Outcome:** pending
