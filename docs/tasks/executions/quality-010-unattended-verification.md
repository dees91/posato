# Execution: `QUALITY-010`

- **Brief:** [Verify every task without the maintainer](../specifications/quality-010-unattended-verification.md)
- **Status:** `done`
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

Reviewed plan, implemented under Result: (1) wake an idle device tunnel and
name the automation lock; (2) a `scrollTo` target shown whole by every
enclosing scroll area counts as reached; (3) iOS `springboard` scope,
no-break-space matching, `launchApp`, `openURL`, `pressKeys` with a Keychain
secret passed only through the runner environment (key taps filtered from the
xcodebuild log; `user-confirmed` 2026-09-24: the local `.xcresult` may keep
them), and the Screen Time consent fixture; (4) VMs as a location of the
desktop target with `vm create|sync|destroy|prompt`, an in-repo RFB client,
and an OCR mode in the Swift bridge; (5) README, skill, feature recipes, and a
contributor guide; (6) AC-03 on VMs and AC-04 on the test iPhone. Stage 3
(no separate plan review, maintainer away): `observe` for the desktop and
bundle-scoped iOS queries for the shield and Safari's blocked page.
`MACOS-020` is out of scope.

### Decisions

`user-confirmed` 2026-09-24: D1-D2 macOS 26 golden VM on the internal disk;
D3 VMs registered through the App Store Connect API; D4-D5 a dedicated wired
test iPhone (13 mini, iOS 26.5.2) as the test account's trusted device; D6 not
needed (`M2` passed on Virtualization's VNC); D7 local-network VNC accepted;
D8 test devices in English; D9 Face ID removed if possible, two-failure
fallback kept.

## Result

- Stage 1 setup, `observed` 2026-09-24: Tart 2.37.0, macOS 26.6.2 golden VM
  from the latest IPSW; `tart-guest-agent` replaces Remote Login (deviation).
- `M0` go: a VM keeps its provisioning identifier; a fresh clone inherits
  it; two VMs with one identifier running together re-identify one of them.
  AMFI rejects the development sync companion in an unregistered VM; after
  registering the primary and peer lines and recreating the profile it runs
  from the guest disk (not from a virtiofs share, where `codesign` fails).
- `M1` go: one sign-in with a code from the test iPhone; clones boot signed
  in with iCloud Keychain. Gap: the account's device list was not read.
- `M2` go: helper approval and 20 of 20 SecurityAgent prompts confirmed over
  VNC (`m2-loop-*`); Gatekeeper first open of the quarantined notarized
  1.0.0 approved 6 of 6, the first click only activating the dialog.
- `M3` go: the driver ran through `tart exec` once privacy approvals went to
  `tart-guest-agent`; `scrollTo` defect found (`m3-guest-scenario`).
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
- Stage 2, `observed` 2026-09-24: steps 1-5 implemented. Deviations: the VNC
  client also announces DesktopSize, LastRect, and QEMU key pseudo-encodings,
  since without DesktopSize the Virtualization server stops the VM; each
  capture uses a fresh connection; `vm prompt` gained `toggle`,
  `account-password`, `mac-password`, and `device-passcode` for privacy panes
  and iCloud renewal; desktop fixtures match the website row by fragment.
- AC-03 met (`ac03-*`): with nobody present, primary and peer clones onboarded
  with iCloud and helper approval, exchanged websites both ways, picked Safari
  in the helper panel, started a session that blocked the website and Safari,
  resumed after a relaunch, let the peer adopt and enforce it, ended early on
  both, and expired naturally in 306 s. Findings: a clone never receives a
  workspace key created by an earlier clone of its line (runs start from an
  empty workspace via the peer's Remove workspace), and repeated clones
  needed iCloud renewal, answered from the Keychain without a new code.
- AC-04 met (`ac04*`): a fresh iPhone install onboarded with iCloud join and
  Screen Time consent in one run, picked Calculator, blocked it and the
  website during a session, kept blocking after a relaunch, released both
  after an early end, synced a website to the Mac, and expired in 324 s.
- AC-05 met: iPhone `ac05-ios-blocked` and `-unblocked`, Mac VM
  `ac05-mac-blocked` and `-unblocked` (pause page and Safari ended, then the
  real page and Safari running); each opposite-state run failed as expected
  (`ac05-*-negative*`).
- Closeout, `user-confirmed` 2026-09-24: `AGENTS.md`, the skill, the guides,
  the quality contract, and the brief template make unattended verification
  mandatory and route provisioning to `posato-provisioning`, which gained
  `devices register --tart-vm` (run: both lines already registered).
  `posato-control` refuses the desktop target on the host Mac
  (`DESKTOP_HOST_REFUSED`, checked live); the peer golden VM holds both
  privacy grants.
- Open observation: one iPhone-started session reached the Mac only after
  **Sync now** on the iPhone (`ac05-repro-*`); the Mac has no push path.
  Candidate row for the maintainer; not fixed here.

## Verification

- `./gradlew :posato-control:check :posato-provisioning:check` pass after the last change.
- `./gradlew quality` passes (5 min) after the last source change.

## Blockers and accepted risks

- Two macOS guests at a time: the golden and peer VMs cannot run beside the `QUALITY-007` macOS 15 VM.
- A test iPhone restart needs one unlock by its owner before the driver runs.

## Final

- **Status:** `done`; the maintainer-ordered whole-change review follows.
- **Outcome:** AC-01 to AC-05 met; AC-04 with the accepted restart exception.
