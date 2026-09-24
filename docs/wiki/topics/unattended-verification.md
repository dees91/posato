# Unattended Verification

This page collects what `QUALITY-010` learned about verifying Posato with no
person at the Mac or the iPhone. It is written for any contributor who wants
to reproduce the environment; machine-specific names, paths, and account
values never belong here. The task brief and execution record hold the run
directories and the acceptance state.

## Target environment

- macOS: Posato runs inside Tart virtual machines on an Apple silicon Mac
  (Apple's Virtualization framework). One line of VMs is the primary target;
  a second line is the Mac-to-Mac sync peer.
- iOS: a dedicated physical test iPhone driven by the XCUITest driver in
  `tools/posato-control`.
- Both sign in to a dedicated test Apple Account, so CloudKit and iCloud
  Keychain data stay separate from any personal account. Development builds
  use the resettable CloudKit Development environment.

## macOS guests

### Creating a guest that can use iCloud

- `source-claim` (Apple Support 120468): an Apple Account works in a macOS
  guest only when host and guest run macOS 15 or later and the VM was created
  from an IPSW on the same host, for example
  `tart create <name> --from-ipsw latest`. Prebuilt registry images and
  upgraded older VMs cannot sign in. Apple Media Services, Find My, Wallet,
  and FileVault are unavailable in a guest.
- `observed` (2026-09-24, macOS 26 host and guest): Setup Assistant, the
  Apple Account sign-in, and iCloud Keychain all work in such a guest. The
  first sign-in needs one two-factor code from a trusted device; joining
  iCloud Keychain asks for the guest's login password.
- At most two macOS guests run at the same time on one host.

### Clones, identity, and provisioning

- `observed`: Tart has no snapshots, so a restore is `tart clone` of a golden
  VM. A clone boots with the golden VM's Apple Account session intact and can
  use CloudKit and the synchronizable Keychain with no prompt.
- `observed`: each VM has a provisioning identifier (System Information,
  "Provisioning UDID"). It is stable across restarts, a fresh clone inherits
  its source's identifier, and when two VMs with the same identifier run at
  the same time one of them is permanently given a new one. Never run a golden
  VM beside its own clone.
- `observed`: the sync companion carries restricted entitlements (iCloud and
  `keychain-access-groups`) from a development provisioning profile. AMFI
  rejects it in a VM whose identifier is not in that profile. Register one
  identifier per VM line as a Mac device and regenerate the companion's
  development profile; per-run clones then need no further registration.
  Profiles cannot be edited through the App Store Connect API, only deleted
  and recreated.
- `observed`: run the development package from the guest's own disk. From a
  virtiofs directory share `codesign` reports the bundle as unrecognized and
  AMFI kills the companion.

### Controlling a guest

- Use `tart-guest-agent` in the guest (a global LaunchAgent with
  `--run-agent`) and `tart exec` from the host. Commands then run in the
  logged-in user's Aqua session over the virtio channel, so the desktop
  driver's accessibility and screen-capture calls work and privacy approvals
  attach to the agent. An SSH session would sit outside the GUI session.
  `tart exec` can hang while the agent is not up; wrap it in a timeout.
- `tart run <vm> --no-graphics --vnc-experimental` starts Virtualization's
  own VNC server without opening a window on the host desktop. Keyboard and
  pointer events over it are hardware input to the guest.
  - `observed`: they complete Setup Assistant, System Settings privacy and
    Login Items toggles, and the SecurityAgent administrator prompt of the
    Posato helper.
  - Command is `Alt_L`; uppercase letters and shifted symbols need an
    explicit Shift press; mouse-wheel scrolling did not reach System Settings.
  - Locate controls by text recognition on the framebuffer rather than fixed
    coordinates.
  - The server listens on all host interfaces behind a short VNC password;
    use it on a trusted network or filter the port.
- Stop a guest from inside (`sync`, then `shutdown -h`) and wait for
  `tart run` to exit. `tart stop` can cut off the guest's last writes.
- Enable automatic login in the golden VM so a clone reaches the desktop
  without input.

### Posato in a guest

- `observed`: Accessibility and Screen Recording are granted once to
  `tart-guest-agent` in the golden VM. macOS 26 can additionally ask the
  agent to bypass the private window picker after screen captures; expect to
  answer it over VNC.
- `observed`: helper registration shows the background approval request;
  after the Login Items toggle, **Check again** reports the helper enabled.
- `observed`: a session start raises the helper's administrator prompt; once
  it is confirmed over VNC the session is active without Retry. An HTTP
  request to a paused domain returns the pause page, HTTPS `CONNECT` to it is
  refused, a control domain loads, a paused application is terminated within
  about two seconds, and everything is reachable again after an early end.
- `observed`: two guests on the same test account establish and join one
  workspace, and a website added on one arrives on the other after
  **Sync now**.

## Open

- `open`: Gatekeeper first-open handling, primary-network-service changes
  with one virtual interface, and the iPhone measurements (Screen Time
  consent, passcode, application picker, two-factor approval) are still being
  measured in `QUALITY-010` Stage 1.
- `open`: how `posato-control` will package these steps for contributors is
  decided in Stage 2.
