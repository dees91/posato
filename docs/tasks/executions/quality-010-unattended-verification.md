# Execution: `QUALITY-010`

- **Brief:** [Verify every task without the maintainer](../specifications/quality-010-unattended-verification.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** Stage 1 and Stage 2 plans reviewed by a separate agent (Critical and Required folded). `user-confirmed` 2026-09-24: per-stage completed-change reviews are replaced by one maintainer-ordered review of the whole change.
- **Branch:** `feature/quality-010-unattended-verification`
- **Updated:** 2026-09-24

## Plan

Stage 1 measured `M0`-`M7` (Result). Platform facts and sources live in the
[unattended verification topic](../../wiki/topics/unattended-verification.md).
Correction (Stage 2 plan review): XCUITest logs each tapped key's label, so
the `M6` probe's log and result bundle held the passcode digits on local
disk; that run directory was deleted and no other trace was found.

### Stage 2: driver and skill

1. iOS tunnel: a paired device whose tunnel idles is woken with
   `devicectl device info details` and read again before refusing; "Timed
   out while enabling automation mode" gets its own error and an unlock hint.
2. Desktop `scrollTo`: a target fully inside the window and outside every
   scroll area counts as reached (unit test with the `M3` geometry).
3. iOS driver, first the Kotlin model: `Query` gains a `springboard` scope
   and `Step` gains `launchApp`, `openURL`, and `pressKeys` with a secret
   reference; the desktop runner refuses them; fixture and round-trip tests.
   Swift: SpringBoard queries without activation, no-break spaces matched as
   spaces, system identifiers through `id`. A scenario with a secret step
   filters key-tap lines from the xcodebuild log, deletes the `.xcresult`
   after exporting attachments, and skips failure capture for that step; a
   test covers the filter, and the no-secret guard covers envelope,
   transcript, log, and run directory. The Screen Time consent fixture works
   with a direct passcode and with two failed Face ID attempts.
4. VMs as a location of the desktop target: `-t desktop --vm primary|peer`
   keeps `--process` and `doctor`. Host-side: `build` and `vm create|destroy|
   prompt`; everything else runs the guest's driver through `tart exec`, with
   guest paths in relayed envelopes rewritten to host paths. `vm create`
   refuses while the source golden VM runs or two guests are up, clones the
   configured golden VM, boots it headless, reads the VNC URL from a pipe into
   one 0600 file under ignored `build/verification/vm/` (deleted by
   `vm destroy`), waits for the agent, and copies the package, the driver,
   and the prebuilt accessibility bridge; the JDK is installed once in the
   golden VM. `vm prompt` answers the administrator password, background
   approval, Gatekeeper open, the private-window-picker bypass, and generic
   text-located clicks. A Kotlin RFB client (authentication type 2 with
   `javax.crypto` DES, raw frames, key and pointer events with explicit Shift)
   and an OCR mode added to the existing Swift bridge add no dependency. Unit
   tests: RFB against a local fake server and a known DES vector, key mapping,
   recognition and Tart output parsing, and the running-VM guard.
5. `verify-posato`, its feature map, and the README gain VM and test-iPhone
   recipes; a guide in `docs/development/` covers one-time setup and golden
   refresh with placeholders. The wiki topic answers its packaging question.
6. Verification: unit tests and `./gradlew quality`; the core flow through
   the driver on a fresh clone with the peer (AC-03) and on the test iPhone
   (AC-04). Stage 3 (AC-05) follows; `MACOS-020` is out of scope.

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
