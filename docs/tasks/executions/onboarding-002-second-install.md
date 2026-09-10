# Execution: `ONBOARDING-002`

- **Brief:** [Second install joins and waits](../specifications/onboarding-002-second-install.md)
- **Status:** `active`; implementation reviewed, physical verification pending
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** independent revised-plan and completed-change reviews approved
- **Branch:** `feature/onboarding-002-second-install`
- **Updated:** 2026-09-10

## Plan

1. Amend ADR 0007 and DESIGN.md using accepted D1/D2 before behavior changes.
2. Add the process-local fresh-join continuation and bounded outcome table;
   preserve candidate/established recovery and test forbidden effects.
3. Share continuation routing between manual and foreground checks, coalesce
   overlaps, and test adoption, changed context, failures, and cancellation.
4. Apply the waiting UI hierarchy and existing native announcement bridge;
   preserve separate local-save/sync facts and collapsed Session controls.
5. Update recipes/wiki; run focused checks, full quality, Simulator fixtures,
   and attended physical joins in both orders with agreed cleanup.
6. Resolve independent review findings, rerun affected checks, and close out
   the record with one wiki-log entry when verification is complete.

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

- Implemented a process-local fresh-join continuation shared by manual retry
  and foreground. It retains consented binding/anchor context, reads only
  until verified adoption, and reconciles interrupted persistence before retry.
- Continue remains primary while waiting; Check again is secondary. Session
  keeps its collapsed status row, and the summary separates local saves from
  sync state. Manual checks use the existing Mac announcement bridge.
- Updated ADR 0007, DESIGN.md, verification recipes, and wiki synthesis.
  PoC bootstrap/evidence informed the checks; no experiment code was imported.
- Threat-model review: no new store, key format, transport, or membership
  boundary. TB-07/T-04/R-04 still apply; accepted consent timing is explicit
  in ADR 0007. Physical delivery evidence remains pending.

## Completed-change review

- **Verdict:** `approve` (independent reviewer, 2026-09-10); zero open
  Critical/Required, no additional findings.
- **Required finding:** missing regression evidence for persistence followed
  by cancellation and the subsequent UI retry route.
- **Resolution:** tests now cover persisted adoption/cancellation, exact and
  conflicting row reconciliation, one commit/exchange, and bounded retry
  after a storage error despite retryable presentation. Focused tests pass;
  reviewer confirmed closure. No production-code defect was found.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `BootstrapJoinTest` bounded checks and outcomes | passed | JVM regressions |
| Manual/foreground, stale context, no consent, cancellation | passed | `AppleSyncJoinTest`, `AppleSyncTest` |
| Holder routing / native waiting presentation | tests passed / physical pending | JVM holder tests |
| `./gradlew quality` | passed after final test correction | JVM/iOS tests, lint, packaging |
| Simulator full / skip first-install fixtures | passed | UI captures; website/bootstrap/completion counts 1/0/1 and 0/0/1 |
| Signed Mac launch and collapsed/expanded Session | passed | Driver snapshot and screenshots |
| Physical direction A: Mac establishes, iPhone joins fresh | pending | |
| Physical direction B: iPhone establishes, Mac joins fresh | pending | |
| Post-join permission and local selection, Mac counts unchanged | pending | |
| Threat-model closeout statement | reviewed | Existing TB-07/T-04/R-04; ADR 0007 amendment |

## Blockers and accepted risks

- Physical joins require attended agreement on concrete workspace cleanup;
  no workspace has been removed during implementation verification.
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
