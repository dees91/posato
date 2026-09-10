# Execution: `ONBOARDING-002`

- **Brief:** [Second install joins and waits](../specifications/onboarding-002-second-install.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** pending handoff to an implementing agent
- **Reviewer:** plan reviewer and completed-change reviewer pending
- **Branch:** `feature/onboarding-002-second-install`
- **Updated:** 2026-09-10

## Plan

1. Confirm the `observed` facts in the brief against the current sources:
   a found anchor with a missing item returns waiting without persisting,
   foreground does nothing for an unlinked device, and the Session row and
   iCloud step render waiting from the shared status.
2. Add the join-only re-check to the bootstrap coordinator per `D1`: read
   zone, anchor, and item under the binding check; never save or delete;
   return local-only when the anchor is missing. Extend `BootstrapResult`
   only as far as that mapping needs. Cover it in `BootstrapCoordinatorTest`
   with call counts over the fakes.
3. Add the `D1` foreground opportunity in `AppleSync` for a process that
   holds `WAITING_FOR_KEY`, keep the linked and no-consent branches unchanged,
   and cover it in `AppleSyncTest` through the harness.
4. Apply `D2`: the waiting notice, the **Check again** and **Continue**
   labels in the iCloud step, the summary's waiting line, and the Session
   row's expanded label; update previews and the onboarding holder tests.
5. Record `D3` in the summary and skill copy without new controls; state the
   `SYNC-011` limit in this record.
6. Update `DESIGN.md`, the ADR 0007 clarifying sentence, the `verify-posato`
   sync and onboarding recipes (join rows, fresh-database cost, fallback),
   the two wiki topics, and the wiki log at closeout.
7. Run focused tests, `./gradlew quality`, the Simulator fixtures, and the
   physical directions A and B under `D4`; write the threat-model closeout
   statement; obtain the independent completed-change review.

## High-risk plan review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

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
| `AppleSyncTest` waiting-device foreground and no-consent cases | pending | |
| Onboarding holder, summary, and Session row waiting rendering | pending | |
| `./gradlew quality` | pending | |
| Simulator `first-install.json` and `first-install-skip.json` | pending | |
| Physical direction A: Mac establishes, iPhone joins fresh | pending | |
| Physical direction B: iPhone establishes, Mac joins fresh | pending | |
| Post-join permission and local selection, Mac counts unchanged | pending | |
| Threat-model closeout statement | pending | |

## Blockers and accepted risks

- Maintainer decisions `D1` to `D4` in the brief precede implementation.
- The waiting window depends on Apple-timed iCloud Keychain propagation; the
  brief names the attended fallback and the recorded limit if it is declined.
- Synced domains, policies, and applications stay invisible on the joining
  device until `SYNC-011`; this task claims nothing about them.

## Final

- **Status:** pending
- **Outcome:** pending
