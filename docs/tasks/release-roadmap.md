# Posato Release Roadmap

## Status and authority

- **Status:** Accepted
- **Revision:** 21 (2026-10-09: `SYNC-021` joins release 1.4; `TARGETS-009` is dropped; amended 2026-10-09: backlog addition `IOS-008`)
- **Prepared:** 2026-09-18
- **Accepted:** 2026-09-18
- **Last amended:** 2026-10-09
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
  The 2026-10-09 backlog addition `IOS-008` (idea 34, `user-confirmed`
  through the `SYNC-021` plan review) changes no release.

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
- Update through `gh project` (`item-list`, `item-create`, `item-edit`,
  `item-archive`) with the `project` token scope. Views are created once in
  the GitHub interface and are not part of the mirror rule.
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
| `SESSION-005` | Add stronger, deliberately slower early-end friction as an optional setting. | Sessions and enforcement | MVP scope Later | A product decision with the accepted friction model |
| `SYNC-018` | Design the portable workspace over one user-selected synchronized folder with its own key delivery and membership. | Portable synchronization | Product framing later direction | A platform beyond Apple in scope |
| `SYNC-019` | Offer recovery after all workspace keys are lost, without a product account. | Portable synchronization | MVP scope Later | `SYNC-018` or an Apple-only recovery design |
| `PLATFORM-001` | Decide the order, enforcement mechanisms, privilege models, and shared UI for Android, Linux, and Windows. | Platform coverage | Availability page planned platforms | A product decision to leave the Apple-only release train |
| `QUALITY-008` | Decide whether golden or automated UI tests join the quality gate now that the interface is stable, and with which tool. | Verification | Engineering quality contract post-MVP decision | Two releases of interface stability |
| `QUALITY-009` | Decide whether hosted CI returns for pull requests and whether external contributions are accepted, with the Actions budget and review load that implies. | Verification | First-release readiness policy | Maintainer capacity decision |
| `QUALITY-010` | Let an agent verify every task without the maintainer: Posato on macOS in Tart virtual machines and on a dedicated physical test iPhone, both on a dedicated test Apple Account, with every system prompt, permission, and picker driven by the verification driver after one-time setup, including observing actual website and application blocking and unblocking. | Verification | Idea 10; absorbs `QUALITY-006` (pull request #52 discussion) | Passing go/no-go measurements (CloudKit in a VM; Screen Time consent and the application picker through XCUITest), plus the maintainer's test account and dedicated iPhone |
| `QUALITY-011` | Decide whether to extract the Tart virtual machine layer and the iOS system-dialog driver of `posato-control` into a standalone, openly licensed tool for macOS and iOS development testing: an application descriptor instead of Posato constants, system-dialog definitions as data per macOS version and language, verification beyond one Mac and one iPhone, dependency licenses, and who maintains it; end with a decision and, if accepted, an extraction plan. Preliminary. | Verification | Idea 16; `QUALITY-010` outcome | `posato-control` stable across one release cycle and a maintainer decision to maintain a public tool |
| `PAUSE-001` | Decide whether the pause page should offer a useful local activity, from the session's stated intention up to user-provided flashcards, within the privacy boundary, the self-contained pause page of `DESIGN-003`, and the iOS shield limits; end with a product decision and a delivery plan. The layers in idea 11 guide it: useful with no setup first, then cards from one open deck format (in-app editor, CSV and Anki import, a chatbot prompt, and a watched folder for learning agents), with spaced review only after the privacy decision. | Product discovery | Idea 11 | A product decision that the pause moment is in scope |
| `I18N-001` | Ship Posato in Polish as the first additional language, following the system language: the whole UI of both applications with Polish plural forms, the macOS pause page, iOS permission descriptions, and date and time formatting, plus the App Store listing and screenshots and a Polish posato.app including the privacy policy. It adds a narrow `AGENTS.md` exception so the agent can author localized product resources for the maintainer's approval, and keeps verification recipes independent of English labels. | Platform coverage | Idea 12 | Any planning checkpoint; the maintainer's time to review the Polish copy |
| `TARGETS-007` | Decide whether and how saved websites and application choices can be exported to and imported from a file: format, encryption, what an application choice can carry across devices, merge or replace, and sync interaction; end with a product decision and a delivery plan. Preliminary. | Target management | Idea 14 | A product decision that file transfer is in scope |
| `TARGETS-008` | Decide a quick way to share saved websites with a device on a different Apple Account, such as AirDrop of a `TARGETS-007` file or a QR code: privacy, one-time or ongoing sharing, and the relation to `SYNC-018`; end with a product decision. Preliminary. | Target management | Idea 15 | A product decision on sharing beyond one Apple Account |
| `MACOS-025` | Let the macOS helper take a new configuration during a running pause without clearing it first, so no browser request passes between the clear and the apply: amend ADR 0004 with an atomic replacement over the existing grant, with its own security review, and prove it with the `SCHEDULE-004` probe that saw a 0.2-0.25 s gap. | Sessions and enforcement | Idea 25; `SCHEDULE-004` measurement | A planning checkpoint; the accepted 1.3 known limit makes it non-urgent |
| `QUALITY-012` | Decide a development-only time control for verification, such as a clock offset or shortened schedule and session minimums behind a launch argument that release builds ignore, so scheduled and synchronized acceptance runs take minutes instead of hours; define what such runs still prove and what stays on real time. | Verification | Idea 26; `SCHEDULE-004` retro | A maintainer decision on a test seam in product code and its safety in release builds |

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
| Published releases | `RELEASE-003`, `RELEASE-004`, `RELEASE-005`, `RELEASE-006` | GitHub Release with checksums, App Review outcome, availability page and site updated |

## Manual and physical gates

| Gate | Owner | Completion rule |
| --- | --- | --- |
| App Store listing live for 1.0.0 | `RELEASE-002` closeout | The listing is live and the badge pull request merged before `RELEASE-003` submits an iOS update. |
| Update feed hosting and signing keys | `MACOS-010`, `MACOS-011` | Any update signing key stays outside Git; the feed is served from the repository's release process or `posato.app`; a notarized candidate updates itself on the supported Mac. |
| macOS 15 virtual machine and iOS 18 iPhone | `QUALITY-007` | The maintainer provides the virtual machine image and the iOS 18 device, or the row records the gap on the availability page. |
| Persistent authorization security review | `MACOS-014` | An independent review of the ADR 0004 revision passes before implementation. |
| Automatic scheduled Apply security review | `SCHEDULE-001` | An independent review of the explicit-consent and automatic-start amendments passes, and the maintainer accepts them, before `SCHEDULE-002` implements them. |
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
