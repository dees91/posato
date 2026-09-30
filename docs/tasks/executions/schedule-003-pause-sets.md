# Execution: `SCHEDULE-003`

- **Brief:** [Decide pause sets for manual sessions and schedules](../specifications/schedule-003-pause-sets.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agent (security review of the amendment and
  completed-change review)
- **Branch:** `docs/schedule-003-pause-sets`
- **Updated:** 2026-09-30

## Plan

1. Read the product scope, 1.2 schedule rules, `DESIGN.md`, ADR 0006,
   `PRIVACY.md`, and the sync, storage, and host code the decisions touch.
2. Escalate the product choices the accepted decisions leave open.
3. Write the rules, the ADR 0006 amendment, the `DESIGN.md` section, the
   drafted `PRIVACY.md` edits, and the `SCHEDULE-004` plan; rename
   blocklists to pause sets.
4. Get an independent security review and fold the result.

## High-risk plan review

- Deviation: the brief's plan had no separate review, following the
  `SCHEDULE-001` precedent rather than `docs/tasks/README.md`. The
  independent review below read the proposed documents before any
  acceptance or implementation, which is the action the tier guards.

## Result

- [Pause set rules](../../product/pause-sets-decisions.md) record the
  decisions and the plan; the [scope](../../product/pause-sets.md) is renamed
  from `blocklists.md`, and the roadmap, wiki, and `DESIGN.md` use the name.
- Maintainer decisions on 2026-09-30 (`user-confirmed`): a missing set is
  empty; a running part never releases an item early; manual start during
  a scheduled pause stays unavailable; **Pause sets** replaces **Paused
  items** as a destination with a list root; a device that links or
  upgrades after the first set was deleted loses that set's local items,
  with no extra rule, documented internally only.
- Code facts, all `observed`, that shaped the proposal:
  - the application-group name is always "Applications" and only gates
    readiness; enforcement already ignores it, and kind 5 is never authored;
  - enforcement resolves current state; the start summary is display only;
  - the Mac helper holds one configuration and nothing forms a union;
  - iOS uses two named stores and one global item list in the App Group
    table;
  - a 1.2 replica stops on an unknown kind with "Sync needs attention".
- Proposed ADR 0006 amendment: kinds 12-19, the all-zero first set as the
  duplicate-free migration identity, a migration that publishes only
  `pause-sets-enabled`, and local per-part retention.
- `PRIVACY.md` is not edited: it publishes to `posato.app` from `main`, so
  the drafted edits ship with the release. Two `open` findings go to
  `SCHEDULE-004`: the policy overstates backups of the App Group copy, and
  the monitor extension's manifest may need the file-timestamp reason.

## Completed-change review

- **Verdict:** independent security and completed-change review `PASS
  after Required fixes`; folded on 2026-09-30. A focused re-review of the
  fixes found one more Required wording fix (host limits count all running
  parts together), folded the same day.
- **Required findings:**
  - **R1.** Retention had no real bound, and an overflow would clear the
    Mac helper. Current and retained items must fit host limits; an
    addition beyond them is not applied and the applied configuration stays.
  - **R2.** Retention now ends whenever the part stops for any reason.
  - **R3.** Retention is recorded no later than the request, and by the
    iPhone extension for parts it starts, instead of one transaction with
    enforcement.
  - **R4.** One default fallback in both documents, including no live set.
  - **R5.** A 1.2 device stops receiving but still publishes; the effects
    and a Tart check are recorded.
  - **R6.** The downgrade claim covers only databases holding kinds 12-19.
  - **R7.** Migration tests and review use synthetic 1.2 data only.
- **Recommended, folded:** liveness before domains and the first set's
  slot; complete Supersedes list; a refused-set status; retention for parts
  running at migration; linking keeps workspace names and default; threat
  model `A-01` on acceptance; Delete disabled while in use; the onboarding
  linking copy; the acceptance gate; plan coverage and a `SCHEDULE-004`
  plan review.
- **Optional, folded:** kind 19 once per database; precise `PauseClaims`
  wording; per-part edit copy.
- **Maintainer review (PR #115, 2026-09-30):** one P1, accepted. The 1.3
  iPhone extension can run before the app writes a version-2 table; it now
  reads a valid version-1 table as the first set instead of clearing, and
  the plan adds an upgrade-without-opening-the-app run on the test iPhone.
  The correction's Standard review added one Required rule, folded: the
  app deletes the version-1 table with its first version-2 commit, and a
  version-2 table that is unreadable never falls back to version 1.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Consistency read against authorities | pass | scope, rules, `DESIGN.md`, ADR 0006, roadmap, wiki |
| Independent security review | pass after Required fixes; folded | findings above |
| Focused re-review of the fixes | pass after one Required fix; folded | host limits across parts |

## Blockers and accepted risks

- The maintainer accepted the ADR 0006 amendment on 2026-09-30
  (`user-confirmed`, `AC-02`); threat model `A-01` and `A-03` now name pause
  sets.
- Accepted for 1.3 (`user-confirmed` 2026-09-29): a linked device still on
  1.2 stops receiving once another device migrates, until it updates.

## Final

- **Status:** `done`
- **Outcome:** met. `AC-01` the name and decisions are stated across the
  scope, rules, `DESIGN.md`, and roadmap; `AC-02` the amendment passed the
  security review and is accepted; `AC-03` the `SCHEDULE-004` plan names
  its migration, Tart and test-iPhone verification, and maintainer gates.
