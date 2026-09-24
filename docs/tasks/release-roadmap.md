# Posato Release Roadmap

## Status and authority

- **Status:** Accepted
- **Revision:** 3 (amended 2026-09-24: `QUALITY-010` started from the backlog by
  maintainer decision, without a release; it keeps its backlog row until a
  planning checkpoint assigns one)
- **Prepared:** 2026-09-18
- **Accepted:** 2026-09-18
- **Last amended:** 2026-09-24
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
  import, and sharing across Apple Accounts).

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
  Intel exclusion in ADR 0003 (`MACOS-015`, `MACOS-016`).

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

## Release 1.2: the Mac always at hand

Theme: let a session live on the Mac without the main window and without a
password prompt at every start. The three rows revise the same process and
authorization contracts, so they form one release.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `MACOS-012` | Decide how Posato stays present on macOS without its main window: a status-bar menu that shows the current session, starts or ends one, and opens the window on demand; whether the Compose Desktop process stays resident or session orchestration moves into a native helper; launch at login; and resource use. Propose the ADR 0003 and ADR 0004 revisions. High-risk. | Sessions and enforcement | discovery | R1.2/W1 | None | PR-MENU-BAR-DECISION |
| `MACOS-013` | Implement the accepted menu bar presence from `MACOS-012`, so blocking continues while the window is closed and the person can start, end, and inspect a session from the status bar. High-risk. | Sessions and enforcement | delivery | R1.2/W2 | `MACOS-012` | PR-MENU-BAR |
| `MACOS-014` | Reduce repeated administrator prompts at session start through a one-time opt-in with an explicit revocation path and authenticated, narrowly scoped helper requests, after a security review of the ADR 0004 revision that replaces the one-use Apply authorization. High-risk. | Sessions and enforcement | delivery | R1.2/W2 | `MACOS-012` | PR-MAC-AUTHORIZATION |
| `NOTIFY-001` | Deliver local notifications when a session starts or ends on iOS and macOS, with a permission flow, a preference, and no remote push or server. | Notifications | delivery | R1.2/W2 | `MACOS-012` | PR-LOCAL-NOTIFICATIONS |
| `RELEASE-004` | Verify the 1.2.0 candidates, publish the macOS release through the `MACOS-011` update path and GitHub Releases, and submit the iOS build to App Review. | Release readiness | delivery | R1.2/W3 | `MACOS-013`, `MACOS-014`, `NOTIFY-001` | PR-RELEASE-1-2 |

## Release 1.3: more Macs, more time

Theme: two independent directions that can run in parallel worktrees: Intel
Macs, and sessions that start on a timetable. Schedules follow the menu bar
work because a Mac schedule needs a resident process.

| Task | Outcome | Epic | Class | Wave | Direct dependencies | Integration group |
| --- | --- | --- | --- | --- | --- | --- |
| `MACOS-015` | Decide whether Posato supports Intel Macs and macOS 14: cost of the x86-64 Compose Desktop artifact and runtime, per-architecture native libraries, universal Swift helpers, two notarized DMGs, macOS 14 API availability, the verification driver on a second architecture, and the support horizon Apple gives Intel Macs and macOS 14; propose the ADR 0003 revision for a go or no-go. | Platform coverage | discovery | R1.3/W1 | None | PR-INTEL-DECISION |
| `MACOS-016` | Deliver the accepted Intel path from `MACOS-015`: build, sign, notarize, and publish the x86-64 release, verify it on the maintainer's 2019 MacBook Air, and update the availability page. High-risk. | Platform coverage | delivery | R1.3/W2 | `MACOS-015` | PR-INTEL-RELEASE |
| `SCHEDULE-001` | Decide the recurring session schedule: model and exceptions, additive operation vocabulary under ADR 0006, iOS scheduling through the Device Activity schedule, macOS scheduling through the resident process from `MACOS-013`, and what happens when a scheduled start meets an offline or missing device; end with a product decision and a delivery plan for the next release. | Schedules | discovery | R1.3/W1 | `MACOS-013` | PR-SCHEDULE-DECISION |
| `RELEASE-005` | Verify the 1.3.0 candidates, publish the macOS release for every accepted architecture, and submit the iOS build to App Review when it changed. | Release readiness | delivery | R1.3/W3 | `MACOS-016`, `SCHEDULE-001` | PR-RELEASE-1-3 |

## Backlog

Rows without a release. Each names what would let the maintainer assign it.
The idea numbers refer to the wiki idea queue.

| Task | Outcome | Epic | Origin | What unblocks assignment |
| --- | --- | --- | --- | --- |
| `SCHEDULE-002` | Deliver recurring schedules on both platforms per the `SCHEDULE-001` decision. | Schedules | Idea 1 | `SCHEDULE-001` decision accepted |
| `FAMILY-001` | Decide whether a parent-and-child use case belongs in Posato: device ownership, consent, access boundaries, and privacy. | Product discovery | Idea 2 | A product decision that the personal-use model may extend |
| `FILTER-001` | Decide whether reducing advertising belongs in Posato and which coverage is useful and feasible. | Product discovery | Idea 3 | A product decision on scope beyond blocking chosen targets |
| `RESEARCH-001` | Compare the Focusly extension's interactions and features with Posato and list the ones worth adopting. | Product discovery | Idea 4 | Any planning checkpoint; cheap |
| `FILTER-002` | Decide how to reduce distractions within YouTube, such as Shorts and recommendations, and on which surfaces. | Product discovery | Idea 5 | `FILTER-001` or a separate product decision |
| `MACOS-017` | Extend macOS browser coverage beyond Safari and Chrome Stable, starting with Firefox, under a revised ADR 0005 support contract. | Sessions and enforcement | macOS enforcement follow-up | A maintainer decision to widen the support promise |
| `MACOS-018` | Detect or disclose iCloud Private Relay before a session applies proxy settings. | Sessions and enforcement | Open question in the macOS enforcement topic | A supported detection route or a decision to disclose only |
| `MACOS-019` | Re-evaluate App Sandbox for the macOS application if a later decision replaces the root daemon and Authorization Services mechanism. | Sessions and enforcement | ADR 0004 deferred decision | `MACOS-012` outcome |
| `IOS-005` | Settle iOS reinstall behavior and the lifecycle of an application selection that becomes invalid. | Sessions and enforcement | `IOS-001` and iOS enforcement open questions | Evidence from support or a reproduction |
| `SESSION-005` | Add stronger, deliberately slower early-end friction as an optional setting. | Sessions and enforcement | MVP scope Later | A product decision with the accepted friction model |
| `SYNC-018` | Design the portable workspace over one user-selected synchronized folder with its own key delivery and membership. | Portable synchronization | Product framing later direction | A platform beyond Apple in scope |
| `SYNC-019` | Offer recovery after all workspace keys are lost, without a product account. | Portable synchronization | MVP scope Later | `SYNC-018` or an Apple-only recovery design |
| `PLATFORM-001` | Decide the order, enforcement mechanisms, privilege models, and shared UI for Android, Linux, and Windows. | Platform coverage | Availability page planned platforms | A product decision to leave the Apple-only release train |
| `QUALITY-008` | Decide whether golden or automated UI tests join the quality gate now that the interface is stable, and with which tool. | Verification | Engineering quality contract post-MVP decision | Two releases of interface stability |
| `QUALITY-009` | Decide whether hosted CI returns for pull requests and whether external contributions are accepted, with the Actions budget and review load that implies. | Verification | First-release readiness policy | Maintainer capacity decision |
| `QUALITY-010` | Let an agent verify every task without the maintainer: Posato on macOS in Tart virtual machines and on a dedicated physical test iPhone, both on a dedicated test Apple Account, with every system prompt, permission, and picker driven by the verification driver after one-time setup, including observing actual website and application blocking and unblocking. | Verification | Idea 10; absorbs `QUALITY-006` (pull request #52 discussion) | Passing go/no-go measurements (CloudKit in a VM; Screen Time consent and the application picker through XCUITest), plus the maintainer's test account and dedicated iPhone |
| `PAUSE-001` | Decide whether the pause page should offer a useful local activity, from the session's stated intention up to user-provided flashcards, within the privacy boundary, the self-contained pause page of `DESIGN-003`, and the iOS shield limits; end with a product decision and a delivery plan. | Product discovery | Idea 11 | A product decision that the pause moment is in scope |
| `I18N-001` | Ship Posato in Polish as the first additional language, following the system language: the whole UI of both applications with Polish plural forms, the macOS pause page, iOS permission descriptions, and date and time formatting, plus the App Store listing and screenshots and a Polish posato.app including the privacy policy. It adds a narrow `AGENTS.md` exception so the agent can author localized product resources for the maintainer's approval, and keeps verification recipes independent of English labels. | Platform coverage | Idea 12 | Any planning checkpoint; the maintainer's time to review the Polish copy |
| `NAV-001` | Move the screen stacks within each destination to Navigation 3 and support system back gestures: the interactive edge swipe on iPhone and iPad, and keyboard and trackpad back on the Mac. It keeps the explicit **Back** actions and the two-destination navigation accepted in `DESIGN.md`. | Platform coverage | Idea 13; `IOS-004` decision | Any planning checkpoint |
| `TARGETS-007` | Decide whether and how saved websites and application choices can be exported to and imported from a file: format, encryption, what an application choice can carry across devices, merge or replace, and sync interaction; end with a product decision and a delivery plan. Preliminary. | Target management | Idea 14 | A product decision that file transfer is in scope |
| `TARGETS-008` | Decide a quick way to share saved websites with a device on a different Apple Account, such as AirDrop of a `TARGETS-007` file or a QR code: privacy, one-time or ongoing sharing, and the relation to `SYNC-018`; end with a product decision. Preliminary. | Target management | Idea 15 | A product decision on sharing beyond one Apple Account |

## Coverage matrix

| Accepted outcome | Owning tasks | Terminal evidence |
| --- | --- | --- |
| Everyday frictions removed on both platforms | `SESSION-004`, `ONBOARDING-003`, `TARGETS-006`, `IOS-004` | Driver runs on the supported Mac, Simulator, and iPhone, plus iPad screenshots |
| Supported macOS update path | `MACOS-010`, `MACOS-011` | Accepted ADR 0004 revision, update from a published notarized candidate to a newer one with proxy ownership restored |
| Verified support matrix | `QUALITY-007` | macOS 15 virtual machine and iOS 18 physical runs, availability page updated |
| Session without the main window and without repeated prompts | `MACOS-012`, `MACOS-013`, `MACOS-014` | Accepted ADR 0003 and ADR 0004 revisions, physical menu bar start, end, relaunch, login, and revocation evidence |
| Session notifications | `NOTIFY-001` | Physical permission flow, start and end notifications on both platforms |
| Intel Macs | `MACOS-015`, `MACOS-016` | Accepted ADR 0003 revision, notarized x86-64 candidate verified on the 2019 MacBook Air |
| Schedule decision | `SCHEDULE-001` | Accepted product decision and delivery plan |
| Published releases | `RELEASE-003`, `RELEASE-004`, `RELEASE-005` | GitHub Release with checksums, App Review outcome, availability page and site updated |

## Manual and physical gates

| Gate | Owner | Completion rule |
| --- | --- | --- |
| App Store listing live for 1.0.0 | `RELEASE-002` closeout | The listing is live and the badge pull request merged before `RELEASE-003` submits an iOS update. |
| Update feed hosting and signing keys | `MACOS-010`, `MACOS-011` | Any update signing key stays outside Git; the feed is served from the repository's release process or `posato.app`; a notarized candidate updates itself on the supported Mac. |
| macOS 15 virtual machine and iOS 18 iPhone | `QUALITY-007` | The maintainer provides the virtual machine image and the iOS 18 device, or the row records the gap on the availability page. |
| Persistent authorization security review | `MACOS-014` | An independent review of the ADR 0004 revision passes before implementation. |
| 2019 MacBook Air | `MACOS-016` | The notarized x86-64 candidate passes the accepted flow on the maintainer's device. |
| App Review per iOS release | `RELEASE-003`–`RELEASE-005` | The submitted build is approved or the row records the rejection and its clearing condition. |

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
