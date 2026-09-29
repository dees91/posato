# `SCHEDULE-003`: Decide pause sets for manual sessions and schedules

- **Review tier:** High-risk
- **Tier reason:** Settles new mandatory format-1 operations, a data
  migration on linked devices, and older-client compatibility (ADR 0006).
- **Dependencies:** `SCHEDULE-005` (merged in #108); release 1.3, wave 2.
  `SCHEDULE-004` delivers what this row decides.
- **Integration group:** PR-BLOCKLIST-DECISION
- **Authority:** [Release roadmap](../release-roadmap.md) revision 13, row
  `SCHEDULE-003`; [accepted product scope](../../product/blocklists.md);
  maintainer named the row on 2026-09-29.

## Outcome

Accepted product, design, and architecture decisions for reusable named
pause sets in manual sessions and schedules, and a delivery plan for
`SCHEDULE-004`.

## Accepted decisions (`user-confirmed`, 2026-09-29)

- **Name.** The feature is **Pause sets** ("Pause set", "New set",
  "Set: Work"); the planned Polish copy is "zestaw". It replaces the working
  name "blocklist" in the product, design, and roadmap text.
- **Live edits.** An item added to a set that a running pause uses is paused
  at once. An item removed from it stays paused until that pause ends.
- **Deletion.** A set that a schedule or a running pause uses cannot be
  deleted; the UI names its users and offers to reassign them first. The
  default set cannot be deleted.
- **Overlap.** A manual session and a scheduled occurrence with different
  sets pause the union of both, as overlapping schedules do. Each part ends
  at its own time, and **End early** still ends everything.
- **Default.** One default set, synchronized across the workspace.
- **Limits.** At most 10 sets. The existing website limit counts unique
  websites across all sets.
- **Readiness.** A pause starts with the set's websites when this device has
  no application choices for the set, and shows that applications are not
  chosen here, with a route to choose them. A set with no websites and no
  local applications cannot start.
- **Compatibility.** The 1.3 migration publishes the set operations at once.
  A device still on 1.2 stops syncing until it updates, as 1.1 did for
  schedules in 1.2.

## Boundaries

- Still to decide and propose: the ADR 0006 amendment (set, default, and
  reference operations; session and schedule references; reduction), a
  migration identity that two linked devices derive without duplicates, how
  a received session resolves its set, the `DESIGN.md` screens and copy, and
  the `PRIVACY.md` and privacy-manifest update for synchronized set names.
- Application choices stay device-local per set; no wire format carries them.
- Escalate every new product choice to the maintainer; the decisions above
  are not reopened without a reason.

## Acceptance

- `AC-01` — The product scope, `DESIGN.md`, and the roadmap use the accepted
  name and state every decision above.
- `AC-02` — A proposed ADR 0006 amendment passes an independent security
  review and is accepted by the maintainer.
- `AC-03` — A `SCHEDULE-004` delivery plan names its migration, verification
  on Tart and the test iPhone, and its maintainer gates.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Document consistency across the product scope, `DESIGN.md`, ADR 0006, and
  `PRIVACY.md`; golden-vector and migration checks belong to `SCHEDULE-004`.

## Decisions or blockers

- None beyond the proposals above.
