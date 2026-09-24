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

Starting facts, `source-claim` (Apple Support 120468, Virtualization notes,
Apple Developer Forums 787827, Tart issue 1068): an Apple Account works in a
macOS guest only when host and guest run macOS 15 or later and the VM was
created from an IPSW on this host, so imported images cannot use iCloud. Tart
has no snapshots, so every restore is a clone. A VM's provisioning identifier
may change across launches, clones, and concurrent VMs, and a registered VM
can still be rejected by a development profile. At most two macOS guests run
at once. Tart is Fair Source, royalty-free on a personal workstation.
`observed` host: M1 Pro, 32 GB, macOS 26.5, 75 GB free, Tart not installed.

Order, cheapest no-go first:

1. Agent: install Tart, create one VM with `tart create --from-ipsw` (D1, D2),
   complete Setup Assistant over Virtualization's own VNC server with a
   random local administrator password, automatic login, and host-only
   Remote Login. Then run `M0`, `M2`, and `M3`.
2. Maintainer: decide D3 to D5 on that evidence.
3. Maintainer, one-time checklist in chat: create the test Apple Account with
   iCloud Keychain and the D5 trusted number or device, and enter the first
   two-factor code. Then the agent runs `M1`, `M4`, and `M5`.
4. `M6` and `M7` when the test iPhone exists (D4).

Measurements, each a go or no-go with its run directory:

- `M0` development provisioning: the provisioning identifier across restart,
  clone, and two running VMs, then a development-signed package with the sync
  companion launching in the guest. No-go means no per-task sync in a VM; the
  Developer ID path stays a release check in Production and is not a
  substitute.
- `M2` VNC control through Virtualization's own server: 20 of 20 for the
  helper's SecurityAgent prompt; fewer runs, each with a stated reset, for
  the one-time System Settings approvals and the Gatekeeper first-open
  dialog. Screens are located by host-side text recognition. Guest Screen
  Sharing is a different input class and needs D6 before it counts.
- `M3` desktop driving in the guest: `posato-control doctor`, `launch`,
  `snapshot`, `screenshot`, and `db query` for `-t desktop` over SSH, with
  the guest Accessibility and Screen Recording approvals granted once over
  VNC. Go is output equivalent to a host run.
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
never printed, logged, or written to a run directory. Tart's VNC address and
password stay outside run logs, and the server must listen on loopback only.
Captures that show the account email are skipped. A final check searches
every run artifact for the secrets. Measurement scripts stay in the ignored
scratch area; Stage 2 decides what enters `posato-control`.

Open decisions for the maintainer:

- D1: guest macOS version. The `QUALITY-007` image counts only if it was
  created from an IPSW on this host.
- D2: storage for about 60 GB of VM disks and a temporary IPSW.
- D3, after `M0`: development Mac device slots used per VM, and whether they
  stay valid.
- D4: the dedicated test iPhone.
- D5: the account's two-factor trusted number or device, preferably the test
  iPhone so the agent can approve codes.
- D6, only if `M2` fails: whether guest Screen Sharing input is acceptable.

## Result

- Opened: worktree, brief, and draft pull request. No setup or measurement yet.

## Verification

- Worktree provisioned from the main checkout's complete ignored `local.properties`. `:posato-control:installDist` and the driver's `--help` pass.

## Blockers and accepted risks

- The test Apple Account and the dedicated test iPhone are maintainer inputs. The physical Mac stays serialized with `MACOS-011` Stage 2 notarized experiments when they switch network services.
- Two macOS guests at a time: the golden and peer VMs cannot run beside the `QUALITY-007` macOS 15 VM.
