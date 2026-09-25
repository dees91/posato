# ADR 0008: Deliver macOS updates through Sparkle

## Status

- **Status:** Accepted; delivered and verified by `MACOS-011` on 2026-09-25
- **Date:** 2026-09-22
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`
- **Owner:** `MACOS-010` decides; `MACOS-011` delivers after separate activation.

The maintainer selected Sparkle after the comparison, checks only after consent,
GitHub Releases hosting, installation after the session ends, and a signing key
in the maintainer's Keychain with an encrypted backup outside the repository.
The maintainer then accepted the implementation plan for this documentation.
This accepted the direction and delivery gates, not a working updater.
`observed` (2026-09-25): `MACOS-011` delivered the updater and proved the
acceptance matrix below on notarized candidates in Tart virtual machines; its
[execution record](../tasks/executions/macos-011-updates.md) maps every row to
evidence. The ADR 0003 and ADR 0004 amendments below are applied to those
decisions and kept here as history. The public wording below remains staged
for `RELEASE-003`, which publishes it with the first updater-capable release;
until then the published application still follows the manual update path.

## Context and evidence

`observed`: the public [1.0.0 release](https://github.com/dees91/posato/releases/tag/v1.0.0)
contains a DMG and checksums, with no update feed. The
[MACOS-008 record](../tasks/executions/macos-008-developer-id-distribution.md)
records the notarized quit, replace, and open experiment. Quitting restores
proxy ownership; reopening during a session requires explicit Resume with
administrator authentication. It does not prove uninterrupted enforcement.

`observed`: `desktopApp` already has a signed AppKit/JNI window leaf. Its
scope in [ADR 0003](0003-mvp-application-architecture-baseline.md) explicitly
excludes networking. `MacOsHelperClient.close()` makes a best-effort Restore
request; swallowed errors cannot establish an installation precondition.
[ADR 0004](0004-macos-helper-ownership-and-lifecycle.md) requires confirmed
cleanup and compatibility. Its 2026-09-15 amendment permits unchanged daemon
registration when the label and `BundleProgram` remain unchanged.

`source-claim`: [Sparkle 2.10.0](https://github.com/sparkle-project/Sparkle/releases/tag/2.10.0),
the current stable release checked on 2026-09-22, supports macOS 12 or later,
within Posato's macOS 15 Apple-silicon baseline. Its
[programmatic API](https://sparkle-project.org/documentation/programmatic-setup/)
supports non-Apple UI toolkits. `SPUUpdater` accepts a user-driver adapter that
can forward to `SPUStandardUserDriver` while Kotlin owns installation admission.
`inferred`: the existing JNI build pattern makes this integration practical;
AWT quit/relaunch and the complete installation lifecycle remain unverified.

## Alternatives considered

The cost comparisons are `inferred` from the existing release process and the
work each option adds. They are relative estimates, not measured delivery times.

| Criterion | GitHub metadata check and guided replacement | Sparkle | Manual download |
| --- | --- | --- | --- |
| User outcome | App reports a newer release; person downloads, quits, and replaces | App offers a verified download, install, and relaunch | Person must discover and install each release |
| Requests and consent | New opt-in HTTPS metadata request; browser handles download | New opt-in feed check; explicit download; provider sees connection metadata | No updater request from Posato; browser contacts GitHub |
| Update trust | HTTPS metadata; Developer ID and notarization for downloaded DMG; no signed feed by default | Signed feed and archive, HTTPS, Developer ID, notarization | Existing notarized manual-download trust |
| Version and compatibility | Posato must implement metadata parsing and version/platform selection | Standard appcast version and platform fields; enforce increasing build numbers | Person selects the artifact; Gatekeeper checks distribution trust |
| Restore before replacement | Guided quit retains reliance on the person's replacement timing | Application must prove cleanup before admitting an installer | Existing MACOS-008 evidence and recovery limits |
| Interrupted update | Person retries replacement or uses existing recovery | New cancellation, termination, and restart proof is mandatory | Existing manual recovery |
| Helper and future resident process | Must distinguish quitting the process from closing its window | Gate follows process and enforcement lifetime, independently of window visibility | Instructions must evolve if the process becomes resident |
| Hosting and keys | Existing GitHub API and release assets; no new signing key | One signed feed asset and DMG per release; separate Ed25519 key | Existing release assets and Developer ID identity |
| Implementation and maintenance | Smaller initial change; owns parsing and guided flow | Greatest initial integration cost; maintain dependency, signing, and lifecycle checks | Lowest engineering cost; highest recurring user effort |

`source-claim`: unauthenticated GitHub API requests share a
[60-request hourly IP limit](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api).
The selected static release-asset feed avoids that API quota and a client token.
GitHub still controls availability and can restrict downloads.

Sparkle is selected because it removes the recurring manual replacement step
and supplies maintained update validation and installation. The existing DMG
can remain the release artifact. A smaller metadata checker would still leave
replacement to the person while adding another owned network/parser boundary.
Manual download remains the bootstrap and recovery route, not the selected
long-term discovery mechanism. There is no fallback to either alternative
without a maintainer decision if the Sparkle delivery experiment fails.

## Decision

### Consent, session behavior, and data

- Ask once for automatic checks using a native prompt after first-run setup;
  no automatic request precedes consent. Schedule checks every 24 hours while
  the process runs, retain a visible opt-out, and offer manual checking.
- Every download and installation requires an explicit action. An active
  product session prevents admitting an update. A person may end the session
  through the existing early-end action; an update does not end it implicitly.
- Keep the preference and scheduler state local in Sparkle's settings. Do not
  duplicate them in synchronized product state or add a resident process.
- Disable system profiling and automatic downloads/installations. Use a fixed
  updater User-Agent without the installed version, OS, or installation ID;
  compare installed version, OS, and architecture locally. No policy, app
  choices, session, workspace, account, or diagnostic data enters requests.
- Embed plain-text release notes in the signed feed, without remote resources.
  User-opened links use the browser. Feed checks and accepted artifact downloads
  are the updater's only network purposes, including their HTTPS redirects.
- GitHub and its delivery providers necessarily observe IP address, timing,
  requested resources, and transport metadata; an artifact URL identifies the
  version being downloaded. This is not anonymity. GitHub exposes aggregate
  asset download counts. Posato adds no analytics or installation identifier.

`observed` in upstream source: Sparkle's
[downloader](https://github.com/sparkle-project/Sparkle/blob/2.10.0/Downloader/SPUDownloader.m)
uses the default URLSession configuration. Profiling being disabled does not
prove cookie isolation or the complete request shape. `MACOS-011` must verify
headers, redirects, and cookie behavior against these boundaries; persistent
provider tracking identifiers are not accepted by this decision.

### Hosting, signing, and recovery

- Publish `appcast.xml` and the signed, notarized, stapled full DMG in the same
  stable GitHub Release. Use
  `https://github.com/dees91/posato/releases/latest/download/appcast.xml` as the
  stable feed URL; each enclosure names its version-specific release asset.
  [GitHub documents this URL form](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases).
- Prepare assets in a draft release and publish only after validating the
  complete set. Every later stable macOS release carries the feed; a release
  without it must not become latest. Test candidates use a separate test feed
  and never become the public stable release during verification.
- Use one stable channel and full DMGs initially. Disable delta generation in
  the release tooling. Inline notes, feed signatures, and download signatures
  are generated after the final DMG bytes exist; never alter signed bytes.
- Set `SURequireSignedFeed=YES`, `SUVerifyUpdateBeforeExtraction=YES`, and
  `SUSignedFeedFailureExpirationInterval=0`. Set `SUAutomaticallyUpdate=NO`,
  `SUAllowsAutomaticUpdates=NO`, and `SUEnableSystemProfiling=NO`. These
  [Sparkle settings](https://sparkle-project.org/documentation/customization/)
  require valid signed metadata without the default timed relaxation.
- Embed only the public Ed25519 key. Generate the private key with Sparkle's
  tool in the maintainer's login Keychain; keep an encrypted backup outside
  Git. Signing stays local. Do not put keys in arguments, logs, or a feed host.
- Keep Developer ID signing, hardened runtime, notarization, and verification
  for the application, every nested executable, and the DMG. Pin and review
  the Sparkle distribution and notices during delivery; this decision adds no
  library-validation exception or new privilege to the Posato daemon.
- `CFBundleVersion` increases across published macOS releases, independently
  of the display version. Reject same/older builds and unsupported platforms.
  A replayed signed feed may withhold a newer release; signatures do not prove
  freshness or availability. Do not offer automatic downgrade as recovery.
- Loss of the update key blocks strict feed delivery until key custody is
  restored or an explicitly reviewed recovery is selected. Manual installation
  of a new notarized build remains available. Sparkle's
  [key-rotation rules](https://sparkle-project.org/documentation/)
  do not justify disabling validation or rotating both trust anchors at once.

GitHub is selected over a feed on `posato.app` to keep publication in one
release operation and avoid a second deployment. The tradeoff is a GitHub-owned
feed URL and dependence on its latest-release routing. The existing Cloudflare
site remains the policy/support host; it receives no automatic update check.

## Authority amendments

Applied on 2026-09-25 by `MACOS-011` to ADR 0003 and ADR 0004, verbatim.

### ADR 0003: add a separate updater exception

> MACOS-011 may add one signed in-process AppKit/JNI updater leaf in the desktop
> application, separate from the DESIGN-001 window-presentation leaf. It adapts
> Sparkle's update UI, network requests, and installer lifecycle. Kotlin owns
> consent-facing orchestration and the decision to admit an installation.
> Sparkle retains its own local preferences and scheduling. Native updater
> objects do not cross into commonMain. This leaf receives no workspace keys,
> policy, application selections, privileged proxy operations, or general
> command interface.
>
> Sparkle's own installation processes remain separate from Posato's
> enforcement helper, root daemon, and synchronization companion. Their signing
> and any installation authorization are verified as release dependencies;
> the Posato daemon's responsibilities do not expand. A native updater failure
> may terminate the desktop application. MACOS-011 must prove cleanup and
> recovery across that failure before releasing the feature. The window leaf's
> existing prohibition on networking remains unchanged.

### ADR 0004: add the supported in-app update contract

> The in-app update path uses Sparkle as specified by ADR 0008. Before passing
> an Install continuation that can begin downloading, extracting, or installing,
> Posato atomically acquires maintenance admission with the no-active-session
> check. It rejects every new Apply or Resume, including synchronization-driven
> attempts and retries. It reconciles unknown helper outcomes, confirms that
> both proxy tuples are no longer Posato-owned, reaches Idle, and completes the
> old helper and companion shutdown needed for replacement. Best-effort close,
> process disappearance, and absent registration alone do not prove cleanup.
>
> Maintenance admission remains closed while bundle replacement is possible,
> including after cancellation, ordinary quit, crash, or relaunch. A Sparkle
> dismissal, aborted cycle, or relaunch callback is not sufficient evidence to
> admit enforcement. Reopening admission requires positive evidence of either
> successful replacement, or termination of the exact installer with no pending
> replacement, followed by native ownership and compatibility revalidation.
> An enabled service must be ready and Idle before another Apply. A previously
> disabled or never-enabled service remains disabled; updating does not grant
> permission or silently enable it. Any uncertain outcome retains the gate and
> an action-required recovery state.
>
> The 2026-09-15 registration exception remains valid for an unchanged daemon
> label, BundleProgram, and compatible executable set. Changes to those values
> or an incompatible protocol require a separately verified re-registration
> path after cleanup; they cannot use that exception. Unknown state schemas,
> mismatched signatures, or incomplete restoration never permit replacement or
> silent reset. No active session is ended by the updater. A session arriving
> while an admitted update is running does not bypass maintenance admission;
> after relaunch, existing administrator-approved Resume rules still apply.
>
> MACOS-011 must prove safe installation, cancellation, and restart recovery
> before release. If supported Sparkle mechanisms and exact owned-process
> observation cannot establish the required facts, delivery is blocked pending
> a maintainer decision. This does not authorize private Sparkle APIs, a fork,
> a custom installer, or a weaker fallback. Manual quit, replace, and open
> remains the route from version 1.0 to the first updater-capable build.

The delivery review must also update `TB-08`/`T-13` in the
[threat model](../security/apple-mvp-threat-model.md) for the signed feed,
third-party installer, key custody, and publication chain, and classify the new
provider-visible metadata. Existing `R-02` and no-telemetry limits still apply.

## Proposed public wording

These are complete replacement/addition passages. Publish them only with the
verified feature; choose the actual policy effective date at publication.
`MACOS-011` must reconcile them with measured requests before publication.

### Privacy policy

Replace the opening paragraph with:

> Posato does not send your website list, app choices, sessions, or iCloud
> workspace data to its developer. There is no Posato account, no analytics,
> no advertising, and no Posato-operated server. Optional iCloud sync and
> macOS update requests are described below.

Add this section after "Blocking on Mac":

> ## Updates on Mac
>
> Posato asks before checking for updates automatically. If you agree, it
> checks once a day while the app is running. You can turn automatic checks
> off or choose Check for updates yourself. Each update requires your action
> to download and install, and installation waits until no session is active.
>
> Update information and downloads come from GitHub Releases through Sparkle,
> an update library included in Posato. Requests do not contain your website
> list, app choices, sessions, iCloud data, an installation identifier, or a
> system profile. Posato compares versions and system requirements on your Mac.
>
> GitHub and its delivery providers can receive your IP address, request time,
> the resource requested, and technical connection information. A download
> address identifies the version you request. They handle this information
> under [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
> GitHub makes aggregate download counts available; Posato adds no usage
> analytics or tracking identifier. Opening a release or support link in your
> browser is subject to that website's policies and your browser settings.
>
> Update preferences are stored on this Mac and are not synchronized through
> iCloud. Sparkle stores update-check state and temporary update files locally.

Replace the first paragraph under "Diagnostics" with:

> Posato does not collect or upload diagnostics, crash reports, or telemetry.
> Update requests are limited to the purposes described in Updates on Mac.
> Your operating system or app store may collect diagnostics under your device
> settings and their own policies; Posato does not access or add to them.

`website/src/pages/privacy.astro` already renders `PRIVACY.md`. Publish one
canonical policy, not a separate website copy. Its description and the site's
"No Posato-operated server" text remain accurate with GitHub-hosted updates;
review the adjacent privacy summary against the final request evidence.

### Availability and update instructions

Add the following paragraph to Availability in
[limits and supported platforms](../product/limits-and-platforms.md) and the
website's Availability section when the updater-capable release is published.
Replace the existing Mac version reference with that release's actual version;
retain whatever iOS availability is accurate then.

> Posato for Mac is a signed and notarized download from GitHub Releases.
> Versions with the updater can check for new releases after you agree, or
> when you choose Check for updates. Downloading and installing requires your
> action, and installation waits until your session has ended. To move from
> Posato 1.0 to the first version with updates, download the new DMG, quit
> Posato, replace the application, and open it again. Manual downloads remain
> available if an in-app update cannot complete.

The existing Mac Resume and recovery limits remain published. `MACOS-011`
records the actual menu/settings placement in `DESIGN.md` without adding a
third product destination or implying that closing a future window quits the
resident process planned by `MACOS-012`.

## MACOS-011 delivery plan and acceptance

Do not create a delivery brief or start code from this discovery alone. After
the maintainer names `MACOS-011`, create its High-risk brief and obtain its
independent plan review. Use this decision for the following bounded work:

1. Prove admission and cancellation first on two locally signed and notarized
   candidates with a separate feed. Wrap both Install replies in
   `SPUUserDriver`; the first can already start an installer. Keep AppKit work
   on its main thread and preparation asynchronous, with one matched reply.
   Revalidate admitted/pending updates across application lifetimes, including
   another instance, before permitting enforcement. If positive safe-release
   evidence cannot be obtained, stop with the failed proof and mark delivery
   blocked. Do not substitute `sessionInProgress` or a cycle callback.
2. Integrate the narrow updater boundary, local consent/settings, session gate,
   and standard native UI. Keep authentication, enforcement, and synchronization
   ownership intact. Define recovery messaging from actual supported outcomes.
3. Extend the existing packaging and verification chain for Sparkle's nested
   binaries, self-contained framework paths, signatures, notarization, and
   notices. Use Sparkle's existing signing/appcast tools in the repeated local
   release process; prepare complete draft assets before publishing stable.
4. Test policy and boundary behavior with synthetic fixtures, then drive the
   notarized A-to-B path through
   [verify-posato](../../.agents/skills/verify-posato/SKILL.md). Preserve local
   user state and keep captures and identifiers in ignored `build/verification/`.
   Distinguish a manually installed first updater from the proven in-app update.
5. Obtain completed-change review, run affected verification and the final local
   `quality` gate, apply the reviewed authority amendments, and coordinate
   policy/availability publication with the verified release. `RELEASE-003`
   retains public 1.1 release ownership. `MACOS-012` must preserve this gate if
   it later changes process or window lifetime.

| Scenario | Required evidence |
| --- | --- |
| Consent, opt-out, manual check | No automatic request before consent or after opt-out; manual checking works; request fields, cookies, redirects, and notes stay within the accepted data boundary |
| Invalid or unavailable update | Wrong/missing feed or archive signature, tampered bytes, older/equal build, unsupported platform, offline response, and HTTP failure never start replacement or affect enforcement |
| Active session and concurrent intent | Active session prevents admission; local Start/Resume, sync-driven intent, retries, and helper recreation cannot slip through the maintenance gate |
| Cleanup failure | Lost Restore reply, proxy conflict, unavailable daemon, and unknown ownership remain blocked without overwriting unrelated settings or treating absence as Idle |
| Cancellation and termination | Cancel before extraction, cancel/error after installer launch, dismissal followed by Cmd-Q/window close/crash, canceled/retried termination, and relaunch with pending installation never reopen enforcement while replacement remains possible |
| Complete A-to-B update | Candidate A proves clean proxy ownership before replacement; B preserves local data and sync configuration, verifies the new signed executable set, preserves disabled service state, and allows enabled enforcement only after ready/Idle and existing authorization |

`observed` in [Sparkle's installer source](https://github.com/sparkle-project/Sparkle/blob/2.10.0/Sparkle/SPUInstallerDriver.m#L563-L582):
cancellation sends a message and reports abort without awaiting installer exit.
Its [session lifetime contract](https://github.com/sparkle-project/Sparkle/blob/2.10.0/Sparkle/SPUUpdater.h#L173-L189)
also distinguishes a completed update cycle from a pending installation.
These are reasons for the delivery gate, not evidence that the gate already
works in Posato. Framework version changes require rechecking these assumptions.
