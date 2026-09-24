# Execution: `QUALITY-010`

- **Brief:** [Verify every task without the maintainer](../specifications/quality-010-unattended-verification.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** Stage 1 plan reviewed by a separate agent (revise; Critical and Required folded); Stage 2 plan review pending. `user-confirmed` 2026-09-24: per-stage completed-change reviews are replaced by one maintainer-ordered review of the whole change.
- **Branch:** `feature/quality-010-unattended-verification`
- **Updated:** 2026-09-24

## Plan

Stage 1 measured `M0`-`M7` (Result). Platform facts and sources live in the
[unattended verification topic](../../wiki/topics/unattended-verification.md).
Secrets from the host Keychain are piped into typing only, never printed or
stored; scans of all run directories found none.

### Stage 2: driver and skill

1. iOS device tunnel: device discovery treats a paired device whose tunnel is
   idle as reachable, wakes it with `devicectl device info details`, and
   reads the state again before refusing. Unit tests on the parsed states.
2. Desktop `scrollTo`: a target fully inside the window and outside every
   scroll area counts as reached. Unit test with the `M3` geometry.
3. iOS driver system surfaces: a scenario query may name the `springboard`
   scope, resolved without activating SpringBoard; matching treats no-break
   spaces as spaces; the existing `id` key matches system identifiers; new
   actions `launchApp` and `openURL` for later blocking recipes; a
   `pressKeys` action takes digits from a secret that the host reads from the
   Keychain item named in `local.properties` and passes only through the test
   runner environment, never through the scenario, result, or log. The
   Screen Time consent becomes a fixture scenario that works with Face ID
   removed (direct passcode) and falls back to two failed Face ID attempts.
4. VM target `-t vm`: `vm create` clones a configured golden VM (primary or
   peer line), boots it headless with Virtualization's VNC, waits for the
   guest agent, and copies the development package, the driver, and a JDK
   onto the guest disk; `vm destroy` shuts it down from inside and deletes it.
   Desktop commands with `-t vm` run the guest's driver through `tart exec`
   and copy its run directory back. `vm prompt` answers system dialogs over
   VNC: administrator password, background-item approval, Gatekeeper open,
   and generic text-located click or key. A Kotlin RFB client (VNC
   authentication, raw framebuffer, key and pointer events with explicit
   Shift) and a Swift Vision text-recognition bridge, compiled on demand like
   the accessibility bridge, add no dependency. VM names and Keychain item
   names come from `local.properties`; the VNC address never leaves memory.
   Unit tests: RFB handshake and authentication against a local fake server,
   key mapping, recognition output parsing, Tart output parsing, and the
   guard that no secret reaches an envelope.
5. `verify-posato` skill, feature map, and `posato-control` README: VM and
   test-iPhone recipes. A contributor guide in `docs/development/` covers the
   one-time setup with placeholders: golden VM from an IPSW, guest agent and
   privacy approvals, device registration, test Apple Account, test iPhone
   settings, and Keychain items.
6. Verification: focused unit tests and `./gradlew quality`; the core flow
   through the driver on a fresh VM clone with the peer (AC-03) and on the
   test iPhone (AC-04).

Stage 3 (observed blocking recipes, AC-05) follows on this driver. Out of
Stage 2: the `MACOS-020` fix and committed App Store Connect API tooling.

### Decisions

- D1, D2, `user-confirmed` 2026-09-24: macOS 26 golden VM on the internal disk.
- D3, `user-confirmed`: VM identifiers registered through the App Store
  Connect API.
- D4, D5, `user-confirmed`: a dedicated test iPhone (iPhone 13 mini, iOS
  26.5.2, wired) on the test account is its trusted device.
- D6 not needed: `M2` passed through Virtualization's own VNC.
- D7, `user-confirmed`: VNC exposure on the local network accepted.
- D8, `user-confirmed` 2026-09-24: test devices run in English, like the VM.
- D9, `user-confirmed` 2026-09-24: remove Face ID from the test iPhone if
  possible; the driver keeps the two-failure fallback.

## Result

- Stage 1 setup, `observed` 2026-09-24: Tart 2.37.0; golden VM on macOS
  26.6.2 from the latest IPSW, Setup Assistant over VNC with host-side text
  recognition. Deviation: `tart-guest-agent` replaces Remote Login, so
  `tart exec` runs in the user's Aqua session rather than under `sshd`.
- `M0` go: a VM keeps its provisioning identifier; a fresh clone inherits
  it; two VMs with one identifier running together re-identify one of them.
  AMFI rejects the development sync companion in an unregistered VM; after
  registering the primary and peer lines and recreating the profile it runs
  from the guest disk (not from a virtiofs share, where `codesign` fails).
- `M1` go: the golden VM signed in once with a code approved on the test
  iPhone; clones boot signed in with iCloud Keychain. Gap: the account's
  device list was not read.
- `M2` go: helper approval and 20 of 20 SecurityAgent prompts confirmed over
  VNC (`m2-loop-*`); Gatekeeper first open of the quarantined notarized
  1.0.0 approved 6 of 6, the first click only activating the dialog.
- `M3` go: `doctor`, `launch`, `snapshot`, `screenshot`, `tap`, `db query`
  through `tart exec` once privacy approvals went to `tart-guest-agent`.
  Defect: `scrollTo` misses a target below the largest scroll area
  (`m3-guest-scenario`).
- `M4` go: two VMs established and joined one workspace and exchanged a
  website; the pause page, HTTPS refusal, control domain, and Safari
  termination held during a session and cleared after it.
- `M5` go: a second service on the one interface exercises the ADR 0005
  restore and Retry path. It found product defect `MACOS-020` (`m5-network`,
  reproduced on a fresh clone), now a backlog row (roadmap revision 4).
- `M6` go with a temporary probe: after UI Automation was enabled and the
  passcode entered once, about 25 runs asked nothing more. Consent went
  Continue, Allow (Face ID), two failures, Enter Passcode, keypad digits from
  the Keychain, Done (`m6-probe*`). Labels carry no-break spaces; the
  authentication buttons have stable identifiers; the tunnel idles.
- `M7` go: the picker is in the app's own accessibility tree; the unchanged
  driver searched, toggled, and saved an application (`m7-picker`).
- Stage 3 preview: during a session Safari showed "site not allowed" and
  Calculator the Screen Time shield, both readable through SpringBoard, and
  both opened after the end (`ios-block*`).

## Verification

- `:posato-control:installDist` passes; Stage 2 checks are listed in step 6.

## Blockers and accepted risks

- Two macOS guests at a time: the golden and peer VMs cannot run beside the `QUALITY-007` macOS 15 VM.
- A test iPhone restart needs one unlock by its owner before the driver runs.
