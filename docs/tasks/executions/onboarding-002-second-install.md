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
2. Add the bounded join-only re-check per `D1` as its own coordinator entry
   point with its own sealed result: store `None` only, current binding
   equal to the retained attempt binding, exact reads, no save, delete, or
   persist, the outcome table of the brief. Cover every row in
   `BootstrapCoordinatorTest` with the fake counters; the press cases stay
   untouched.
3. Add the `D1` foreground opportunity in `AppleSync` behind its own
   flight-exclusive wrapper (not `syncNow()`, not `guarded`), retaining the
   attempt binding with `WAITING_FOR_KEY` in memory, publishing only a
   changed definitive outcome so the announcing notice speaks once; keep the
   linked and no-consent branches unchanged; cover it in `AppleSyncTest`.
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

- **Verdict:** `changes-required` (independent reviewer, 2026-09-10),
  resolved in the brief before handoff.
- **Critical or Required findings:** the join-only re-check was undefined
  for a persisted candidate and would have deleted the own key item on an
  automatic path; zone-missing, retryable, and account outcomes were
  undefined, so a foreground could publish retryable over a true wait; the
  binding check had no referent for a store-`None` device, leaving an
  account switch able to join the new account's workspace without a press;
  `AC-01` promised Mac database evidence in the direction where the waiting
  device is the iPhone; the verification asked for rendering and label
  tests that the quality contract forbids.
- **Resolution:** the re-check is scoped to store `None`, keeps the attempt
  binding in process memory and requires equality, has a full outcome
  table, publishes only changed definitive outcomes, and uses its own
  wrapper and sealed result; `AC-01` names the evidence per direction;
  tests target holders and state mapping. Advisory items folded: the ADR
  0007 amendment is conditional on `D1` being `user-confirmed` and uses the
  dated amendment convention; the waiting sentence no longer states where
  Apple asks; a shared `action_check_again` string; the summary receives
  the status; `D3` anchored as `observed`; the `D4` costs (removal order,
  Mac backup consequence, keep-passwords prompt, no guaranteed Apple
  prompt) and the `D1`-shaped threat-model closeout are recorded. The
  reviewer confirmed the chain stays linear and that the `SYNC-010`
  follow-up shares this write surface, so it is serialised after.

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
- Implementation note: the iCloud step's waiting notice announces changes,
  so the re-check must not republish an unchanged status.
- The waiting window depends on Apple-timed iCloud Keychain propagation; the
  brief names the attended fallback and the recorded limit if it is declined.
- Synced domains, policies, and applications stay invisible on the joining
  device until `SYNC-011`; this task claims nothing about them.

## Final

- **Status:** pending
- **Outcome:** pending
