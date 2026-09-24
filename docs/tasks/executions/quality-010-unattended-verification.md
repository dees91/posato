# Execution: `QUALITY-010`

- **Brief:** [Verify every task without the maintainer](../specifications/quality-010-unattended-verification.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** Stage 1 plan reviewed by a separate agent (revise; Critical and Required folded); completed-change review per stage
- **Branch:** `feature/quality-010-unattended-verification`
- **Updated:** 2026-09-24

## Plan

1. Stage 1 setup: maintainer checklist for the test Apple Account; agent installs Tart, builds the golden image, and stores credentials in the host Keychain.
2. Stage 1 measurements, each with a recorded go or no-go: CloudKit and Keychain in the VM, VNC control of privileged prompts, XCUITest through Screen Time consent and the picker, network-service switching in the VM.
3. Plan review of Stage 2 on the Stage 1 evidence, then driver and skill extension for both targets.
4. Stage 3 observed blocking recipes on both targets.
5. Completed-change review per stage, `./gradlew quality`, and closeout.

### Stage 1 plan

Starting facts, `source-claim` (Apple Support 120468, Apple Developer Forums
787827, Tart issue 1068): iCloud works only in a guest created from an IPSW
on this macOS 15+ host; every restore is a clone; at most two macOS guests run
at once; a registered VM can still be rejected by a development profile.

Order, cheapest no-go first: agent setup, `M0`, `M3`, the maintainer's
account checklist, then `M1`, `M4`, `M2`, `M5` on VMs (all under Result).
Remaining: `M6` Screen Time consent and any passcode prompt through XCUITest
on the test iPhone, and `M7` picker taps located by text recognition.
- No-go fallbacks: without development provisioning there is no per-task sync
  in a VM (Developer ID is a release check only); without clone sessions, one
  long-lived VM per role reset by application state.

Secret handling: each host Keychain item has an access list limited to its
consumer, and a secret is piped straight into the typing or sign-in step,
never printed, logged, or written to a run directory; Tart's VNC address
stays outside run logs. Captures that show the account email are skipped,
and a final check searches every run artifact for the secrets. Measurement
scripts stay in the ignored scratch area.

Open decisions for the maintainer:

- D1, D2, `user-confirmed` 2026-09-24: macOS 26 golden VM on the internal
  disk. The `QUALITY-007` macOS 15 image stays separate and must also be
  created from an IPSW on this host.
- D3, `user-confirmed` 2026-09-24: register the two VM identifiers through
  the App Store Connect API, which this task sets up (team key outside Git).
- D4, `user-confirmed` 2026-09-24: the dedicated test iPhone (iPhone 13 mini,
  iOS 26.5.2, wired, Developer Mode on) is signed in to the test account.
- D5, `user-confirmed` 2026-09-24: the test iPhone is the trusted device.
- D6, only if `M2` fails: whether guest Screen Sharing input is acceptable.
- D7, `user-confirmed` 2026-09-24: risk accepted; the VNC server listens on
  all interfaces and the host firewall is off (`observed`).

## Result

- Opened: worktree, brief, and draft pull request.
- Setup, `observed` 2026-09-24: Tart 2.37.0 (`openai/tools` tap); golden VM
  on macOS 26.6.2 from the latest IPSW, Setup Assistant driven over VNC with
  host-side text recognition, no Apple Account yet. Deviation:
  `tart-guest-agent` 0.15.0 replaces Remote Login, so `tart exec` runs in the
  user's Aqua session instead of an SSH session that would move privacy
  approvals to `sshd`. Machine details live outside this repository.
- `M0` provisioning identity, `observed`: a VM keeps its provisioning
  identifier across restarts and start order; a fresh clone inherits the
  source's current identifier; two VMs with the same identifier running
  together leave one of them permanently re-identified. The sync companion of
  a development package is rejected by AMFI in an unregistered VM ("restricted
  entitlements"). Two registered identifiers (primary and peer lines) cover
  every per-run clone if a golden VM never runs beside its own clone. After
  registering both via the App Store Connect API and recreating the
  companion profile, the companion starts from a local copy in the guest; on
  the virtiofs share `codesign` rejects the bundle, so the driver must copy it.
- `M3` desktop driving, `observed`: with the worktree and a JDK shared into
  the guest, `doctor`, `launch`, `snapshot`, `screenshot`, `tap`, and
  `db query` work through `tart exec` after Accessibility and Screen Recording
  were granted once to `tart-guest-agent` over VNC, administrator prompt
  included (run `m3-guest-launch`). Go, with one driver defect:
  `scrollTo` only accepts a target inside the largest scroll area, so the
  onboarding Continue button below that area in the guest's taller window
  never counts as reached (run `m3-guest-scenario`, step 3).

- `M1` iCloud identity, `observed`: the golden VM signed in once with a
  two-factor code approved on the test iPhone and joined iCloud Keychain with
  the VM password. A fresh clone of it boots signed in with no prompt; the
  peer (same machine lineage, different identifier) signed in without a
  code. Go, with one gap: the account's device list could not be read.
- `M4` sync half, `observed`: in the clone, onboarding **Sync with iCloud**
  established a workspace; the peer joined the same one (identical bootstrap
  row), and a website added on the clone arrived on the peer after
  **Sync now**. The peer now carries Posato data and must be reset first.
- `M2` and `M4` enforcement, `observed` on fresh clones: helper background
  approval over VNC, then 20 of 20 session starts whose SecurityAgent prompt
  was confirmed over VNC (active without Retry, clean early end; runs
  `m2-loop-*`). A paused HTTP domain shows the pause page, HTTPS `CONNECT` is
  refused, a control domain loads, a paused Safari ends within about two
  seconds, and all of it is reachable after the end. Gatekeeper first open of
  the notarized 1.0.0 release, quarantined, approved 6 of 6 (two clicks: the
  first activates the dialog). Go.
- `M5`, `observed`: with a second service on the same interface, disabling
  the primary during a session restores it and asks Retry; Retry applies on
  the new service. Go for measurability. Product defect found, reproduced on
  a fresh clone (`m5-network`): removing the service that holds the mutation
  and re-enabling the original leaves "Restrictions active" with no proxy and
  no application termination, and a stale ownership record then makes every
  later start report "Restrictions may still apply". Candidate `MACOS` row.
- `M6` blocked: the first XCUITest run on the test iPhone times out "while
  enabling automation mode" until UI Automation is enabled on the device
  (maintainer, once). The CoreDevice tunnel also idles, so `doctor` reports no
  device until `devicectl device info details` wakes it.

## Verification

- Worktree provisioned from the main checkout's ignored `local.properties`; `:posato-control:installDist` passes.

## Blockers and accepted risks

- Two macOS guests at a time: the golden and peer VMs cannot run beside the `QUALITY-007` macOS 15 VM.
