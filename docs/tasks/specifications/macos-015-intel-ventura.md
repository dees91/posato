# `MACOS-015`: Posato on Intel Macs running macOS 13 Ventura

- **Review tier:** High-risk
- **Tier reason:** Revises the ADR 0003 platform baseline and the ADR 0008
  update channel and adds a second signed and notarized architecture.
- **Dependencies:** None (release 1.3, wave 1). `DOCS-004` and `RELEASE-005`
  wait for this row.
- **Integration group:** PR-INTEL-RELEASE
- **Authority:** [Release roadmap](../release-roadmap.md) revision 13, row
  `MACOS-015` (absorbs `MACOS-016`); maintainer named the row on 2026-09-29.
  The evaluation already on this branch is summarized in the
  [execution record](../executions/macos-015-intel-ventura.md).

## Outcome

Posato 1.3 can ship a notarized x86-64 build for Intel Macs running macOS 13
Ventura or later that passes the same accepted flow as the arm64 build.

## Boundaries

- **Accepted decisions** (`user-confirmed`, 2026-09-29; the maintainer
  accepted them in advance, so the revisions below need no further approval
  unless the implementation departs from them):
  - ADR 0003: an x86-64 build for Intel Macs on macOS 13 or later; the
    x86-64 build refuses to run under Rosetta on Apple silicon and points to
    the Apple silicon build; the arm64 minimum also drops to macOS 13 and
    is published as fully supported without separate macOS 13 or 14
    verification, a maintainer exception to the evidence rule recorded in
    the ADR; Intel on macOS 13 stays supported while the toolchain (Xcode,
    Compose, JDK) builds it, checked every release, with removal announced
    one release ahead and the end of Apple's macOS 13 security updates stated
    on the availability page.
  - ADR 0008, with the `TB-08`/`T-13` threat-model update: a separate
    `appcast-intel.xml` for the x86-64 build in the same GitHub Release;
    `appcast.xml` stays arm64-only; both builds of a release share one
    `CFBundleVersion`.
  - Verification (2026-09-30): the 2019 MacBook Air is shared with another
    person's account, so it is not a test Mac. The x86-64 build is verified
    under Rosetta in an arm64 macOS 13 Tart guest. A verification-only build
    switch lifts the Rosetta refusal; release builds never carry it. No
    physical Intel Mac and no `AGENTS.md` exception are used.
- The feed that installed 1.1 and 1.2 arm64 clients read never offers an
  x86-64 build, and the x86-64 channel never offers an arm64 build or one with
  a higher minimum macOS.
- This row delivers release tooling and verified candidates on a test feed;
  only `RELEASE-005` publishes anything public.
- macOS 13 compatibility stays enforced after this row: the quality gate
  compiles the macOS 13 native parts, and later macOS rows inherit that.
  The x86-64 Java runtime is pinned to the arm64 Temurin version and
  obtainable by any contributor.
- `posato-control` gains a macOS 13 VM line, like the macOS 15 legacy line,
  with Rosetta installed in the guest.
- Setup must work from a clean state without a restart, or the missed daemon
  submission seen in the evaluation becomes a named, disclosed risk. The
  driver never runs `sfltool resetbtm`.
- Non-goals: macOS 12 or older, and the deferred CloudKit bootstrap timeout
  unless it reproduces.

## Acceptance

- `AC-01` — The ADR 0003 and ADR 0008 revisions state the accepted
  decisions above.
- `AC-02` — In an arm64 macOS 13 Tart guest, driven by `posato-control` from a
  clean state, the x86-64 verification build under Rosetta passes the macOS 15
  flow of the availability page (installation, setup, pause, a schedule after
  restart with login launch, start notice, early end from the menu bar), plus
  the pause page, the application picker, helper removal, and an update
  between two x86-64 candidates on the Intel feed. Sync with a Tart peer
  runs the same x86-64 verification build under Rosetta in a macOS 15
  (`legacy`) guest, because an Apple Account needs macOS 15 or later in a
  guest (`user-confirmed`, 2026-09-30).
- `AC-03` — The arm64 candidate passes its package checks and the same flow
  in Tart, feeds stay separated as stated, and the x86-64 build refuses to
  run under Rosetta in an arm64 Tart guest.
- `AC-04` — The availability page lists Intel on macOS 13 with its evidence
  (verified under Rosetta, not on Intel hardware) and arm64 from macOS 13 as
  supported.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Package checks for both architectures: Mach-O architecture and deployment
  target, signatures, notarization, Gatekeeper, and feed contents.
- The verification-only switch differs from the release build only in the
  Rosetta refusal; a package check proves the release build refuses Rosetta
  and the verification build does not ship.

## Decisions or blockers

- Accepted limit (`user-confirmed`, 2026-09-30): no run on Intel hardware. Code paths that differ between
  Rosetta and a real Intel CPU stay unverified and are named on the
  availability page. Sync on macOS 13 is one of them.
- Accepted (`user-confirmed`, 2026-09-30): the Intel test feed is served on
  loopback inside the guest and nothing is published; the Rosetta refusal
  reads "This version is for Intel Macs" / "This Mac has Apple silicon.
  Download Posato for Apple silicon." with **Download** and **Quit**; the
  arm64 candidate runs only on the `primary` line, keeping the macOS 13
  exception strict.
- The evaluation build and its helper are still registered on the shared
  MacBook Air; the maintainer removes them with **Remove from this Mac**.
