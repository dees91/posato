# Execution: `MACOS-027`

- **Brief:** [Mac helper readiness that agrees with what the helper does](../specifications/macos-027-helper-readiness.md)
- **Status:** `active`: brief written and read-only checks recorded; reproduction has not started
- **Review tier:** `standard` (escalates to `high-risk` under the brief's rule)
- **Implementer:** Claude
- **Reviewer:** pending
- **Branch:** `task/macos-027-helper-readiness`
- **Updated:** 2026-10-09

## Plan

1. Obtain the maintainer's answers to the brief's decisions. The read-only
   checks are allowed and done (below); the install history is still open.
2. Within one work session, run the brief's reproduction plan in Tart,
   cheapest hypothesis first, agreeing VM use with the parallel sessions.
3. With a reproduction: fix, verify `AC-03` and `AC-04`, then do a Standard
   review. Without one: record the attempts and hand the backlog move to the
   maintainer.

## Read-only checks of the maintainer's install (2026-10-09)

The maintainer allowed read-only checks (`user-confirmed`, 2026-10-09). The
coordinator ran them; Posato was neither launched nor changed. All `observed`:

- The installed copy is 1.3.0 (28), Developer ID with the project's team, in
  `/Applications` since 2026-10-04.
- `codesign --verify --deep --strict /Applications/Posato.app` passes in
  0.07 s, three times in a row with a warm cache.
- The files carry only `com.apple.provenance`, and there are no stray files at
  the bundle root. `Contents/` holds a `CodeResources` file next to
  `_CodeSignature/`, and the strict check still passes.
- launchd: `app.posato.macos.proxy-settings` is enabled in the system domain
  and not running, because it starts on demand. The application runs in the
  GUI domain.
- `sfltool dumpbtm` shows three `PosatoMacOSHelper` records
  (`2.app.posato.macos.helper`), all for the same `/Applications` URL and all
  with the app disposition "disabled, allowed":
  - one under UID -2, generation 1, which carries the embedded daemon
    `16.app.posato.macos.proxy-settings` (enabled, allowed, notified,
    generation 21);
  - one under UID 0;
  - one under the maintainer's UID 501, generation 2 or 3, with no embedded
    daemon.

  `Posato` itself (`2.app.posato.macos`) is enabled, generation 7.
- Spotlight finds many development builds with the same bundle identifiers
  (`Posato.app`, `PosatoMacOSHelper.app`) under ignored `build/` directories
  of the maintainer's checkouts and worktrees. Earlier physical tasks such as
  `MACOS-004` and `MACOS-009` ran development builds and candidates on this
  Mac.

How this compares with the recorded background item facts on the
[macOS enforcement](../../wiki/topics/macos-enforcement.md) page:

- A disabled helper parent beside an enabled, allowed daemon is not abnormal
  on its own. `MACOS-007` saw the same shape while launchd still started the
  daemon.
- `MACOS-009`: records keep `allowed` after unregistering, so only `enabled`
  separates a removed helper. Records survive a move to the Trash with stale
  URLs and disappear once the bundle is deleted. The extra UID 0 and UID 501
  records therefore need not be removals. Their origin is `open`.
- `MACOS-007` also saw a stale record pointing to another bundle. Here all
  three records name `/Applications`, so a stale URL is not the cause.

Hypotheses after these checks:

- **Hypothesis 1 is weaker** for the current state. Strict verification
  passes and is fast. A slow check under load and a past bundle state that
  failed verification are still possible.
- **Hypothesis 2 is stronger** (`inferred`). The helper reads
  `SMAppService` status from the user context. If that read resolves to the
  UID 501 record, which has no daemon, or to a same-ID development copy that
  Launch Services resolves, Status reads not enabled. The app then offers
  Enable. If `register()` fails against the existing system registration, the
  helper returns `failedEnableResponse`: failure and not registered. The
  application maps that to `UNAVAILABLE` ("could not be checked or enabled").
  Meanwhile the UID -2 daemon keeps blocking. This matches idea 29: **Finish
  setup** ended in "Blocking could not be turned on yet".
- **Hypothesis 3 is unchanged and unlikely.** The daemon is enabled and
  starts on demand.

Open questions:

- Does the maintainer's Posato still show "Setup incomplete" or "couldn't
  start here" today?
- When and from which copy did the UID 0 and UID 501 records appear? The Tart
  update path reproduction should show whether an in-app update or a setup by
  the person creates them.

## Result

- Pending.

## Final

- **Status:** pending
