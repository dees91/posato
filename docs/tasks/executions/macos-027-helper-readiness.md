# Execution: `MACOS-027`

- **Brief:** [Mac helper readiness that agrees with what the helper does](../specifications/macos-027-helper-readiness.md)
- **Status:** `active`: brief written and read-only checks recorded; reproduction has not started
- **Review tier:** `standard` (escalates to `high-risk` under the brief's rule)
- **Implementer:** Claude
- **Reviewer:** pending
- **Branch:** `task/macos-027-helper-readiness`
- **Updated:** 2026-10-09

## Plan

1. Obtain the maintainer's answers to the brief's decisions: both are
   answered (below).
2. Within one work session, run the sequence chosen below in Tart,
   agreeing VM use with the parallel sessions.
3. Reproduced: fix, `AC-03`, `AC-04`, Standard review. Otherwise record the
   attempts and hand the backlog move to the maintainer.

## Read-only checks of the maintainer's install (2026-10-09)

The maintainer allowed the checks (`user-confirmed`). Posato was neither
launched nor changed. All `observed`:

- 1.3.0 (28), Developer ID with the project's team, in `/Applications` since
  2026-10-04. `codesign --verify --deep --strict` passes in 0.07 s, three
  times with a warm cache.
- The files carry only `com.apple.provenance`, and there are no stray files at
  the bundle root. A `Contents/CodeResources` file beside `_CodeSignature/`
  still passes the strict check.
- `app.posato.macos.proxy-settings` is enabled in the system domain and
  starts on demand. The application runs in the GUI domain.
- `sfltool dumpbtm` shows three `PosatoMacOSHelper` records
  (`2.app.posato.macos.helper`) for the same `/Applications` URL, all
  "disabled, allowed":
  - under UID -2, generation 1, carrying the daemon
    `16.app.posato.macos.proxy-settings` (enabled, allowed, notified,
    generation 21);
  - under UID 0;
  - under the maintainer's UID 501, generation 2 or 3, with no daemon.

  `2.app.posato.macos` is enabled, generation 7.
- Spotlight finds many same-ID development builds under ignored `build/`.

Against the [macOS enforcement](../../wiki/topics/macos-enforcement.md) facts:

- A disabled helper parent beside an enabled daemon is normal (`MACOS-007`).
- Records keep `allowed` after unregistering (`MACOS-009`), so the extra
  records need not be removals.
- Unlike `MACOS-007`, no record points at another bundle.

`user-confirmed` (2026-10-09): the symptom no longer shows on the
maintainer's 1.3.0 (28), installed since 2026-10-04. Idea 29 was reported on
2026-10-05, so the state appeared on 1.3.0 and later cleared without a change:
it is **transient**. The maintainer does not know which builds ran on that Mac.

### Host install history before the 2026-09-24 rule (`inferred`)

From tracked records only; installs of the published releases are `open`.

1. 2026-08-29 to 09-07: Apple Development copies in renamed `/Applications`
   bundles (`MACOS-003`, `MACOS-004`) and worktree packages (`TARGETS-003`,
   `MACOS-005`, `SESSION-002`, `SYNC-009` to `SYNC-015`) each registered the
   same helper and daemon.
2. 2026-09-11, `MACOS-007`: the daemon record still pointed at a removed copy.
   Leftover copies were deleted, `sfltool resetbtm` ran (maintainer approved),
   and a development package registered again.
3. 2026-09-14, `MVP-001`: attended phases on development builds.
4. 2026-09-15, `MACOS-008`: from a baseline with no Posato background items,
   notarized 1.0.0 candidates 3 and then 4 were installed from Safari into
   `/Applications` and replaced in Finder. The registration kept the parent
   bundle version 3.
5. 2026-09-16: `MACOS-009` removed the helper from notarized candidates in the
   app, which leaves `allowed` records. `DESIGN-003` ran a signed worktree app,
   and its daemon failed to launch until a Mac restart.
6. 2026-09-23 to 09-25: `ONBOARDING-003` development runs, then `MACOS-011`
   Stage 1. That stage ran notarized builds 8 to 14, which updated each other
   in the app from a loopback feed.
7. From 2026-09-24 no agent ran Posato on that Mac. 1.3.0 (28) has been in
   `/Applications` since 2026-10-04.

Hypotheses now:

- **Hypothesis 1a is stronger again** (`inferred`): the defect came and went
  on an unchanged install, which fits a load-dependent failure. The readiness
  check verifies the whole bundle, including the Java runtime, with a 5 s
  timeout. On 2026-10-05 the host also ran Tart guests and Gradle builds for
  `DESIGN-004`. 1b is unlikely: the bundle is clean.
- **Hypothesis 2 stays plausible** (`inferred`). The helper reads
  `SMAppService` from the user context. If that read lands on the UID 501
  record, which has no daemon, or on a same-ID copy, Status reads not enabled.
  Enable then fails with `failedEnableResponse`, which the app shows as
  `UNAVAILABLE`, while the UID -2 daemon keeps blocking. This matches **Finish
  setup** ending in "Blocking could not be turned on yet", but clears on its
  own less naturally. **Hypothesis 3** stays unlikely.

### Reproduction sequence chosen from the history

Runs within one work session, using one VM at a time:

1. **Hypothesis 1a first.** On a fresh clone with 1.3.0 installed and set up,
   time the strict check, then put sustained CPU and disk load on the guest
   (and the host) and purge its cache. Read Session, This Mac, and **Finish
   setup**, and watch a scheduled start.
2. **Hypothesis 2 on the closest path.** Steps 1 to 6 above shrink to: a
   development package that registers the helper from a non-`/Applications`
   path; 1.0.0 from its DMG in `/Applications`, set up and then removed in
   the app; set up again; `vm install --replace` to 1.1.0 and then to 1.2.0;
   the in-app update to 1.3.0; then `sfltool dumpbtm` and readiness after
   each step.

Open: when the UID 0 and 501 records appeared; whether the schedule started.

## Result

- Pending.

## Final

- **Status:** pending
