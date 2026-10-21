# `TARGETS-007`: Export pause sets and schedules to a file and import them on any device

- **Review tier:** High-risk
- **Tier reason:** A new file format that leaves the device, optional
  encryption with a code a person reads aloud, and an import that authors
  many set and schedule changes at once, including removals in replace
  mode, which reach every linked device through sync. A brief independent
  plan review comes before the implementation.
- **Dependencies:** `TARGETS-012` (merged in #177); release 1.5, wave 4b.
  `DOCS-006` and `RELEASE-007` wait for this row.
- **Integration group:** PR-EXPORT-IMPORT
- **Authority:** [Release roadmap](../release-roadmap.md) revision 26, row
  `TARGETS-007`; idea 14 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md); the clickable
  mockup accepted on 2026-10-21 (`user-confirmed`); [PRIVACY.md](../../../PRIVACY.md);
  [DESIGN.md](../../../DESIGN.md);
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  and [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md)
  for how authored changes sync.
- **Record:** [execution record](../executions/targets-007-export-import.md)

## Outcome

A person exports their pause sets (names and websites), the default set,
and their schedules to one `.posato` file, optionally protected by a code
Posato shows once, and imports that file on any Posato device, for example
a Mac on another Apple Account. Import shows what the file holds and lets
the person add it (combining sets with the same name, or keeping them
separate) or replace their sets and schedules. It is a one-time copy, not
sync: nothing stays connected. App choices stay on each device.

## Boundaries

- Every platform: Mac, iPhone, Linux, and Android, from the Pause sets menu
  (**Export pause sets…**, **Import…**), as in the mockup.
- The file never holds app choices, sessions, history, device names,
  workspace data, or keys. iPhone app choices are opaque tokens and cannot
  move; desktop and Android app choices stay local by the same rule.
- Imported changes go through the existing set and schedule authoring, so a
  linked device queues them like hand edits and every member receives them;
  replace mode removes sets and schedules for every member too, and the
  import screen says so.
- The workspace's 10-set and 10-schedule limits and the domain limits hold;
  an import that would exceed them is refused before anything changes.
- Non-goals: opening a `.posato` file from Files, Finder, or a share
  extension (import starts in Posato), exporting app choices, merging
  website lists across differently named sets, sync of any kind, and
  `TARGETS-008` (quick sharing).

## Acceptance

- `AC-01`: Format tests written first and shown failing: a plain and a
  coded file round-trip; a wrong code, a damaged file, a file over 1 MiB, an
  unknown format version, invalid domains, and over-limit contents are each
  refused with their own message and change nothing.
- `AC-02`: Import planner tests written first: add-and-combine,
  add-and-keep-separate (`Name (imported)`), and replace, including the
  unnamed first set, the default set, schedules that point at imported
  sets, duplicates, and the limits.
- `AC-03`: On a linked device, an import queues the same intents as the
  equivalent hand edits (shared test), and another member receives them on
  a real device pair.
- `AC-04`: On real devices: a coded export from a Mac in a Tart clone
  imported on the test iPhone; an Android export imported on a Linux clone
  in each add mode; a Linux export imported on Android with replace.
- `AC-05`: `PRIVACY.md` and `DESIGN.md` describe export and import; the
  `DESIGN.md` entry is `user-confirmed` from the mockup.

## Verification

- `shared` tests: the codec, the code key derivation (published test
  vectors), the planner, and the authoring path.
- `posato-control` first (tool-first): `flow export` and `flow import` on
  every target, and moving the file between targets from the host.
- Device runs as in `AC-04`, cited by run id on the final head.

## Decisions or blockers

See the execution record's open decisions D1 to D5 for the maintainer.
