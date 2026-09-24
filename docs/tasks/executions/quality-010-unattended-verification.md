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

Order, cheapest no-go first:

1. Agent: install Tart, create one VM with `tart create --from-ipsw` (D1, D2),
   complete Setup Assistant over Virtualization's own VNC server with a
   random local administrator password and automatic login. Then run `M0`,
   `M2`, and `M3`.
2. Maintainer: decide D3 to D5 on that evidence.
3. Maintainer, one-time checklist in chat: create the test Apple Account with
   iCloud Keychain and the D5 trusted number or device, and enter the first
   two-factor code. Then the agent runs `M1`, `M4`, and `M5`.
4. `M6` and `M7` when the test iPhone exists (D4).

Measurements, each a go or no-go with its run directory:

- `M0` development provisioning: identifier stability across restart, clone,
  and concurrent VMs, then the development sync companion in the guest.
  No-go means no per-task sync in a VM; the Developer ID path stays a release
  check in Production and is not a substitute.
- `M2` VNC control through Virtualization's own server: 20 of 20 for the
  helper's SecurityAgent prompt; fewer runs, each with a stated reset, for
  the one-time System Settings approvals and the Gatekeeper first-open
  dialog, located by host-side text recognition. Guest Screen Sharing is a
  different input class and needs D6 before it counts.
- `M3` desktop driving in the guest: `doctor`, `launch`, `snapshot`,
  `screenshot`, and `db query` for `-t desktop`, equivalent to a host run.
- `M1` iCloud identity: a clone of the signed-in golden VM appears as a
  separate device in the test account and joins iCloud Keychain, and an
  agent completes any required re-sign-in with no maintainer action. Fallback:
  one long-lived VM per role reset by application state, listing the lost
  rows (Gatekeeper first open, privacy approvals, helper approval).
- `M4` sync, helper, and enforcement: the Sync with iCloud consent on the
  golden VM and the peer linking to it in the Development environment; the
  pause page for a paused synthetic domain, a control domain loading, the
  paused application ending, and both recovering after the session.
- `M5` network-service switching with one virtual interface: a second
  service on the same interface, a reordered service list, or a second
  interface, compared with the physical-Mac scenario.
- `M6` Screen Time consent and any passcode prompt through XCUITest on the
  test iPhone; `M7` picker taps located by text recognition.

Secret handling: each host Keychain item has an access list limited to its
consumer, and a secret is piped straight into the typing or sign-in step,
never printed, logged, or written to a run directory; Tart's VNC address
stays outside run logs. Captures that show the account email are skipped,
and a final check searches every run artifact for the secrets. Measurement
scripts stay in the ignored scratch area.

Open decisions for the maintainer:

- D1, `user-confirmed` 2026-09-24: macOS 26 for the golden VM. The
  `QUALITY-007` macOS 15 image stays separate and must also be created from an
  IPSW on this host.
- D2, `user-confirmed` 2026-09-24: the internal disk (about 150 GB free).
- D3, `user-confirmed` 2026-09-24: register the two VM identifiers through
  the App Store Connect API, which this task sets up (team key outside Git).
- D4, `user-confirmed` 2026-09-24: a dedicated test iPhone exists; it moves
  to the test Apple Account.
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
  every per-run clone if a golden VM never runs beside its own clone.
- `M3` desktop driving, `observed`: with the worktree and a JDK shared into
  the guest, `doctor`, `launch`, `snapshot`, `screenshot`, `tap`, and
  `db query` work through `tart exec` after Accessibility and Screen Recording
  were granted once to `tart-guest-agent` over VNC, administrator prompt
  included (run `m3-guest-launch`). Go, with one driver defect:
  `scrollTo` only accepts a target inside the largest scroll area, so the
  onboarding Continue button below that area in the guest's taller window
  never counts as reached (run `m3-guest-scenario`, step 3).

## Verification

- Worktree provisioned from the main checkout's complete ignored `local.properties`. `:posato-control:installDist` and the driver's `--help` pass.

## Blockers and accepted risks

- The test Apple Account and the dedicated test iPhone are maintainer inputs. The physical Mac stays serialized with `MACOS-011` Stage 2 notarized experiments when they switch network services.
- Two macOS guests at a time: the golden and peer VMs cannot run beside the `QUALITY-007` macOS 15 VM.
