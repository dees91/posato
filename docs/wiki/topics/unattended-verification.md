# Unattended Verification

This page collects what `QUALITY-010` learned about verifying Posato with no
person at the Mac or the iPhone. It is written for any contributor who wants
to reproduce the environment; machine-specific names, paths, and account
values never belong here. The task brief and execution record hold the run
directories and the acceptance state.

`user-confirmed` 2026-09-24: this environment is the mandatory way every task
verifies its change (`AGENTS.md`, Application verification), and the macOS
application never runs on the maintainer's own Mac; `posato-control` refuses
desktop commands outside a virtual machine except `build`, `doctor`, and
`artifacts`.

## Test selection and evidence

`user-confirmed` 2026-09-26: prefer E2E as the sole testing mechanism when it
exposes the relevant failures of a complex feature. The
[quality contract](../../development/engineering-quality-contract.md#tests-and-runtime-checks)
and `AGENTS.md` own this policy. Isolated tests require a failure inventory
before implementation and must expose a failure that stronger existing proof
misses. New unit tests precede the implementation or bug fix.

Each E2E run ends with repeatable proof: revision, target, setup, command or
scenario, expected and actual outcome, and an ignored evidence directory.
Passing a normal user flow does not establish malformed-input rejection,
cryptographic failure handling, migration safety, or race behavior. Retain
independent tests for those contracts until stronger proof covers them.

`observed` during the September 2026 test audit: some tests asserted copied
test-only readiness logic, unused privacy canaries, or assertions already
covered by stronger neighboring tests. Those patterns do not establish the
production behavior their names suggest. Judge assertions and supplied inputs
before treating a passing test as evidence.

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
- `observed`: a VNC client must announce the DesktopSize pseudo-encoding
  (-223). Without it Virtualization's VNC server hits an internal assertion
  and stops the whole VM. Several captures on one connection can also stall;
  a fresh connection per capture is reliable.
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
- `observed`: the administrator prompt was confirmed over VNC in 20 of 20
  consecutive session starts, each about ten seconds after the start press.
- `observed`: Gatekeeper's first-open dialog for a quarantined, notarized
  release was approved over VNC every time; the first click only activates
  the dialog, the second presses **Open**. A fresh clone resets the approval.
- `observed`: two guests on the same test account establish and join one
  workspace, and a website added on one arrives on the other after
  **Sync now**.
- `observed`: clones of one golden VM share an iCloud Keychain identity. A
  synchronizable item written by one clone never reached a later clone of the
  same line, while the other line received it at once. A run therefore starts
  from an empty workspace: the peer removes the old one, the primary
  establishes, the peer joins.
- `observed`: after many clones of one golden VM, macOS reported that the Mac
  could not connect to iCloud and asked for the account password; iCloud
  Keychain in the other line asked for the Mac password and a trusted
  iPhone's passcode. All three were answered over VNC from the Keychain, with
  no new two-factor code.
- `observed`: a full core flow ran with nobody present: onboarding with iCloud
  and helper approval on both lines, website exchange in both directions, the
  application picker through the helper panel, a session blocking a website
  and Safari, a relaunch with Resume restrictions, the peer adopting and
  enforcing the session, an early end clearing both, and a natural expiry.
- `observed`: a primary-network-service change is testable with one virtual
  interface. Add a second service on the same interface
  (`networksetup -createnetworkservice "<name>" en0`) and disable the first;
  the recipe then sees the ADR 0005 restore and Retry path. Disposable clones
  make destructive network cases cheap: a broken guest is deleted, not
  repaired.

### Previous macOS version (`QUALITY-007`)

`observed` (2026-09-25):
- A golden VM on the previous macOS major version can be prepared with no person present:
  - Setup Assistant runs over VNC with `vm click`, `vm press`, and `vm type`;
  - the guest agent comes from a host-served, checksum-verified setup script;
  - the two-factor code is read from the test iPhone's screenshot after a `springboard`-scoped scenario taps Allow.
- Apple publishes no restore image (IPSW) for every point release. For macOS 15 the newest one is 15.6.1.
- `vm install`, LaunchServices launch, the helper, enforcement, and iCloud sync all work on macOS 15.6.1. Cross-device sync with a macOS 26 guest works in both directions.
- When a new device signs in to the test account, the existing golden VMs can report "Some iCloud Data Isn't Syncing". iCloud Keychain items then stop reaching them, and Posato waits for the workspace key. Resume Data Sync with the account and guest passwords restores them. `superseded` (2026-09-25, `RELEASE-003`): a fix in a clone does not carry over to the golden VM; later clones were paused again until the golden VM itself was repaired. `vm create` now reports the state, and `vm icloud --resume` repairs it.

## iPhone

- `observed`: the first XCUITest run on a new device fails with "Timed out
  while enabling automation mode" until UI Automation is enabled on the device
  (Settings, Developer). The next run shows a passcode prompt for XCTest; after
  its owner enters it once, about 25 further runs asked nothing. A restart
  needs one unlock by the owner anyway.
- `observed`: the CoreDevice tunnel to a wired iPhone idles within about a
  minute; `devicectl list` then shows "available (paired)" and the driver
  reports no device until a call such as
  `xcrun devicectl device info details --device <id>` wakes it.
- `observed`: the Screen Time consent is reachable through SpringBoard
  (`XCUIApplication(bundleIdentifier: "com.apple.springboard")`) without
  activating it: the first sheet's Continue, then Allow with Face ID. With
  nobody in front of the phone Face ID fails twice and offers Enter Passcode;
  the passcode keypad exposes keys `0` to `9`. The authentication buttons have
  stable `com.apple.localauthentication.ax.authentication.button.*`
  identifiers, and system labels can contain no-break spaces, so match by
  identifier or by a fragment rather than a whole localized label. A test
  device without Face ID enrolled goes straight to the passcode.
- `observed`: the Family Controls application picker is not opaque to
  XCUITest: its rows (switches labeled with the application name), search
  field, and Save are in the Posato app's own accessibility tree.
- `observed`: during a session a paused application shows the Screen Time
  shield in the SpringBoard tree ("You cannot use Calculator because it is
  restricted.") and the paused domain shows "Website Not Allowed" in Safari's
  own tree (`com.apple.mobilesafari`), so the driver asserts blocking and its
  release with queries scoped to those bundles; each assertion fails in the
  opposite state.
- `observed`: the Mac pulls remote changes only on its own triggers (window
  foreground, **Sync now**, its own session change); nothing pushes. One
  iPhone-started session reached CloudKit only after **Sync now** on the
  iPhone, although the start requests a sync; `inferred`: that upload was
  interrupted and is not retried until the next foreground or manual sync.

## Observed blocking on macOS

- `observed`: `posato-control observe` in a guest requests a URL through the
  proxy `scutil --proxy` reports and opens an application. During a session
  the helper's pause page answers ("This site is paused") and Safari no longer
  runs after eight seconds; after an early end the real page loads directly
  and Safari keeps running. An adopted remote session on a peer Mac enforces
  only after its own **Resume restrictions** and administrator prompt.

## Open

- `open`: approving a two-factor sign-in request on the test iPhone through
  SpringBoard, and how often the automation-mode passcode returns over days.
- `open`: a guest network toggle and an Apple Account sign-out in a VM for the
  offline-retry and account-gate sync steps.
- `user-confirmed` 2026-09-25: the interrupted post-start upload on iOS is
  backlog row `SYNC-020` (reproduce, then automatic retry and background
  time); a push path to the Mac is not part of it.
- `observed` answer to the packaging question: `posato-control` treats a VM as a
  location of the desktop target (`--vm primary|peer`) with `vm create`,
  `sync`, `destroy`, and `prompt`; the one-time setup is in
  `docs/development/unattended-verification.md`.
