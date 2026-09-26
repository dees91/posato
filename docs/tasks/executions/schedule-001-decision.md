# Execution: `SCHEDULE-001`

- **Brief:** [Decide shared recurring schedules and automatic Mac starts](../specifications/schedule-001-decision.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude, under the maintainer's delegated night mandate (2026-09-26)
- **Reviewer:** independent agent (security review of the amendments)
- **Branch:** `docs/schedule-001-decision`
- **Updated:** 2026-09-27

## Plan

1. Read the product scope, `DESIGN.md`, ADR 0002, 0004, 0006, and 0009, and
   the sync, iPhone, and Mac code where feasibility is uncertain.
2. Decide each open rule for the simplest predictable behavior.
3. Propose the ADR amendments and update `PRIVACY.md`.
4. Write the `SCHEDULE-002` plan.
5. Get an independent security review and fold the result.

## Result

- [Schedule rules](../../product/schedules-decisions.md) record the
  decisions and the plan:
  - each device's local wall clock;
  - weekdays belong to the start;
  - intervals of 15 minutes to under 24 hours;
  - one pause at a time, where **End early** ends everything restricting;
  - occurrences identified by schedule and local date;
  - grow-only skip and end facts;
  - at most 10 schedules;
  - notifications with `NOTIFY-001`;
  - the migration of `MACOS-014` grantees through the upgrade offer, with
    no second password.
- Code facts, all `observed`:
  - a 1.1 replica stops syncing on an unknown kind
    (`AppleMailboxExchange.acceptPage`, `RemoteBundleClassifier`);
  - the iPhone monitor extension reads no tokens or domains today;
  - Posato uses a 15-minute Device Activity minimum.

  They shaped the compatibility, optional-kind, and App Group decisions.
- Proposed amendments:
  - ADR 0006: kinds 8-11 and the optional kinds 128-255;
  - ADR 0004: automatic scheduled Apply through the unchanged daemon checks,
    with no reason code;
  - ADR 0009: the scheduled-start exception and the host triggers.
- `PRIVACY.md` covers synchronized schedules and the on-device App Group
  copy for the Screen Time extension. The extension's privacy manifest needs
  no change if the copy is a plain file with no timestamp APIs; `SCHEDULE-002`
  verifies this.

## High-risk plan review

- Not applicable: this is a discovery row that produces documents. The
  amendments get the independent security review below.

## Completed-change review

- **Verdict:** independent security review `PASS after Required fixes`;
  folded on 2026-09-27.
- **Required findings:**
  - **R1.** Accepted text contradicted the amendments. Each amendment now
    lists and rewrites the sentences it supersedes: ADR 0004 "Who may request
    it" and the PR #92 note; the ADR 0009 received-session, after-wake, and
    `SCHEDULE-001` approval sentences; and the ADR 0006 closed-kinds and
    unknown-kinds sentences. `RESUME_REQUIRED` for received sessions is now a
    user-experience rule, not a security control. A received manual session
    is not enforced by an occurrence, and it returns to `RESUME_REQUIRED`
    when the occurrence ends. Retries: at most one per trigger per
    occurrence.
  - **R2.** Consent could go stale. It is valid only while a flagged Status
    confirms the caller's grant. Revoke, Disable, Remove, and an absent or
    unknown grant clear it.
  - **R3.** Wire invariants were missing. `schedule-put` requires start
    different from end and a circular duration of at least 15 minutes,
    pinned by cross-target golden vectors in `SCHEDULE-002`.
- **Recommended, folded:**
  - local terminal markers per occurrence, and edits that may extend a
    running occurrence;
  - consent wording that names other devices, and upgrade-offer consent
    only by active acceptance;
  - a console owned by another account is neither "setup required" nor a
    notification;
  - optional kinds carry no restriction or integrity semantics, touch no
    mandatory state, and ignore invalid payloads deterministically, with
    byte limits before allocation;
  - NFC normalized by the writer, with decoders validating only UTF-8 and
    control characters;
  - `PRIVACY.md` covers backups and the deletion of the App Group copy.
- **Optional, folded:**
  - a 24-hour real-time cap on daylight-saving days;
  - skip and end dates at most 400 days ahead;
  - the downgrade rule for a 1.1 installation opening a 1.2 database.
- The linked-device residual is in the ADR 0004 amendment. Its "On
  acceptance" list adds it to threat model `T-07`.
- The amendments stay `proposed` until the maintainer accepts them.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Consistency read against authorities | pass | product scope, `DESIGN.md`, ADR 0002, 0004, 0006, 0009 |
| Independent security review | pass after Required fixes; folded | findings above |

## Blockers and accepted risks

- The maintainer must accept the proposed amendments before `SCHEDULE-002`
  ships automatic starts.
- Accepted for 1.2: a linked device still on 1.1 stops syncing once a
  schedule exists, until it updates.

## Final

- **Status:** `done`
- **Outcome:** met. The decisions are recorded and the amendments reviewed. The
  maintainer's acceptance of the amendments gates automatic starts in
  `SCHEDULE-002`.
