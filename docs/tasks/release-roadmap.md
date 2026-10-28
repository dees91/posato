# Posato Release Roadmap

## Status and authority

- **Status:** Accepted
- **Revision:** 36 (2026-10-28: `REDIRECT-001` joins the backlog)
- **Prepared:** 2026-09-18
- **Accepted:** 2026-09-18
- **Last amended:** 2026-10-28
- **Accepted by:** Project maintainer
- **Provenance:** `user-confirmed`; the maintainer accepted the three-release
  composition, the document form, and revision 1 on 2026-09-18. Revision 2
  restates the `TARGETS-006` outcome as a matching rule with one stored row
  per typed host, after the completed-change review of PR #72 rejected
  materialized `www` counterparts. Revision 3 adds the `QUALITY-010` backlog row
  from idea 10 (verification without the maintainer). At the maintainer's
  request it merges `QUALITY-006`, verifying actual blocking from the driver,
  into that row and deletes `QUALITY-006` from the backlog. It also adds the
  `PAUSE-001` backlog row from idea 11 (a useful moment on the pause page). It
  also adds the `I18N-001` backlog row from idea 12 (Polish as the first
  additional language). It also adds the `NAV-001` backlog row from idea 13
  (Navigation 3 and system back gestures). Preliminary rows
  `TARGETS-007` and `TARGETS-008` come from ideas 14 and 15 (file export and
  import, and sharing across Apple Accounts). Revision 4 adds the `MACOS-020`
  backlog row for a defect that `QUALITY-010` measurement `M5` reproduced:
  losing the network service that holds the proxy settings leaves a false
  active claim and a stale ownership record. Revision 5 adds the `SYNC-020`
  backlog row for the `QUALITY-010` observation that a session started on
  the iPhone reached iCloud only after a manual **Sync now**; a push path to
  the Mac stays outside it as a separate product decision. Revision 6 adds
  the `QUALITY-011` backlog row from idea 16 (a standalone verification tool
  from the reusable core of `posato-control`) and the `FILTER-003` backlog
  row from idea 17 (silencing notifications without blocking the
  application). Revision 7 clarifies the `PAUSE-001` outcome with the
  layered direction recorded in idea 11; its assignment condition is
  unchanged. Revision 8 adds `IOS-006` to release 1.2
  for a defect that `RELEASE-003` reproduced on the test iPhone: relaunching
  Posato during a session can end it as expired and lift its restrictions.
  The code path is unchanged since 1.0.0, so the maintainer chose to publish
  1.1 with the limit stated and fix it in 1.2, without a patch release
  (`user-confirmed`, 2026-09-25). Revision 9 adds the `MACOS-021` backlog
  row from idea 18, a transient helper CPU spike that `MACOS-012` measured. It
  also restates the `MACOS-019` condition, because ADR 0009 kept the root
  daemon (`user-confirmed`, 2026-09-26). Revision 10 revises release 1.2
  while it runs (`user-confirmed`, 2026-09-26, PR #92). It adds
  `ONBOARDING-004` from idea 19: one guided Mac setup for blocking, quiet
  login launch, and starts without repeated passwords. It moves
  `SCHEDULE-001` and `SCHEDULE-002` into release 1.2 and makes shared
  schedules a release gate. PR #92 supplies the UI shells; the authorization
  for automatic starts stays gated by the `SCHEDULE-001` security review.
  Revision 11 adds `DOCS-003` to release 1.2 at the maintainer's request
  (`user-confirmed`, 2026-09-27): the showcase media, README, `posato.app`,
  App Store text and screenshots, and release notes are prepared for 1.2
  before `RELEASE-004` publishes them. Revision 12 records the partial
  release 1.3 selection (`user-confirmed`, 2026-09-28): `NAV-001` is included;
  `SYNC-020`, `MACOS-020`, and `MACOS-021` are tentative candidates. The
  maintainer also accepted reusable blocklists for manual sessions and
  schedules in release 1.3. Idea 20 and `SCHEDULE-003` now cover the remaining
  blocklist decisions, with `SCHEDULE-004` delivering the accepted
  [product scope](../product/pause-sets.md). The remaining release scope and
  final waves will be decided at a later checkpoint. The same revision adds
  `MACOS-022` and `SCHEDULE-005` from reports 21 and 22 to the unassigned
  backlog; neither changes release 1.3 composition or authorizes a rule change.
  The 2026-09-29 clarification targets Intel discovery at macOS 13 Ventura,
  which the maintainer uses on the 2019 MacBook Air and chooses to retain.
  Compatibility and any ADR 0003 baseline revision remain to be established.
  Revision 13 completes the release 1.3 composition (`user-confirmed`,
  2026-09-29). `MACOS-022`, `SCHEDULE-005`, `SYNC-020`, `MACOS-020`, and
  `MACOS-021` move from the backlog into release 1.3; the two fixes already
  prepared in PRs #107 and #108 join its first wave instead of a patch
  release. Intel support is a release gate: `MACOS-016` is deleted and
  merged into `MACOS-015`, which becomes one High-risk delivery row that
  proposes the ADR 0003 revision, continues from the evaluation build in
  PR #109, and verifies on the 2019 MacBook Air as a dedicated test Mac.
  `MACOS-017` becomes a release 1.3 discovery row for Firefox, with PR #110
  as its spike; delivery stays in the backlog as `MACOS-023`. `DOCS-004`
  prepares the 1.3 public packaging. `NAV-001` precedes `SCHEDULE-004` so
  that the pause set screens are built on Navigation 3. `MACOS-022` and
  `SCHEDULE-005` started on 2026-09-28 as separately authorized fixes, and
  PRs #109 and #110 began before the composition; the composition therefore
  freezes when this revision is accepted. The 2026-09-30 clarification from
  `SCHEDULE-003` renames blocklists to **pause sets** (`user-confirmed`,
  2026-09-29) in the release theme and rows; outcomes and boundaries do not
  change.
  Revision 14 adds `MACOS-024` to release 1.3 at the maintainer's request
  (`user-confirmed`, 2026-09-30, idea 23): a session's system proxy must not
  capture loopback connections such as local MCP servers. It revises the
  frozen composition by the maintainer's decision.
  Revision 15 changes how `MACOS-015` verifies (`user-confirmed`,
  2026-09-30): the 2019 MacBook Air is shared with another person's account,
  so it cannot be a dedicated test Mac. The x86-64 build is verified under
  Rosetta in an arm64 macOS 13 Tart guest through a verification-only
  switch; the `AGENTS.md` exception and the MacBook Air gate are dropped,
  and the row also carries the ADR 0008 update-channel revision.
  The 2026-09-30 backlog addition `WEB-002` (idea 24, `user-confirmed`)
  changes no release. Neither does `MACOS-025` (idea 25, `user-confirmed`,
  2026-09-30), found by the `SCHEDULE-004` measurements.
  The 2026-09-30 clarification from `MACOS-024` adds a loopback-only relay in
  the listener to its outcome (`user-confirmed`, 2026-09-30), because Codex
  reads no proxy exceptions list; the release and wave do not change.
  Revision 16 adjusts release 1.3 before its packaging (`user-confirmed`,
  2026-10-02). `WEB-002` joins the release, continuing from the homepage
  experiment in PR #126, so that `DOCS-004` packages the new site, product
  line, and assets. `SCHEDULE-006` adds a minimal fix for the iPhone schedule
  table size found in the `SCHEDULE-004` review. `MACOS-021` narrows to a
  short measurement, because the maintainer suspects a one-off load on the
  host. `MACOS-017` returns to the backlog for a later release; PRs #110 and
  #114 are closed and kept as material to reuse.
  The 2026-10-02 backlog addition `QUALITY-012` (idea 26, `user-confirmed`,
  from the `SCHEDULE-004` retro) changes no release.
  Revision 17 adds `NAV-002` and `SESSION-006` to release 1.3
  (`user-confirmed`, 2026-10-03). Both are defects that the `DOCS-004`
  captures found: an iOS crash when leaving a pause set and then switching
  destinations, and Session copy and counts that still describe the former
  single item list. `DOCS-004` and `RELEASE-005` wait for them.
  The 2026-10-04 backlog addition `MACOS-026` (idea 27, `user-confirmed`,
  from the `RELEASE-005` retro) changes no release; the maintainer wants it
  considered first when release 1.4 is composed.
  The 2026-10-05 backlog additions `SESSION-007` (idea 28) and `MACOS-027`
  (idea 29), both `user-confirmed`, change no release.
  Revision 18 composes release 1.4 (`user-confirmed`, 2026-10-05) with
  `DESIGN-004` (idea 30), which continues the accepted design spike in
  PR #137, and its `RELEASE-006` row. The maintainer chose to start 1.4 with
  this row alone; adding `MACOS-026`, `SESSION-007`, or other backlog rows
  later takes a further revision.
  The 2026-10-07 backlog addition `IOS-007` (idea 31, `user-confirmed`)
  changes no release.
  Revision 19 adds `IOS-007` to release 1.4 (`user-confirmed`, 2026-10-07)
  as a Standard delivery row in wave 1. It fixes a defect found while
  measuring the verification workload; `RELEASE-006` waits for it.
  The 2026-10-08 backlog additions `SYNC-021` (idea 32) and `TARGETS-009`
  (idea 33), both `user-confirmed` and found while measuring the
  verification workload, change no release.
  Revision 20 completes the release 1.4 composition (`user-confirmed`,
  2026-10-08). `MACOS-026` and `SESSION-007` move from the backlog into
  wave 1. `MACOS-027` joins wave 1 with a limit of one work session to
  reproduce it in a Tart VM; without a reproduction it returns to the
  backlog. `DOCS-005` is a new row that prepares the 1.4 public packaging,
  because `DESIGN-004` changes almost every screen in the showcase and the
  App Store screenshots. `SYNC-021` and `TARGETS-009` stay in the backlog
  until a recheck on `main` after PR #147: both were seen in VMs whose
  onboarding switched the helper off and on again, and no product source
  has changed since. A defect that the recheck reproduces joins wave 1 by a
  later revision. Release 1.4 has no target date; it is published when its
  rows are done.
  Revision 21 records the recheck on `main` `572e341` (`user-confirmed`,
  2026-10-09). `SYNC-021` reproduced in every fresh Tart clone: the first
  **Remove workspace** failed in 6 of 6 runs, and removal took 3 or 4
  presses and up to about 7 minutes. It joins release 1.4 wave 1 with a
  restated outcome. `TARGETS-009` did not recur in 3 runs and the
  maintainer dropped it; it is deleted from the backlog.
  The 2026-10-09 backlog addition `IOS-008` (idea 34, an agent proposal
  under the maintainer's delegation of 2026-10-09, from the `SYNC-021`
  plan review) changes no release. So does the 2026-10-09 backlog
  addition `SYNC-022` (idea 35, an agent proposal under the same
  delegation), which owns the `SYNC-021` verification deferred by the
  maintainer's order to keep VM and iPhone verification to a minimum.
  The 2026-10-10 backlog addition `SESSION-008` (idea 36, an agent proposal
  under the same delegation, from `DOCS-005`) changes no release either.
  The 2026-10-10 backlog addition `TARGETS-010` (idea 37, an agent
  proposal under the maintainer's delegation of 2026-10-09) changes no
  release: the stale summary already exists in 1.3.0, so 1.4 ships with it.
  The 2026-10-10 backlog addition `SESSION-009` (idea 38, from a
  maintainer report, filed as an agent proposal under the same delegation)
  changes no release.
  The 2026-10-11 backlog addition `SESSION-010` (idea 39, found by the
  release 1.5 cross-device run and filed as an agent proposal under the
  same delegation) changes no release.
  Revision 22 composes release 1.5 (`user-confirmed`, 2026-10-10): Posato
  leaves the Apple-only release train for Linux and Android, synchronized
  through one folder that the person keeps synchronized with a service of
  their choice. `PLATFORM-001` and `SYNC-018` move from the backlog into
  the release and are delivered by one long run. The maintainer accepted
  the direction in chat: the Mac desktop application also offers the
  folder transport, a workspace keeps one transport, and iOS joins the
  folder mode too (added the same day);
  a device joins with a one-time pairing code and there is no device
  revocation (a new workspace replaces it), which ADR 0010 records as a
  revision of ADR 0002; Android blocks applications through usage access
  and a full-screen block screen rather than an accessibility service.
  `LINUX-001` and `ANDROID-001` are new rows, and each extends
  `posato-control` and `verify-posato` for its platform. Windows stays in
  the backlog as `PLATFORM-002`.
  Revision 23 completes the release 1.5 composition (`user-confirmed`,
  2026-10-20) after the maintainer accepted ADR 0010. `SESSION-010` and
  `TARGETS-010` move from the backlog into the release: a folder workspace
  makes a device that joins during another device's pause more common.
  `DOCS-006` is a new row that prepares the 1.5 public packaging. The
  maintainer chose a GitHub Actions x86-64 runner for the x86-64 Linux
  package, so `RELEASE-007` builds the `.deb` for both architectures and
  publishes a release-signed Android APK.
  Revision 24 files the maintainer's ideas 40 to 47 (`user-confirmed`,
  2026-10-20). Four small rows join release 1.5 before `DOCS-006`:
  `TARGETS-011` (no warning for a set without apps), `MACOS-028` (the
  window that opens with only its toolbar), `SYNC-023` (when the last sync
  happened), and `TARGETS-012` (duplicate a pause set). Four larger rows
  enter the backlog as candidates for release 1.6: `MACOS-029` (resume
  restrictions without a press when administrator approval is on),
  `SCHEDULE-007` (a break in a scheduled pause), `TARGETS-013` (a website
  opened briefly every N minutes), and `HISTORY-001` (a calm history of
  pauses kept as planned).
  Revision 25 holds release 1.5 for six rows found while `DOCS-006`
  captured the final interface (`user-confirmed`, 2026-10-21): `SYNC-024`
  (a renamed first set lost its name when the device created a workspace),
  `SYNC-025` (iCloud wording in folder mode), `SYNC-026` (a simpler way to
  join a folder workspace, idea 48), `ANDROID-002` (the block screen in
  Dark Mode), `LINUX-002` (Linux follows the desktop's light or dark
  setting, idea 49), and `LINUX-003` (Posato on Linux keeps running in the
  background, idea 50). `DOCS-006` recaptures after them.
  Revision 26 adds two more rows to release 1.5 (`user-confirmed`,
  2026-10-21): `TARGETS-007` becomes a delivery row for a one-time export
  and import of pause sets and schedules (idea 14), the maintainer's answer
  to moving choices to a Mac on another Apple Account without sync, and
  `DESIGN-005` warms the light palette (idea 51). It also records that the
  test Apple Account's long-history zone, which `SYNC-022` was to reproduce
  on, was removed during `SYNC-024`'s verification on 2026-10-21, so
  `SYNC-022` must rebuild that history first.
  Revision 27 adds two backlog rows from the release 1.5 retrospective
  (`user-confirmed`, 2026-10-22): `QUALITY-013` (idea 52), so that
  `posato-control flow onboard -t linux` finishes when the pausing service
  is already installed, and `PLATFORM-003` (idea 53), which extracts a
  shared resident-window core from the Mac and Linux code when Windows
  arrives. It also names `tools/roadmap-mirror` as the way to update the
  GitHub Projects mirror.
  Revision 28 records `QUALITY-013` as delivered outside a release by the
  `RELEASE-007` retrospective (`user-confirmed`, 2026-10-23), together with
  a fix it exposed: `launch -t linux` now returns only once Posato's
  accessibility tree can be read.
  Revision 29 adds `MACOS-030` (idea 54) and starts it outside a release
  at the maintainer's request (`user-confirmed`, 2026-10-23): the Mac
  stopped talking to its helper after 256 operations, which left
  restrictions in place after a pause and refused updates. It ships with
  the next macOS build.
  Revision 30 composes release 1.5.1 for Mac at the maintainer's request
  (`user-confirmed`, 2026-10-24, translated from Polish: "prepare 1.5.1
  up to the release in the GitHub assets"): `MACOS-030`, merged as #197,
  moves from the backlog
  into it, and `RELEASE-008` publishes it.
  Revision 31 composes release 1.5.2 for Mac at the maintainer's request
  (`user-confirmed`, 2026-10-25) after "Restrictions need attention"
  returned on 1.5.1 (idea 55): `MACOS-031` (new) keeps the helper and its
  daemon through a slow moment, and `MACOS-029` moves from the backlog
  into the release, widened to a helper lost during a pause and to the rule
  that the red notice appears only when Posato cannot recover without the
  person; `SESSION-009` folds into it and leaves the backlog.
  `RELEASE-009` publishes them.
  Revision 32 composes release 1.6 at the maintainer's request
  (`user-confirmed`, 2026-10-25) after the `PLATFORM-002` feasibility spike
  (PR #206) showed that an agent can verify Posato in a Tart Windows guest
  with no person present: Windows at parity with Linux and the Mac as one
  delivery row, `PLATFORM-002`, together with the candidates
  `SCHEDULE-007`, `TARGETS-013`, and `HISTORY-001`, and a new
  `ONBOARDING-005` for setup wording that names the Mac on other platforms
  (idea 56). `PLATFORM-003` joins because `PLATFORM-002` starting is its
  trigger, so Windows reuses one resident-window core (agent-proposed with
  this revision). `DOCS-007` and `RELEASE-010` complete the release. All
  six rows leave the backlog. Release 1.5.2 is still open; if 1.6 product
  changes reach `main` before it ships, `RELEASE-009` branches from the
  `v1.5.1` tag, as the planning boundaries require.
  Revision 33 folds `PLATFORM-003` into `PLATFORM-002` at the maintainer's
  request (`user-confirmed`, 2026-10-27), when `PLATFORM-002` started
  before the resident core existed: one pull request extracts the core and
  builds Windows on it, and `PLATFORM-003` leaves the release.
  Revision 34 adds two backlog rows from the `PLATFORM-002` review at the
  maintainer's request (`user-confirmed`, 2026-10-28): `PLATFORM-004`, one
  least-privilege rule for the desktop helpers on the Mac, Linux, and
  Windows (idea 57), and `SESSION-011`, one shared fix for the expired
  session a helper keeps reporting (idea 58).
  Revision 35 moves `I18N-001` from the backlog into release 1.6 and starts
  it at the maintainer's request (`user-confirmed`, 2026-10-28, translated
  from Polish: put it in 1.6 right away). Polish follows the system
  language, with no in-app switch, in wave R1.6/W1 and integration group
  `PR-I18N-PL`. `DOCS-007` and `RELEASE-010` depend on it so the public
  packaging and the publication include Polish. The maintainer reviews the
  Polish copy in that pull request. This amends the composed release 1.6.
  Revision 36 adds the `REDIRECT-001` backlog row from idea 59 at the
  maintainer's request (`user-confirmed`, 2026-10-28): decide whether
  Posato offers host redirect rules beside blocking, including whether a
  rule is always on or bound to a session or pause set.

This roadmap plans the releases that follow Posato 1.0.0. It retains
outcomes, ordering, direct dependencies, waves, and integration groups for
the next three releases and keeps a backlog for ideas that have no release
yet. It does not pre-authorize implementation, revise an accepted decision, or
require a full specification for inactive work. Create a concise brief just
before a row starts, following [the task workflow](README.md).

The [MVP roadmap](mvp-roadmap.md) is complete and retained as history. Its
last row, `RELEASE-002`, closes outside this document; see
[Prerequisites](#prerequisites-and-handovers).

Ideas arrive faster than releases. The
[idea queue](../wiki/topics/mvp-open-questions.md#post-mvp-feature-ideas-for-discovery)
and the
[session usability proposals](../wiki/topics/mvp-open-questions.md#post-mvp-session-usability-proposals)
in the wiki are the intake for loosely defined ideas; this roadmap assigns
them to releases. The [intake rule](#intake-and-release-composition) below
governs how a new idea becomes a row.

## Planning boundaries

- A release is a themed, bounded set of rows that ends with one `RELEASE`
  row. Three releases are planned here; later releases are composed at their
  own planning checkpoint from the backlog and new ideas.
- Both applications share one version. A release with product changes takes
  the next minor version (`1.1.0`, `1.2.0`, `1.3.0`); a release that only
  fixes defects takes a patch version. The iOS build is submitted to App
  Review only when the iOS binary changed; the macOS download always follows
  the notarized `MACOS-008` path through GitHub Releases.
- Row classes:
  - `delivery`: the outcome is known; the brief records boundaries and
    acceptance criteria.
  - `discovery`: the outcome is a recorded decision, an accepted decision
    revision proposal, or a delivery plan, never product code. A discovery
    row may propose a revision of an ADR, `DESIGN.md`, or a product page; it
    does not accept one. Acceptance stays with the maintainer, as the
    [agent instructions](../../AGENTS.md) require.
  - `backlog`: a stub with no release; it may be refined but not started.
- A patch release branches from the tag of the release it fixes when `main`
  already carries unreleased product changes.
- The accepted contracts remain in force until explicitly revised: the
  exact-domain contract in
  [ADR 0005](../decisions/0005-macos-browser-enforcement-and-coexistence.md),
  the one-use Apply authorization, update path, and removal contract in
  [ADR 0004](../decisions/0004-macos-helper-ownership-and-lifecycle.md), the
  arm64-only macOS 15 baseline in
  [ADR 0003](../decisions/0003-mvp-application-architecture-baseline.md),
  the format-1 operation vocabulary in
  [ADR 0006](../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md),
  and the no-telemetry promise in [`PRIVACY.md`](../../PRIVACY.md).
- A wave marks dependency-eligible concurrency, not automatic authorization.
  Exact write surfaces, contract ownership, worktrees, and integration order
  are checked when that wave starts.
- The review tier of every row is chosen at its start under
  [`docs/tasks/README.md`](README.md). Rows named High-risk below need the
  additional plan review; the others default to Standard.
- Product accounts, product-operated relays, analytics, browsing history, and
  administrator resistance stay outside every release, as the
  [MVP scope non-goals](../product/mvp-scope.md#explicit-non-goals) record.

## Intake and release composition

1. A new idea is recorded first as one bullet on the wiki idea queue with its
   provenance label, then as one `backlog` row here, in the same pull request
   when both are needed. A bullet without a row is acceptable while the idea
   is too vague to name an outcome.
2. Only the maintainer assigns a row to a release, at a planning checkpoint
   held before that release starts. The checkpoint is a roadmap revision: it
   names the release theme, its rows, and their waves.
3. A release's composition freezes when its first row starts. A later idea
   goes to the next release unless the maintainer revises the current one.
4. A `backlog` row that gains an owner and a release moves into that
   release's table with its wave; the backlog table keeps no copy.
5. Rows dropped by the maintainer are deleted from the tables and named in the
   revision prose, so the tables show only live work.
6. Each release ends with a `RELEASE` row that follows
   [`releasing.md`](../development/releasing.md); its brief names only the
   differences.

## GitHub Projects mirror

The public GitHub project
[Posato roadmap](https://github.com/users/dees91/projects/1) mirrors this
document so that anyone can see the plan and its execution state without
reading the repository. This document remains the source of truth; the
project never introduces a row, release, or dependency that the document
does not have.

- One draft item per row, titled `<ID>: <outcome>`, with a link back to this
  document. Roadmap rows are not issues; issues are reserved for reports from
  people who use Posato.
- Fields mirror the tables: Release, Class, Epic, Wave, Risk, Dependencies,
  and Integration group. Status (Todo, In Progress, Done), PR, Start, and
  Target carry execution state that the document does not track.
- Keep the mirror current in the same step as the change, never later: a
  roadmap revision adds, moves, or removes items; starting a row sets Status
  to In Progress and records the pull request; merging that pull request sets
  Done; a dropped row is archived. An agent that changes a row and cannot
  update the project says so in the pull request.
- Update with `tools/roadmap-mirror`
  (`./gradlew :roadmap-mirror:installDist`, then
  `tools/roadmap-mirror/build/install/roadmap-mirror/bin/roadmap-mirror`),
  which needs `gh` with the `project` token scope:
  `set-status <ROW-ID> todo|in-progress|done [--pr N]` finds the draft item
  titled `<ROW-ID>: ...` and sets Status and the PR field (`#N`);
  `add <ROW-ID> --title <outcome> --body <markdown> --epic <epic>
  [--release backlog|1.5|...] [--class backlog|delivery|discovery]
  [--status ...] [--pr N]` adds a row's item, with Status Todo unless
  `--status` says otherwise. The project and field ids live only in its
  `RoadmapProject.kt`; refresh them there after a field changes. It works
  through `gh api graphql`, because `gh project item-list` returned no items
  for this project (`observed` 2026-10-22). GitHub's item list can also come
  back empty or without items added minutes before, so `add` takes
  `--status` and `--pr`, sets a new row's whole state at once, and prints
  the item id; `set-status --item <id>` skips the list, and `set-status`
  refuses an empty list instead of reporting the row missing. Both read the
  item back by its id after writing and fail unless it is a live item of
  this project with the row's title and the written values. `add` checks
  for an existing item through the list, so while the list lags, a rerun
  can add a duplicate. Archive and the remaining fields
  (Wave, Dependencies, Integration group, Risk) still go through
  `gh project item-archive` and `item-edit`. Views are created once in the
  GitHub interface and are not part of the mirror rule.
- Each release has a repository milestone named after its version (`1.3.0`).
  A row's pull request receives that milestone when it opens, and the
  release row closes the milestone at publication.

## Prerequisites and handovers

These items belong to the MVP roadmap or to maintainer-owned accounts and are
dependencies of release 1.1, not rows of this roadmap:

- `RELEASE-002` closeout: the App Store listing goes live after App Review
  accepts 1.0.0 (3), then the draft pull request `docs/release-002-app-store`
  links the App Store badge and merges. Until then, release 1.1 can publish
  the macOS download but not an iOS update.
- Apple's EU trader verification, which gates EU availability of the iOS
  application.
- The wiki idea queue from pull request #52, which this roadmap cites.
- Handovers from the release records that the rows below own: the manual
  update download recorded as an accepted risk under `TB-08`/`T-13`
  (`MACOS-010`, `MACOS-011`); the unverified macOS 15 and iOS 18 matrix
  (`QUALITY-007`); the iPhone-only copy shown on iPad (`IOS-004`); and the
  Intel exclusion in ADR 0003 (`MACOS-015`).

## Release 1.1: everyday use and the update path

Theme: remove the frictions people meet in the first days with Posato and
give the macOS application a supported way to learn about a newer release.
Low product risk except the update path, which revises an accepted contract.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `SESSION-004` | Offer a direct route from the Session screen's paused-items summary to adding and editing websites and applications, and make the selected-items search field read as a filter rather than an entry field, within the accepted two-destination navigation in `DESIGN.md`. | Sessions and enforcement | delivery | R1.1/W1 | None | PR-SESSION-EDIT-ROUTE |
| `ONBOARDING-003` | Keep the first-website step focused after each added website while its continue action stays visible, state the saved total, and give the expanded macOS helper-permission actions a consistent arrangement under the `DESIGN.md` action hierarchy. | Onboarding | delivery | R1.1/W1 | None | PR-ONBOARDING-ENTRY |
| `TARGETS-006` | Treat `www.<host>` and `<host>` as one paused website through the matching rule on both platforms and say so at entry, keeping one stored exact-domain row per typed host under a clarified ADR 0005, and recording separately whether broader subdomain coverage is wanted. | Target management | delivery | R1.1/W1 | None | PR-WWW-COVERAGE |
| `IOS-004` | Show iPad-appropriate wording and layout wherever the iPhone-only copy appears, and refresh the iPad store screenshots when a captured surface changes. | Platform coverage | delivery | R1.1/W1 | None | PR-IPAD-COPY |
| `MACOS-010` | Decide how Posato on macOS learns about a newer release: compare a check-and-download flow against GitHub Releases with a Sparkle-style in-app installer on hosting, update signing, the ADR 0004 update path that restores proxy ownership and helper registration before the bundle is replaced, and the `PRIVACY.md` boundary that allows no request beyond the version check; propose the ADR 0004 revision. High-risk. | Release readiness | discovery | R1.1/W1 | None | PR-MAC-UPDATE-DECISION |
| `MACOS-011` | Implement the accepted update path from `MACOS-010`, with the feed or release metadata published from the repository's release process and verified on a notarized candidate. High-risk. | Release readiness | delivery | R1.1/W2 | `MACOS-010` | PR-MAC-UPDATES |
| `QUALITY-007` | Verify the accepted flow on macOS 15 in a virtual machine on the supported Mac and on iOS 18 on a physical iPhone with the 1.1 candidates, and update the availability page with the verified matrix or its stated gaps. | Verification | delivery | R1.1/W2 | `SESSION-004`, `ONBOARDING-003`, `TARGETS-006`, `IOS-004` | PR-PLATFORM-MATRIX |
| `RELEASE-003` | Verify the 1.1.0 candidates, publish the notarized DMG through GitHub Releases with release notes, submit the iOS build to App Review, and hand the maintainer only account-owned steps. | Release readiness | delivery | R1.1/W3 | `MACOS-011`, `QUALITY-007` | PR-RELEASE-1-1 |

## Release 1.2: the Mac always at hand, with shared schedules

Theme: keep Posato ready on the Mac through one guided setup,
and deliver recurring schedules on Mac and iPhone. The accepted
[product scope](../product/schedules-and-mac-setup.md) and
[`DESIGN.md`](../../DESIGN.md#release-12-setup-and-schedules) define the outcome.

`ONBOARDING-004` follows `MACOS-014` and stays serialized with `NOTIFY-001`
because both touch onboarding and This Mac. `SCHEDULE-001` then settles the
remaining schedule rules and obtains the required authorization review.
`SCHEDULE-002` delivers the accepted design on both platforms before
`RELEASE-004`. `IOS-006` remains an independent prerequisite for reliable
session lifetime on iPhone. These are planning dependencies; this revision
starts none of the rows.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `MACOS-012` | Decide how Posato stays present on macOS without its main window: a status-bar menu that shows the current session, starts or ends one, and opens the window on demand; whether the Compose Desktop process stays resident or session orchestration moves into a native helper; launch at login; and resource use. Propose the ADR 0003 and ADR 0004 revisions. High-risk. | Sessions and enforcement | discovery | R1.2/W1 | None | PR-MENU-BAR-DECISION |
| `MACOS-013` | Implement the accepted menu bar presence from `MACOS-012`, so blocking continues while the window is closed and the person can start, end, and inspect a session from the status bar. High-risk. | Sessions and enforcement | delivery | R1.2/W2 | `MACOS-012` | PR-MENU-BAR |
| `MACOS-014` | Reduce repeated administrator prompts at session start through a one-time opt-in with an explicit revocation path and authenticated, narrowly scoped helper requests, after a security review of the ADR 0004 revision that replaces the one-use Apply authorization. High-risk. | Sessions and enforcement | delivery | R1.2/W2 | `MACOS-012` | PR-MAC-AUTHORIZATION |
| `IOS-006` | Keep an active iPhone session and its restrictions when Posato is relaunched during the session: re-applying the same session must not stop and restart its Device Activity monitoring, and an interval-end callback that arrives before the planned end must not end the session. Prove it with repeated relaunches on the test iPhone, including a fast relaunch, and keep the `IOS-002` suspended expiry working. High-risk. | Sessions and enforcement | delivery | R1.2/W1 | None | PR-IOS-RELAUNCH |
| `NOTIFY-001` | Deliver local notifications when a session starts or ends on iOS and macOS, with a permission flow, a preference, and no remote push or server. | Notifications | delivery | R1.2/W2 | `MACOS-012` | PR-LOCAL-NOTIFICATIONS |
| `ONBOARDING-004` | Deliver one guided Mac setup in `DESIGN.md` for blocking, quiet login launch and starts without repeated passwords. Reuse the flow from onboarding, Session and Schedules; resume missing steps and verify actual state before completion. Keep a persistent Finish setup route after deferral, one dismissible upgrade offer and advanced revocation controls in This Mac. Preserve existing installations and defer password-requiring setup during a session. The setup's grant covers the person's own Start and Resume; `SCHEDULE-002` extends it to automatic starts only after the `SCHEDULE-001` security review, and neither ships without the other. High-risk. | Onboarding | delivery | R1.2/W2 | `MACOS-014` | PR-MAC-SETUP |
| `SCHEDULE-001` | Complete the accepted release 1.2 schedule design: occurrence identity and skip/early-end convergence, time zones and daylight saving, overnight intervals, overlaps and manual-session conflicts, and offline or missing-device behavior. Specify ADR 0006 operation compatibility, iOS Device Activity execution and the resident Mac host. Propose and independently security-review the ADR 0004/0009 changes for explicit consent to automatic scheduled Apply, including startup and wake within an interval. Specify verified completion of the unified Mac setup before schedule creation, including automatic-start consent, migration of existing grants and recovery after revocation. Update `PRIVACY.md` and the privacy manifests for the synchronized schedule data. End with accepted decisions and an implementation plan for `SCHEDULE-002` in this release. High-risk. | Schedules | discovery | R1.2/W3 | `MACOS-013`, `MACOS-014`, `ONBOARDING-004` | PR-SCHEDULE-DECISION |
| `SCHEDULE-002` | Deliver the accepted recurring schedules on Mac and iPhone: the Schedules destination, multiple named weekday/time plans, enable/disable, iCloud sharing with local offline execution, next-run and device-readiness states, skip-next and early-end behavior, and consented automatic Mac start/wake catch-up to the original end. Integrate the unified Mac setup and require verified blocking, login launch and automatic-start consent before Mac schedule creation; resume missing setup if access is revoked. Integrate contextual setup and local session notifications; prove occurrence suppression across restart and sync under the accepted convergence rules. High-risk. | Schedules | delivery | R1.2/W4 | `SCHEDULE-001`, `NOTIFY-001`, `IOS-006` | PR-SCHEDULE-DELIVERY |
| `DOCS-003` | Prepare the public packaging for 1.2 without publishing it: recapture the showcase on the 1.2 applications, revise the storyboard with schedules and render the demo, walkthrough, stills, and social preview again; describe 1.2 in the README and on `posato.app`; update the App Store description, What's New, and iPhone and iPad screenshots; and draft the GitHub release notes. | Release readiness | delivery | R1.2/W5 | `ONBOARDING-004`, `NOTIFY-001`, `SCHEDULE-002` | PR-RELEASE-1-2-MEDIA |
| `RELEASE-004` | Verify the 1.2.0 candidates including shared schedules and Mac setup, publish the macOS release through the `MACOS-011` update path and GitHub Releases, and submit the iOS build to App Review. | Release readiness | delivery | R1.2/W5 | `MACOS-013`, `MACOS-014`, `NOTIFY-001`, `IOS-006`, `ONBOARDING-004`, `SCHEDULE-002`, `DOCS-003` | PR-RELEASE-1-2 |

## Release 1.3: pause sets, navigation, and Intel Macs

Theme: reusable named pause sets for one-time sessions and recurring
schedules, system back gestures through `NAV-001`, and Posato on Intel Macs
running macOS 13 Ventura. The [pause set product scope](../product/pause-sets.md)
records the accepted behavior and the decisions `SCHEDULE-003` must complete
before delivery. The release also carries reported defects in enforcement,
schedules, and synchronization, and a Firefox discovery.

The composition was completed on 2026-09-29 and is frozen: `MACOS-022` and
`SCHEDULE-005` had already started as separately authorized fixes, and the
evaluation and spike work in PRs #109 and #110 began before composition.
Ordering across the waves:

- `MACOS-022` and `SCHEDULE-005` are prepared in PRs #107 and #108 and merge
  first. `MACOS-020`, `MACOS-021`, and `MACOS-024` touch the
  same helper and proxy surface as `MACOS-022` and start from it in the next
  wave; `MACOS-020` and `MACOS-024` both change proxy apply and restore, so
  they are implemented one after the other.
  `SCHEDULE-003` decides on top of the natural-expiry state that
  `SCHEDULE-005` introduces.
- `NAV-001` merges before `SCHEDULE-004` so that the pause set screens are
  built on Navigation 3 rather than migrated later.
- `MACOS-015` continues from the evaluation build in PR #109. Tart cannot
  run an x86-64 guest, and the 2019 MacBook Air is shared with another
  person's account, so the row verifies the x86-64 build in an arm64 macOS 13
  Tart guest under Rosetta through a verification-only switch
  (`user-confirmed`, 2026-09-30). No physical Intel Mac is used.
- `WEB-002` and `SCHEDULE-006` precede `DOCS-004`, which packages the new
  product line, site, and media.
- `NAV-002` and `SESSION-006` were found while `DOCS-004` recaptured the
  showcase. They precede it because the corrected Session appears in its
  media.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `MACOS-022` | Restore the local pause page after HTTPS denial in Chrome and Safari within ADR 0005 without weakening denial: request Automation consent per browser, keep network blocking when it is refused, and deliver browser and picker calls from the helper's main run loop; see the [brief](specifications/macos-022.md). High-risk. | Sessions and enforcement | delivery | R1.3/W1 | None | PR-MAC-PAUSE-PRESENTATION |
| `SCHEDULE-005` | Resume naturally expired schedules after synchronization extends them into the current interval; preserve explicit Skip and End early, original start, clock rollback protection, and the 24-hour cap, under the expiry rule revision and legacy reevaluation accepted on 2026-09-28 (`user-confirmed`, PR #108); see the [brief](specifications/schedule-005.md). High-risk. | Schedules | delivery | R1.3/W1 | None | PR-SCHEDULE-EXTENSION |
| `SYNC-020` | Publish a session start or early end reliably from the device that made it: first reproduce, without the maintainer, that an iPhone-started session reaches iCloud only after a manual **Sync now**; then retry an interrupted or failed publication automatically with backoff and give iOS time to finish it in the background, so the peer adopts the session at its next own sync. Remote push to wake the peer is out of scope. | Sessions and enforcement | delivery | R1.3/W1 | None | PR-SESSION-PUBLISH |
| `MACOS-020` | Keep a session truthful and recoverable when the network service that holds Posato's proxy settings disappears during it: report that restrictions need attention instead of **Restrictions active**, and clear or reconcile the stale ownership record so later sessions can apply again. | Sessions and enforcement | delivery | R1.3/W2 | `MACOS-022` | PR-MAC-PROXY-RECOVERY |
| `MACOS-021` | Measure the normal-user helper's CPU use in the first enforced session of one or two fresh Tart clones on a quiet host, where `MACOS-012` once saw about 60% of a core for 14 minutes. The maintainer suspects a one-off load from other work on the host (`user-confirmed`, 2026-10-02): if the spike does not recur, the row closes with that result; a reproduced cause is fixed and bounded. | Sessions and enforcement | delivery | R1.3/W2 | `MACOS-022` | PR-MAC-HELPER-CPU |
| `MACOS-015` | Support Intel Macs on macOS 13 Ventura: propose and obtain acceptance of the ADR 0003 baseline revision, covering the support horizon Apple gives Intel Macs and macOS 13, and of the ADR 0008 update-channel revision; build the x86-64 Compose Desktop artifact, runtime, native libraries, and Swift helpers for macOS 13; sign and notarize verified x86-64 candidates and the update feed entry that the accepted ADR 0003 and ADR 0008 revisions specify, which `RELEASE-005` publishes; verify setup, actual website and application blocking, synchronization, and the in-app update in an arm64 macOS 13 Tart guest running the x86-64 build under Rosetta through a verification-only switch; and update the availability page. High-risk. | Platform coverage | delivery | R1.3/W1 | None | PR-INTEL-RELEASE |
| `MACOS-024` | Keep loopback connections working during a macOS session: while a session's proxy is applied, add `localhost`, `127.0.0.1`, and `::1` to the proxy exceptions of the service Posato applies to, keep the existing exceptions, and restore them exactly when the proxy is restored; relay requests to those three hosts on any port through the listener for clients that ignore the exceptions, under an ADR 0004 and ADR 0005 amendment. Reported: local Codex MCP connections received an empty response and `codex_tui failed to start` during a session, while a direct connection and `NO_PROXY` worked. High-risk. | Sessions and enforcement | delivery | R1.3/W2 | `MACOS-022` | PR-MAC-LOOPBACK-EXCEPTIONS |
| `NAV-001` | Move the screen stacks within each destination to Navigation 3 and support system back gestures: the interactive edge swipe on iPhone and iPad, and keyboard and trackpad back on the Mac. It keeps the explicit **Back** actions and the destinations accepted in `DESIGN.md`, including Schedules. | Platform coverage | delivery | R1.3/W1 | None | PR-NAVIGATION |
| `SCHEDULE-003` | Complete the accepted pause set scope for manual sessions and schedules: live edits and deletion, manual-session overlap, default ownership, limits and device readiness, migration, synchronization and older-client compatibility. Update the affected design and architecture authorities and end with accepted decisions and a delivery plan. | Target management | discovery | R1.3/W2 | `SCHEDULE-005` | PR-PAUSE-SET-DECISION |
| `SCHEDULE-004` | Deliver reusable named pause sets on Mac and iPhone under the accepted scope: one set per manual session or schedule, a default for new starts and plans, migration of existing targets and schedules, union of overlapping sets, synchronized definitions and websites, and per-set device-local application choices. Verify migration, actual blocking and unblocking, overlap, offline execution, and cross-device convergence. High-risk. | Target management | delivery | R1.3/W3 | `SCHEDULE-003`, `NAV-001` | PR-PAUSE-SET-DELIVERY |
| `WEB-002` | Make the first screen of `posato.app` show at a glance what Posato does, especially on a phone, and replace the product line **Pause. Then choose.** with a stronger one wherever it appears (`DESIGN.md`, the site title and hero, the README, and the App Store subtitle). Continue from the homepage experiment in PR #126 (a bolder layout, an animated hero with a static poster on phones and for reduced motion, and the provisional line "A little space. For what matters."), with the maintainer choosing the final line and layout, the clarity review, and no em dash in public copy. | Release readiness | delivery | R1.3/W4 | None | PR-WEB-HOMEPAGE |
| `SCHEDULE-006` | Keep the iPhone version-2 schedule table within its storage bound for every supported configuration, including 1,024 long websites shared by ten sets, and report a failed publication instead of silently keeping the previous table: the minimal change, with no new limits. | Schedules | delivery | R1.3/W4 | `SCHEDULE-004` | PR-SCHEDULE-TABLE-BOUND |
| `NAV-002` | Stop the iOS crash after leaving a pause set's screen and then switching destinations: a stack nested in a screen that Navigation 3 already removed must not dispose its back dispatcher a second time. Keep system back, the edge swipe, and the Back buttons working on iPhone, iPad, and the Mac. | Platform coverage | delivery | R1.3/W4 | `NAV-001`, `SCHEDULE-004` | PR-RELEASE-1-3-FIXES |
| `SESSION-006` | Make Session describe pause sets: replace the remaining **Paused items** copy with **Pause sets**, explain a running session's frozen counts in terms of its set, and stop showing the default set's counts while only a scheduled pause restricts, where each running part names its own set. | Sessions and enforcement | delivery | R1.3/W4 | `SCHEDULE-004` | PR-RELEASE-1-3-FIXES |
| `DOCS-004` | Prepare the public packaging for 1.3 without publishing it: recapture the showcase with pause sets and render the media again; describe 1.3, pause sets, and Intel support in the README and on `posato.app` with the new product line and homepage from `WEB-002`; update the App Store description, What's New, and iPhone and iPad screenshots; and draft the GitHub release notes. | Release readiness | delivery | R1.3/W5 | `SCHEDULE-004`, `MACOS-015`, `SYNC-020`, `MACOS-020`, `MACOS-021`, `MACOS-024`, `WEB-002`, `SCHEDULE-006`, `NAV-002`, `SESSION-006` | PR-RELEASE-1-3-MEDIA |
| `RELEASE-005` | Verify the 1.3.0 candidates, publish the macOS release for arm64 and x86-64 through the `MACOS-011` update path and GitHub Releases, and submit the iOS build to App Review. | Release readiness | delivery | R1.3/W6 | `MACOS-022`, `SCHEDULE-005`, `SYNC-020`, `MACOS-020`, `MACOS-021`, `MACOS-024`, `MACOS-015`, `NAV-001`, `SCHEDULE-004`, `SCHEDULE-006`, `NAV-002`, `SESSION-006`, `WEB-002`, `DOCS-004` | PR-RELEASE-1-3 |

## Release 1.4: a native feel on every Apple device

Theme: Posato looks and behaves like an app made for each Apple device while
its interface stays one shared Compose codebase for the planned Android and
Linux apps. The design critique of 2026-10-04 and the spike in PR #137 found
and replaced the Material 3 port feel on iOS and the shared defects behind
it; `DESIGN-004` finishes that work and records the direction in
`DESIGN.md`. The release also carries two everyday improvements, an in-app
update without the quit question and longer quick choices for a manual
pause, and reported defects.

The composition was started with one delivery row by the maintainer's
choice. Revision 19 adds `IOS-007`, a defect in ending a pause on the
iPhone. Revision 20 completes the composition, and revision 21 adds
`SYNC-021` after its recheck. The release has no target date. Ordering
across the waves:

- `MACOS-026`, `SESSION-007`, `MACOS-027`, and `SYNC-021` are independent.
  `MACOS-026`, `MACOS-027`, and `SYNC-021` all change the macOS
  application, so their write surfaces are checked when they start.
- `SYNC-021` reproduces only on a zone with a long history, which the test
  Apple Account has after many verification link and removal cycles. The
  row first makes that history repeatable to build; only then is the test
  account's zone history cleaned, so routine iCloud runs are fast again.
- `MACOS-027` has a limit of one work session to reproduce the defect in a
  Tart VM along the maintainer's update path. Without a reproduction the
  row records its attempts, returns to the backlog, and no longer holds
  `DOCS-005` or `RELEASE-006`.
- `DOCS-005` captures the final interface, so it follows every wave 1 row.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `DESIGN-004` | Give the iOS app a native feel and refine the shared design system on every platform, from the spike in PR #137: a UIKit-like screen stack with velocity-aware back swipe, navigation bars and large titles, native menus and pickers, swipe actions, and one calmer set of shared controls without ripple on the Apple hosts. Close the remaining findings of a native audit and one polish round, adapt `posato-control` and `verify-posato` to the new interface, and record the direction in `DESIGN.md`. | Platform coverage | delivery | R1.4/W1 | None | PR-NATIVE-FEEL |
| `IOS-007` | Find why Safari on the iPhone hangs on a black page for every plain `http://` site after a pause that blocks websites ends, unless Posato is in front, while HTTPS loads; reproduce it on the test iPhone without the test driver, decide whether Posato's clear or reconcile can avoid it, and fix it or record it as an iOS limit. Until then `observe-unblocked-ios.json`, which opens `http://example.com`, cannot pass after a session. | Sessions and enforcement | delivery | R1.4/W1 | None | PR-IOS-HTTP-AFTER-PAUSE |
| `MACOS-026` | Let an in-app update replace Posato on the Mac without asking "Quit Posato?": the updater's quit request skips the confirmation that a person's quit gets while a pause runs or a schedule is on, and a person's quit still asks. Verify a candidate-channel update from a build with the change to the next with a schedule on and during a pause; without either, the old build does not ask either. | Sessions and enforcement | delivery | R1.4/W1 | None | PR-MAC-UPDATE-QUIT |
| `SESSION-007` | Offer longer quick choices when a pause is started by hand, next to 25 and 45 minutes: 1 h, 2 h, 4 h, 8 h, and **Until end of day** at midnight on the device's clock, within the existing 5-minute minimum and 24-hour maximum, on Mac and iPhone, with the final set and phone layout settled in `DESIGN.md`. | Sessions and enforcement | delivery | R1.4/W1 | None | PR-SESSION-QUICK-DURATIONS |
| `MACOS-027` | Find why a long-lived Mac install reports the background helper as unavailable ("Setup incomplete", "could not be checked or enabled", a schedule that "couldn't start here") while System Settings allows it and blocking works, reproduce it in a Tart VM along the maintainer's update path within one work session, and fix the readiness check or its recovery so Session, setup, and schedules agree with what the helper does. Without a reproduction in that session the row records its attempts and returns to the backlog. | Sessions and enforcement | delivery | R1.4/W1 | None | PR-MAC-HELPER-READINESS |
| `SYNC-021` | Make one **Remove workspace** press remove a Mac's workspace right after a link and when its zone has a long history. On `main` `572e341` in fresh Tart clones on the test Apple Account, the first press ended with "Sync didn't finish" in 6 of 6 runs, and removal took 3 or 4 presses and up to about 7 minutes while the sync companion read records 16 at a time. Find whether the attempt deadline, the page budget, or the first sync after the link causes it; let the removal continue to completion with honest progress instead of failing at a deadline; and build a repeatable long-history reproduction before the test account's zone history is cleaned. | Sessions and enforcement | delivery | R1.4/W1 | None | PR-SYNC-REMOVAL-HISTORY |
| `DOCS-005` | Prepare the public packaging for 1.4 without publishing it: recapture the showcase with the interface from `DESIGN-004` and render the media again; describe 1.4 in the README and on `posato.app`; update the App Store description, What's New, and iPhone and iPad screenshots; and draft the GitHub release notes. | Release readiness | delivery | R1.4/W2 | `DESIGN-004`, `IOS-007`, `MACOS-026`, `SESSION-007`, `MACOS-027`, `SYNC-021` | PR-RELEASE-1-4-MEDIA |
| `RELEASE-006` | Verify the 1.4.0 candidates, publish the macOS release through the `MACOS-011` update path and GitHub Releases, and submit the iOS build to App Review. | Release readiness | delivery | R1.4/W3 | `DESIGN-004`, `IOS-007`, `MACOS-026`, `SESSION-007`, `MACOS-027`, `SYNC-021`, `DOCS-005` | PR-RELEASE-1-4 |

## Release 1.5: Linux and Android, synchronized through a folder

Theme: Posato runs on Linux and Android as well as on Apple devices. The
devices of one person share pauses, pause sets, and schedules through a
folder that Dropbox, OneDrive, Syncthing, or any other service keeps
synchronized; Posato only reads and writes encrypted files in it. The Mac
and the iPhone can join such a workspace instead of using iCloud.

The maintainer asked for one long run (`user-confirmed`, 2026-10-10). The
rows are delivered as stacked pull requests in the order of the table.
The four delivery and discovery rows share one
[brief](specifications/release-1-5-linux-android.md) and execution record,
with one milestone per pull request.
Revision 23 adds `SESSION-010` and `TARGETS-010` as separate pull
requests after the long run, each with its own record path. `DOCS-006`
captures the final interface, so it follows them, and `RELEASE-007` comes
last.
Revision 24 adds `TARGETS-011`, `MACOS-028`, `SYNC-023`, and
`TARGETS-012` to wave 4 as separate lightweight or recorded pull requests;
`DOCS-006` waits for them too.
Revision 25 adds wave 4b: six rows found while preparing the packaging,
each its own pull request; `SYNC-025` lands before `SYNC-026`, which
rebuilds the same section. Revision 26 adds `TARGETS-007` and `DESIGN-005`
to the same wave; `DESIGN-005` lands before `DOCS-006` recaptures.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `PLATFORM-001` | Record the platform decision in ADR 0010: Linux and Android first, Windows later; per-platform enforcement mechanisms and privilege models; one shared Compose interface. | Platform coverage | discovery | R1.5/W1 | None | PR-PORTABLE-FOLDER |
| `SYNC-018` | Synchronize a workspace through one user-selected folder with the format-1 encrypted operation model: one file per bundle, a one-time pairing code that delivers the workspace key, no device revocation; offered by the Mac desktop application and the iOS application, verified between two Tart Macs over a shared folder and on the test iPhone. | Portable synchronization | delivery | R1.5/W1 | `PLATFORM-001` | PR-PORTABLE-FOLDER |
| `LINUX-001` | Ship Posato for Linux from the shared desktop application as a `.deb` package: a systemd helper that blocks websites through `/etc/hosts` and ends chosen applications, applications chosen from desktop entries, the folder transport, and `posato-control --vm linux` with a Tart Ubuntu guest. | Platform coverage | delivery | R1.5/W2 | `SYNC-018` | PR-LINUX |
| `ANDROID-001` | Ship Posato for Android from the shared interface: websites blocked by a local DNS filter (`VpnService`), applications by usage access and a full-screen block screen, schedules, the folder transport, and `posato-control -t android` on an emulator, ending with a Linux, Android, and Mac workspace that pauses across all three. | Platform coverage | delivery | R1.5/W3 | `SYNC-018`, `LINUX-001` | PR-ANDROID |
| `SESSION-010` | Capture an adopted session's paused items after the same pass's pause-set changes are applied: a device that links while another device's pause is running adopts that session in the session phase, before the policy phase writes the set's websites locally, so it freezes its pre-link set and enforces nothing (`NOTHING_TO_ENFORCE`) until the next pause. Write the failing exchange test first, then recapture once after the policy phase or read the set's websites from the converged projection, without letting a policy-capacity failure suppress a session end. Present since sync began; also affects iCloud links. | Sessions and enforcement | delivery | R1.5/W4 | `SYNC-018` | PR-SESSION-ADOPTION |
| `TARGETS-010` | Refresh a pause set's "Apps on this Mac" or apps summary in the Pause sets list as soon as applications are chosen, removed, or cleared, without waiting for a policy change: emit the device-local application mappings' invalidations after every successful change on every platform, and check the other readers of those invalidations (Schedules set source, session recompose, Mac presence). Not a regression: published 1.3.0 behaves the same. | Target management | delivery | R1.5/W4 | `ANDROID-001` | PR-SET-SUMMARY |
| `TARGETS-011` | Treat a pause set with websites and no apps chosen on this device as complete: drop the warning "Apps aren't chosen for this set on this Mac. The pause includes its websites." from session Review and schedule rows on every platform, keep **Choose apps** reachable in the set, and update `DESIGN.md` readiness. A set with neither still offers **Add websites or apps**. | Target management | delivery | R1.5/W4 | `ANDROID-001` | PR-SET-READINESS |
| `MACOS-028` | Open the Mac window at its normal size every time: reproduce in a Tart clone when Posato shows a tiny window with only its toolbar (a launch, a login launch, reopening from the menu bar, or a restore after the window was closed), fix it test first, and verify each opening path. | Platform coverage | delivery | R1.5/W4 | None | PR-MAC-WINDOW-SIZE |
| `SYNC-023` | Show when this device last synchronized: the date and time of the last completed exchange in the synchronization section on every platform, for iCloud and folder workspaces, kept on the device and never synchronized; the state for no sync yet and for a failing sync stays as it is. | Portable synchronization | delivery | R1.5/W4 | `SYNC-018` | PR-LAST-SYNC |
| `TARGETS-012` | Duplicate a pause set from its menu: a new set named "<name> (copy)" with the same websites through the existing set authoring and its limit of 10, and this device's chosen apps copied locally; other devices choose their own apps as for any new set. | Target management | delivery | R1.5/W4 | `ANDROID-001` | PR-SET-DUPLICATE |
| `SYNC-024` | Keep the first pause set's name and the default set when this device creates a new workspace: a rename made before linking was never recorded as a change, and linking deliberately skips the first set's name, so the merge replaced "Focus" with the unnamed "My set" (observed on Android, shared code for iCloud and folder links). Write the failing link test first; joining an existing workspace keeps today's rule. | Portable synchronization | delivery | R1.5/W4b | `SYNC-018` | PR-FIRST-SET-NAME |
| `SYNC-025` | Say "folder" instead of "iCloud" in folder mode: the retry, running, and connected messages fall through to iCloud wording, and a folder holding a copy of a workspace this device removed shows "Sync with iCloud didn't finish." instead of a lasting explanation. | Portable synchronization | delivery | R1.5/W4b | `SYNC-018` | PR-FOLDER-WORDING |
| `SYNC-026` | Rebuild the expanded Folder sync row around two steps done in either order, choose the shared folder and scan the QR code with the phone's camera or type the code, joining as soon as both are done and waiting with an explanation while the folder catches up; the linked state keeps one primary action (idea 48, clickable mockup accepted 2026-10-21). | Portable synchronization | delivery | R1.5/W4b | `SYNC-025` | PR-FOLDER-JOIN |
| `ANDROID-002` | Show the Android block screen in the system's Dark Mode with Posato's dark palette instead of fixed light colors. | Platform coverage | delivery | R1.5/W4b | `ANDROID-001` | PR-ANDROID-BLOCK-DARK |
| `LINUX-002` | Follow the desktop's light or dark setting on Linux by reading the GNOME color scheme, live, without a new setting (idea 49). | Platform coverage | delivery | R1.5/W4b | `LINUX-001` | PR-LINUX-THEME |
| `TARGETS-007` | Export pause sets with their websites, the default set, and schedules to a file and import them on any device, as a one-time copy rather than sync: export offers an optional code that encrypts the file; import shows a summary and a clear choice between adding (combine sets with the same name, or keep them separate) and replacing, within the 10-set limit; app choices stay on each device; imported changes enter sync like any edit (idea 14, clickable mockup accepted 2026-10-21). | Target management | delivery | R1.5/W4b | `TARGETS-012` | PR-EXPORT-IMPORT |
| `DESIGN-005` | Warm the light palette on every platform: surface #F4F3EB with the surrounding container, outline, and background tones shifted to read against it, the Android block screen window and posato.app's light theme to match, and `DESIGN.md` updated (idea 51, tested on a Mac and an iPhone before acceptance). | Platform coverage | delivery | R1.5/W4b | `ANDROID-002` | PR-WARM-LIGHT |
| `LINUX-003` | Keep Posato for Linux running after its window closes, with an icon in the status area like the Mac menu bar and a start at login, so schedules start while the window is closed; verified on Ubuntu 24.04 (idea 50). | Platform coverage | delivery | R1.5/W4b | `LINUX-001` | PR-LINUX-BACKGROUND |
| `DOCS-006` | Prepare the public packaging for 1.5 without publishing it: move ADR 0010's limits to the limits page and mark Linux and Android available; describe 1.5, Linux, Android, and folder sync in the README and on `posato.app` with install steps for the `.deb` and the APK; capture Android and Linux screenshots; update the App Store description and What's New for folder sync and joining by link; and draft the GitHub release notes. | Release readiness | delivery | R1.5/W5 | `ANDROID-001`, `SESSION-010`, `TARGETS-010`, `TARGETS-011`, `MACOS-028`, `SYNC-023`, `TARGETS-012`, `SYNC-024`, `SYNC-025`, `SYNC-026`, `ANDROID-002`, `LINUX-002`, `LINUX-003`, `TARGETS-007`, `DESIGN-005` | PR-RELEASE-1-5-MEDIA |
| `RELEASE-007` | Verify and publish Posato 1.5.0: the macOS release through the `MACOS-011` update path, the iOS build to App Review, and on GitHub Releases the Linux `.deb` for arm64 and x86-64 (x86-64 built and smoke-tested on a GitHub Actions runner) and a release-signed Android APK whose signing key stays outside Git. | Release readiness | delivery | R1.5/W6 | `SYNC-018`, `LINUX-001`, `ANDROID-001`, `SESSION-010`, `TARGETS-010`, `TARGETS-011`, `MACOS-028`, `SYNC-023`, `TARGETS-012`, `SYNC-024`, `SYNC-025`, `SYNC-026`, `ANDROID-002`, `LINUX-002`, `LINUX-003`, `TARGETS-007`, `DESIGN-005`, `DOCS-006` | PR-RELEASE-1-5 |

## Release 1.5.1: the Mac keeps talking to its helper

Theme: a fix release for Mac only. Every Mac that runs Posato for a few days
lost its helper (`MACOS-030`), which kept restrictions after a pause and
refused updates. iOS, Linux, and Android do not change; their 1.5.0
packages stay current.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `MACOS-030` | Keep the Mac talking to its helper for as long as Posato runs: start a new helper connection before the 256-operation bound of ADR 0004 on both the application side and the helper's daemon side, without ending the helper process, its proxy relay, or its lease during a pause; restrictions end when the pause ends; an update is refused only while restrictions are held. Prove it with a test that sends more than 256 operations to the real helper in a Tart VM. | Sessions and enforcement | delivery | R1.5.1/W1 | None | PR-MAC-HELPER-CONNECTION |
| `RELEASE-008` | Verify and publish Posato 1.5.1 for Mac: notarized arm64 and Intel builds through the `MACOS-011` update path, with the 1.5.0 Linux and Android packages attached again unchanged, so the latest release still offers every platform. | Release readiness | delivery | R1.5.1/W2 | `MACOS-030` | PR-RELEASE-1-5-1 |

## Release 1.5.2: the Mac recovers on its own

Theme: a fix release for Mac only. On 1.5.1 a pause still lost its
restrictions when the Mac was busy for a few seconds, and Posato waited for
the person to press **Retry** although it could apply again by itself
(idea 55). The red notice should mean that only the person can help. iOS,
Linux, and Android do not change; their 1.5.0 packages stay current.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `MACOS-031` | Keep a pause's restrictions when the Mac is busy for a few seconds: one late lease renewal or Status answer does not end the helper, the daemon does not exit while a new helper connects to it, and while restrictions do not apply the session screen never says they are active. Prove it in a Tart VM by delaying the daemon beyond the renewal deadline during a pause. High-risk (ADR 0004 lease). | Sessions and enforcement | delivery | R1.5.2/W1 | None | PR-MAC-HELPER-RESILIENCE |
| `MACOS-029` | The Mac applies restrictions again by itself whenever it can, and shows the red notice only when it cannot recover without the person: with administrator approval on and automatic-start consent valid, a pause received from another device, after sleep, after a login launch, and after the helper or its daemon was lost during a pause applies by itself with bounded retries, without a notice in between; "Restrictions need attention" or **Resume restrictions** remains only when approval is off, the grant is missing, or the retries fail. Includes `SESSION-009` (the notice left after an unresumed session from another device ends). Amends ADR 0004 "Who may request it" and the `DESIGN.md` rule that synchronization and a login launch never apply by themselves, both with the maintainer's acceptance. High-risk plan review (authorization). | Sessions and enforcement | delivery | R1.5.2/W1 | None | PR-MAC-SELF-RECOVERY |
| `RELEASE-009` | Verify and publish Posato 1.5.2 for Mac: notarized arm64 and Intel builds through the `MACOS-011` update path, with the 1.5.0 Linux and Android packages attached again unchanged. | Release readiness | delivery | R1.5.2/W2 | `MACOS-031`, `MACOS-029` | PR-RELEASE-1-5-2 |

## Release 1.6: Posato on Windows, and more ways to pause

Theme: Posato runs on Windows as it does on Linux: the same interface and
folder synchronization, websites and chosen applications blocked during a
pause, a status-area icon, and a start at login. The person also gains a
break in a scheduled pause and a website that opens briefly during a pause,
and a calm history of pauses is decided. The
[Windows spike](https://github.com/dees91/posato/pull/206)
showed that every Windows change can be verified in a Tart guest with no
person present. The release also speaks Polish by following the system
language, with no in-app language switch.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `ONBOARDING-005` | Name the right setup and device on every platform: the last onboarding step says "Website pauses are unavailable until Mac setup is complete." on every platform but iOS, and a desktop that is not Linux calls itself a Mac (idea 56). Reproduce it in a Tart Ubuntu clone and on the Android emulator first, then give each platform its own notice and device name, Windows included. | Platform coverage | delivery | R1.6/W1 | None | PR-PLATFORM-COPY |
| `SCHEDULE-007` | Take a break in a running scheduled pause: release restrictions for a chosen number of minutes and apply them again automatically until the occurrence's end, on every device of the workspace; decide how many breaks and how long, beside the early-end friction of `SESSION-005` (idea 45). | Schedules | delivery | R1.6/W1 | None | PR-SCHEDULE-BREAK |
| `TARGETS-013` | Decide and deliver a website that opens briefly every N minutes during a pause (for example once an hour for 5 minutes, configurable): the rule in the synchronized model, enforcement timers on each platform, and the pause page's way to open it; the decision is accepted before delivery starts (idea 46). | Target management | delivery | R1.6/W1 | None | PR-TIMED-WEBSITE |
| `HISTORY-001` | Decide a calm history of pauses kept as planned, such as a quiet calendar of days, without streaks, scores, or shame: what is recorded, whether it stays on the device or synchronizes, and the `DESIGN.md` brand rule; end with a product and privacy decision and a delivery plan (idea 47). | Product discovery | discovery | R1.6/W1 | None | PR-HISTORY-DECISION |
| `I18N-001` | Ship Posato in Polish as the first additional language, following the system language with no in-app switch: the whole UI on Mac, iOS, Linux, Android, and Windows, Polish plural forms, the macOS pause page, iOS permission descriptions, date and time formatting, the App Store listing and screenshots, and a Polish posato.app including the privacy policy. A narrow `AGENTS.md` exception lets an agent author those localized product resources; the maintainer reviews that copy in the pull request. Verification selects Posato controls by identifier, so recipes do not depend on English labels. | Platform coverage | delivery | R1.6/W1 | None | PR-I18N-PL |
| `PLATFORM-002` | Ship Posato for Windows 11 on arm64 and x64 at parity with Linux: the shared interface and folder synchronization, websites and chosen applications blocked during a pause by a Windows service installed once with administrator approval, applications chosen from the Start menu, the status icon and start at login on a resident-window core extracted from the Mac and Linux code so each platform keeps only its native layer (folded `PLATFORM-003`, idea 53; Mac and Linux behave as before in a Tart clone of each), signed MSI packages, and `posato-control -t windows` driving a Tart Windows guest, ending with a Windows, Linux, and Mac workspace that pauses across all three. Amends ADR 0010 with the Windows row, with the maintainer's acceptance. High-risk plan review (privileged service, signing). | Platform coverage | delivery | R1.6/W2 | `ONBOARDING-005` | PR-WINDOWS |
| `DOCS-007` | Prepare the public packaging for 1.6 without publishing it: the limits page, README, and `posato.app` with Windows install steps and screenshots, the new pause features in the store text and What's New, and draft release notes. Polish pages and the Polish listing stay with `I18N-001`; this row adds only the 1.6 changes, in both languages. | Release readiness | delivery | R1.6/W3 | `ONBOARDING-005`, `SCHEDULE-007`, `TARGETS-013`, `PLATFORM-002`, `I18N-001` | PR-RELEASE-1-6-MEDIA |
| `RELEASE-010` | Verify and publish Posato 1.6.0: the macOS release through the `MACOS-011` update path, the iOS build to App Review, and on GitHub Releases the Linux `.deb` packages, the Android APK, and the Windows MSI packages for arm64 and x64 (x64 built and smoke-tested on a GitHub Actions runner). Verifies on iOS, Linux, and Android the shared session changes those builds have not shipped since 1.5.0: `PauseClaims` and `EnforcementPort.replace` (`MACOS-030`) and the `SESSION-009` branch of `clearAfterEnd` (`MACOS-029`). | Release readiness | delivery | R1.6/W4 | `ONBOARDING-005`, `SCHEDULE-007`, `TARGETS-013`, `PLATFORM-002`, `DOCS-007`, `I18N-001` | PR-RELEASE-1-6 |

## Backlog

Rows without a release. Each names what would let the maintainer assign it.
The idea numbers refer to the wiki idea queue.

| Task | Outcome | Epic | Origin | What unblocks assignment |
| --- | --- | --- | --- | --- |
| `FAMILY-001` | Decide whether a parent-and-child use case belongs in Posato: device ownership, consent, access boundaries, and privacy. | Product discovery | Idea 2 | A product decision that the personal-use model may extend |
| `FILTER-001` | Decide whether reducing advertising belongs in Posato and which coverage is useful and feasible. | Product discovery | Idea 3 | A product decision on scope beyond blocking chosen targets |
| `RESEARCH-001` | Compare the Focusly extension's interactions and features with Posato and list the ones worth adopting. | Product discovery | Idea 4 | Any planning checkpoint; cheap |
| `FILTER-002` | Decide how to reduce distractions within YouTube, such as Shorts and recommendations, and on which surfaces. | Product discovery | Idea 5 | `FILTER-001` or a separate product decision |
| `FILTER-003` | Decide whether a session can silence notifications from chosen applications and websites while the applications stay usable, on iOS and macOS: establish what the platforms allow a third party, including whether a Screen Time shield silences notifications and how browser web push can be reached; end with a product decision and, if feasible, a delivery plan. Preliminary. | Product discovery | Idea 17 | A feasibility result showing a supported mechanism on at least one platform |
| `MACOS-017` | Decide whether Posato supports Firefox on macOS under a revised ADR 0005 browser promise: presentation through a Posato extension, its signing and distribution through addons.mozilla.org, the fixed loopback rendezvous port against the current per-session port and its fail-closed conflict, This Mac setup guidance, and verification in Tart; end with the proposed ADR 0005 revision and a delivery plan for `MACOS-023`. | Sessions and enforcement | macOS enforcement follow-up; PRs #110 and #114 closed as reusable material | A planning checkpoint for a later release (`user-confirmed`, 2026-10-02) |
| `MACOS-018` | Detect or disclose iCloud Private Relay before a session applies proxy settings. | Sessions and enforcement | Open question in the macOS enforcement topic | A supported detection route or a decision to disclose only |
| `MACOS-019` | Re-evaluate App Sandbox for the macOS application if a later decision replaces the root daemon and Authorization Services mechanism. | Sessions and enforcement | ADR 0004 deferred decision | A decision that replaces the root daemon; ADR 0009 (`MACOS-012`) kept it |
| `MACOS-023` | Deliver Firefox support on macOS under the ADR 0005 revision and delivery plan accepted in `MACOS-017`, continuing from the PR #110 spike. | Sessions and enforcement | `MACOS-017` | An accepted `MACOS-017` decision and a planning checkpoint |
| `IOS-005` | Settle iOS reinstall behavior and the lifecycle of an application selection that becomes invalid. | Sessions and enforcement | `IOS-001` and iOS enforcement open questions | Evidence from support or a reproduction |
| `IOS-008` | Let one **Remove workspace** press on the iPhone finish a removal when the zone has a long history: `IosCloudKitMailboxProvider` caps a press at `MAX_REMOVAL_CALLS` (10), as the Mac did before `SYNC-021`. Reproduce it on the test iPhone with the `SYNC-021` fixture and apply the same progress-bounded continuation and progress state. | Sessions and enforcement | Idea 34; `SYNC-021` plan review | A reproduction on the test iPhone; release composition |
| `SYNC-022` | Finish what `SYNC-021` deferred: build the verification-only seeding operation; rebuild the long-history fixture and show a pre-fix failure on it; one press in 3 fresh Tart clones, after a settled link, and across a window close and reopen; then clean the test Apple Account's zone with the `SYNC-021` controls and waits, and run the iPhone check of `AC-05`. The zone is cleaned only after seeding exists or after `IOS-008` has reproduced on the current history. Also decide whether the first sync after a link should page a long history in larger passes (idea 35). | Sessions and enforcement | Idea 35; `SYNC-021` deferrals | A release composition; the test account's long history was removed on 2026-10-21 (`SYNC-024` verification), so it must be rebuilt first |
| `SESSION-008` | Mac VoiceOver reads full duration labels: find out what VoiceOver on the Mac speaks for the quick lengths of a manual pause, and if it speaks "1 h", make it read "1 hour" … "8 hours" as iOS does, keeping the visible labels. | Sessions and enforcement | Idea 36; `SESSION-007` | A release composition |
| `SESSION-005` | Add stronger, deliberately slower early-end friction as an optional setting. | Sessions and enforcement | MVP scope Later | A product decision with the accepted friction model |
| `SYNC-019` | Offer recovery after all workspace keys are lost, without a product account. | Portable synchronization | MVP scope Later | `SYNC-018` or an Apple-only recovery design |
| `QUALITY-008` | Decide whether golden or automated UI tests join the quality gate now that the interface is stable, and with which tool. | Verification | Engineering quality contract post-MVP decision | Two releases of interface stability |
| `QUALITY-009` | Decide whether hosted CI returns for pull requests and whether external contributions are accepted, with the Actions budget and review load that implies. | Verification | First-release readiness policy | Maintainer capacity decision |
| `QUALITY-010` | Let an agent verify every task without the maintainer: Posato on macOS in Tart virtual machines and on a dedicated physical test iPhone, both on a dedicated test Apple Account, with every system prompt, permission, and picker driven by the verification driver after one-time setup, including observing actual website and application blocking and unblocking. | Verification | Idea 10; absorbs `QUALITY-006` (pull request #52 discussion) | Passing go/no-go measurements (CloudKit in a VM; Screen Time consent and the application picker through XCUITest), plus the maintainer's test account and dedicated iPhone |
| `QUALITY-011` | Decide whether to extract the Tart virtual machine layer and the iOS system-dialog driver of `posato-control` into a standalone, openly licensed tool for macOS and iOS development testing: an application descriptor instead of Posato constants, system-dialog definitions as data per macOS version and language, verification beyond one Mac and one iPhone, dependency licenses, and who maintains it; end with a decision and, if accepted, an extraction plan. Preliminary. | Verification | Idea 16; `QUALITY-010` outcome | `posato-control` stable across one release cycle and a maintainer decision to maintain a public tool |
| `PAUSE-001` | Decide whether the pause page should offer a useful local activity, from the session's stated intention up to user-provided flashcards, within the privacy boundary, the self-contained pause page of `DESIGN-003`, and the iOS shield limits; end with a product decision and a delivery plan. The layers in idea 11 guide it: useful with no setup first, then cards from one open deck format (in-app editor, CSV and Anki import, a chatbot prompt, and a watched folder for learning agents), with spaced review only after the privacy decision. | Product discovery | Idea 11 | A product decision that the pause moment is in scope |
| `TARGETS-008` | Decide a quick way to share saved websites with a device on a different Apple Account, such as AirDrop of a `TARGETS-007` file or a QR code: privacy, one-time or ongoing sharing, and the relation to `SYNC-018`; end with a product decision. Preliminary. | Target management | Idea 15 | A product decision on sharing beyond one Apple Account |
| `MACOS-025` | Let the macOS helper take a new configuration during a running pause without clearing it first, so no browser request passes between the clear and the apply: amend ADR 0004 with an atomic replacement over the existing grant, with its own security review, and prove it with the `SCHEDULE-004` probe that saw a 0.2-0.25 s gap. | Sessions and enforcement | Idea 25; `SCHEDULE-004` measurement | A planning checkpoint; the accepted 1.3 known limit makes it non-urgent |
| `QUALITY-012` | Decide a development-only time control for verification, such as a clock offset or shortened schedule and session minimums behind a launch argument that release builds ignore, so scheduled and synchronized acceptance runs take minutes instead of hours; define what such runs still prove and what stays on real time. | Verification | Idea 26; `SCHEDULE-004` retro | A maintainer decision on a test seam in product code and its safety in release builds |
| `QUALITY-013` | Let `posato-control flow onboard -t linux` finish when Posato's pausing service is already installed in the clone: the onboarding page then shows **Continue** instead of **Allow**, and the flow fails waiting for **Allow**. Press whichever of the two is shown, as `flow icloud link` does with its buttons, and cover a rerun on the same clone. | Verification | Idea 52; release 1.5 retrospective | Any planning checkpoint; cheap |
| `PLATFORM-004` | Give the desktop helpers one least-privilege rule on the Mac, Linux, and Windows: state it in ADR 0010, then narrow each platform's mechanism to it (on Windows, for example, a restricted service SID where Windows Filtering Platform allows it) and verify each in its Tart guest. High-risk. | Platform coverage | Idea 57; `PLATFORM-002` review | A release that changes a desktop helper |
| `SESSION-011` | Clear the expired session that the Linux and Windows helpers keep reporting after a pause ends while Posato is closed: the shared session code acknowledges an expiry only while its local record is still active, so the next launch never acknowledges it. One shared fix, verified on Linux and Windows. | Sessions and enforcement | Idea 58; `PLATFORM-002` review | Any planning checkpoint; no effect on enforcement |
| `REDIRECT-001` | Decide whether Posato offers host redirect rules beside blocking. First settle the model: always-on rules in a separate setting, or redirects inside sessions and pause sets, with the UX of each compared so that blocking and redirecting stay clear. Then analyze the technical approach on macOS (proxy and browser presentation; for always-on rules also failure, sleep, VPN and proxy coexistence, and privacy outside sessions) and the feasibility on Linux, Windows, Android, and iOS. Settle host versus path rules, loop protection, and whether rules sync. End with a product decision, the proposed ADR 0004 and ADR 0005 revisions if needed, and a delivery plan. Preliminary. | Product discovery | Idea 59 | A product decision that redirects are in scope |

## Coverage matrix

| Accepted outcome | Owning tasks | Terminal evidence |
| --- | --- | --- |
| Everyday frictions removed on both platforms | `SESSION-004`, `ONBOARDING-003`, `TARGETS-006`, `IOS-004` | Driver runs on the supported Mac, Simulator, and iPhone, plus iPad screenshots |
| Supported macOS update path | `MACOS-010`, `MACOS-011` | Accepted ADR 0004 revision, update from a published notarized candidate to a newer one with proxy ownership restored |
| Verified support matrix | `QUALITY-007` | macOS 15 virtual machine and iOS 18 physical runs, availability page updated |
| Session without the main window and without repeated prompts | `MACOS-012`, `MACOS-013`, `MACOS-014` | Accepted ADR 0003 and ADR 0004 revisions, physical menu bar start, end, relaunch, login, and revocation evidence |
| One guided Mac setup | `ONBOARDING-004` | In a Tart clone: setup from onboarding, Finish setup, and the upgrade offer; resume after interruption; verified completion; revocation in This Mac |
| Session notifications | `NOTIFY-001` | Physical permission flow, start and end notifications on both platforms |
| Intel Macs | `MACOS-015` | Accepted ADR 0003 and ADR 0008 revisions, notarized x86-64 candidate verified by `posato-control` under Rosetta in an arm64 macOS 13 Tart guest |
| Shared recurring schedules | `SCHEDULE-001`, `SCHEDULE-002` | Accepted rules and security-reviewed authorization revision; Mac VM and test-iPhone runs for start/end, offline execution of known plans, synchronization, missing permissions, skipping, early end, restart and Mac catch-up |
| iPhone session kept across a relaunch | `IOS-006` | Repeated fast and slow relaunches on the test iPhone with restrictions observed after each |
| Public packaging for 1.2 | `DOCS-003` | Media rendered within budget from recorded captures, README and site built, store text and screenshots ready for upload |
| System back gestures | `NAV-001` | Test-iPhone and iPad edge-swipe back and Mac keyboard and trackpad back in Tart, with the explicit **Back** actions kept |
| Reported enforcement, schedule, and publication defects | `MACOS-022`, `SCHEDULE-005`, `SCHEDULE-006`, `SYNC-020`, `MACOS-020`, `MACOS-021`, `MACOS-024`, `NAV-002`, `SESSION-006`, `IOS-007`, `MACOS-027`, `SYNC-021` | Tart and test-iPhone runs that reproduce each defect before the fix and show the corrected behavior after it |
| New homepage and product line | `WEB-002` | The chosen line in `DESIGN.md`, the site, README, and App Store subtitle; the site built and checked on a phone and a wide screen |
| Public packaging for 1.3 | `DOCS-004` | Media rendered from recorded captures, README and site built, store text and screenshots ready for upload |
| A native feel on every Apple device | `DESIGN-004` | The direction in `DESIGN.md`; Simulator iPhone and iPad, Mac VM, and test-iPhone runs of the adapted scenarios |
| In-app update without the quit question | `MACOS-026` | A candidate-channel update in Tart between two builds with the change, with a schedule on and during a pause, that asks nothing |
| Longer quick choices for a manual pause | `SESSION-007` | The final set in `DESIGN.md`; pauses started with a long choice and with **Until end of day** in a Mac VM and on the test iPhone, each with the expected end |
| Public packaging for 1.4 | `DOCS-005` | Media rendered from recorded captures, README and site built, store text and screenshots ready for upload |
| Reusable pause sets for manual sessions and schedules | `SCHEDULE-003`, `SCHEDULE-004` | Accepted remaining decisions; Mac VM and test-iPhone proof of migration, per-set selections, overlap, blocking and release, offline execution, and synchronized definitions with local app choices |
| Linux and Android with folder synchronization | `PLATFORM-001`, `SYNC-018`, `LINUX-001`, `ANDROID-001` | Accepted ADR 0010; `posato-control` runs on two Tart Macs, a Tart Ubuntu guest, and an Android emulator sharing one folder, with blocking observed on each |
| A device that joins during a pause enforces it | `SESSION-010` | The failing exchange test first; a device linked during another device's pause blocks the set's websites without waiting for the next pause |
| Pause set summaries refreshed at once | `TARGETS-010` | The summary changes right after apps are chosen, removed, or cleared, in a Mac VM, on the test iPhone, and on Android |
| Smaller frictions before 1.5 | `TARGETS-011`, `MACOS-028`, `SYNC-023`, `TARGETS-012` | A set without apps shows no warning; the Mac window opens at its size on every path in a Tart clone; the last sync time on each platform; a duplicated set with its websites and this device's apps |
| Fixes and improvements found while packaging 1.5 | `SYNC-024`, `SYNC-025`, `SYNC-026`, `ANDROID-002`, `LINUX-002`, `LINUX-003`, `TARGETS-007`, `DESIGN-005` | A renamed first set keeps its name when a device creates a workspace; folder wording without iCloud; joining in either order on a phone and a computer; the block screen in Dark Mode; Linux in light and dark; Linux schedules starting with the window closed in a Tart Ubuntu clone; an export imported on a second Mac with add and replace; the warm light palette on each platform |
| Public packaging for 1.5 | `DOCS-006` | Limits page and README updated, site built, Android and Linux screenshots, store text ready for upload, release notes drafted |
| Posato on Windows | `PLATFORM-002` | Accepted ADR 0010 Windows row; `posato-control` runs in a Tart Windows guest with website and application blocking observed, the status icon and the start at login, and a Windows, Linux, and Mac workspace sharing one folder; Mac and Linux unchanged on the shared resident core; MSI install and removal on arm64 and x64 |
| Each platform names its own setup and device | `ONBOARDING-005` | Linux, Android, and Windows runs that reproduce the Mac wording before the fix and show each platform's own after it |
| Posato in Polish | `I18N-001` | The apps, pause page, permissions, and dates follow the system language, including a per-app language; `/pl/` on posato.app and a `pl-PL` store listing are ready without publishing; an English guest still completes a session |
| More ways to pause | `SCHEDULE-007`, `TARGETS-013`, `HISTORY-001` | A break and a briefly opened website applied and restored on each platform in Tart and on the test iPhone; an accepted history decision with a delivery plan |
| Public packaging for 1.6 | `DOCS-007` | Limits page and README updated, site built, Windows screenshots, store text ready for upload, release notes drafted |
| Published releases | `RELEASE-003`, `RELEASE-004`, `RELEASE-005`, `RELEASE-006` | GitHub Release with checksums, App Review outcome, availability page and site updated |

## Manual and physical gates

| Gate | Owner | Completion rule |
| --- | --- | --- |
| App Store listing live for 1.0.0 | `RELEASE-002` closeout | The listing is live and the badge pull request merged before `RELEASE-003` submits an iOS update. |
| Update feed hosting and signing keys | `MACOS-010`, `MACOS-011` | Any update signing key stays outside Git; the feed is served from the repository's release process or `posato.app`; a notarized candidate updates itself on the supported Mac. |
| macOS 15 virtual machine and iOS 18 iPhone | `QUALITY-007` | The maintainer provides the virtual machine image and the iOS 18 device, or the row records the gap on the availability page. |
| Persistent authorization security review | `MACOS-014` | An independent review of the ADR 0004 revision passes before implementation. |
| Automatic scheduled Apply security review | `SCHEDULE-001` | An independent review of the explicit-consent and automatic-start amendments passes, and the maintainer accepts them, before `SCHEDULE-002` implements them. |
| Windows code signing | `PLATFORM-002` | The maintainer provides an Authenticode signing identity, such as a certificate or a cloud signing service, or accepts unsigned packages with SmartScreen's warning recorded on the limits page, before `RELEASE-010` publishes. |
| Windows image for verification | `PLATFORM-002` | The maintainer accepts the license of the Windows 11 image that the Tart guests run beyond the spike, or provides another one. |
| App Review per iOS release | `RELEASE-003`–`RELEASE-006` | The submitted build is approved or the row records the rejection and its clearing condition. |

No credential, signing identity, update signing key, provisioning profile,
private device ID, raw capture, opaque application token, real-person domain,
or account-specific value enters tracked evidence.

## Activation rule

Acceptance of this document makes its rows planning authority, not
implementation authorization. Start a row only when the maintainer names it,
its release is composed, its dependencies and wave barrier are clear, and its
brief exists; then apply the proportional review tier and evidence rules in
`docs/tasks/README.md`. A discovery row ends when its decision is recorded and
accepted or rejected; it never starts the delivery row on its own.
