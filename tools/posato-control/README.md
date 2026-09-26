# posato-control

`posato-control` is the agent-facing driver for the Posato applications. It
builds, launches, drives, inspects, screenshots, and resets the macOS desktop
application and the iOS application on the Simulator and on a connected
iPhone. Every command prints one JSON envelope on standard output, so a coding
agent can verify its own change without a person in the loop.

It provides platform builds, inspection, and scenario-driven E2E verification.
The [testing policy](../../AGENTS.md#testing-policy) prefers these real user
flows for complex features and requires repeatable evidence from each run.
Scenarios run separately from `./gradlew quality` and CI; the aggregate gate
runs this module's ktlint, Detekt, unit tests, and Swift format check.

## Setup

```shell
./gradlew :posato-control:installDist
alias posato-control="$PWD/tools/posato-control/build/install/posato-control/bin/posato-control"
posato-control doctor
```

Run the tool from anywhere inside the repository checkout (it walks up to the
root `settings.gradle.kts`) or export `POSATO_REPO_ROOT`. The launcher needs a
Java 17 or newer runtime on `PATH`; Gradle itself still needs Temurin 21.

Prerequisites that `doctor` reports:

- Xcode with the iOS Simulator runtime, `xcrun simctl`, and `xcrun devicectl`.
- **macOS Accessibility** and **Screen Recording** access for the terminal or
  IDE process that runs the tool (System Settings > Privacy & Security). The
  desktop backend cannot read the Compose accessibility tree or capture the
  window without them; `doctor --request-permissions` triggers the prompts.
- An Apple development team for the physical iPhone, stored only in the
  ignored `local.properties` file.

### Local configuration

Values live in the ignored `local.properties` file (or an environment
variable, which wins). `doctor` reports presence and source, never values.

| Key | Environment variable | Purpose |
| --- | --- | --- |
| `posato.apple.developmentTeam` | `POSATO_APPLE_DEVELOPMENT_TEAM` | Automatic signing for the device build and the device driver. |
| `posato.macos.signingIdentity` | `POSATO_MACOS_SIGNING_IDENTITY` | Development signing for the staged desktop package (needed by the macOS application picker). |
| `posato.macos.syncProvisioningProfile` | `POSATO_MACOS_SYNC_PROVISIONING_PROFILE` | Absolute path to the untracked `app.posato.macos.sync` development profile that development signing requires. |
| `posato.control.simulator` | `POSATO_CONTROL_SIMULATOR` | Simulator name or UDID to use instead of the booted one. |
| `posato.control.device` | `POSATO_CONTROL_DEVICE` | Device name or UDID to use instead of the first connected iPhone. |
| `posato.control.devicePasscodeKeychainService` / `...Account` | `POSATO_CONTROL_DEVICE_PASSCODE_KEYCHAIN_SERVICE` / `..._ACCOUNT` | Login Keychain item holding the test iPhone passcode that `pressKeys` types. |
| `posato.vm.primaryGolden` / `posato.vm.peerGolden` / `posato.vm.legacyGolden` | `POSATO_VM_PRIMARY_GOLDEN` / `POSATO_VM_PEER_GOLDEN` / `POSATO_VM_LEGACY_GOLDEN` | Tart golden VMs of the primary, peer, and legacy (previous macOS version) lines. |
| `posato.vm.adminKeychainService` / `...Account` | `POSATO_VM_ADMIN_KEYCHAIN_SERVICE` / `..._ACCOUNT` | Login Keychain item holding the guest administrator password. |
| `posato.vm.accountKeychainService` / `...Account` | `POSATO_VM_ACCOUNT_KEYCHAIN_SERVICE` / `..._ACCOUNT` | Login Keychain item holding the test Apple Account password. |
| `posato.vm.accountPhoneKeychainService` | `POSATO_VM_ACCOUNT_PHONE_KEYCHAIN_SERVICE` | Login Keychain item (same account as above) holding the test account's trusted phone number, for iCloud re-verification. |
| — | `POSATO_CONTROL_TARGET` | Default for `--target`. |

When `posato.macos.signingIdentity` is an Apple Development identity, Gradle
packaging also needs the untracked development profile for App ID
`app.posato.macos.sync` (iCloud/CloudKit). Set
`posato.macos.syncProvisioningProfile` and `build -t desktop` passes it as
`-PposatoMacOsSyncProvisioningProfile`; `doctor` reports whether the profile's
App ID, expiry, and team match, without printing any of them or its path. The profile is never tracked.
`keychain-access-groups` comes from that team profile; it is not a separate App
ID capability. Without the profile the development package fails closed. Do not
ad-hoc-sign only the companion to bypass it; mixed signing is rejected.

`./gradlew quality` runs the packaging tasks without those properties, so it
restages an ad-hoc package and silently removes the application picker. Rerun
`build -t desktop` after it; `doctor` names this case on `desktop.staged`.

## Targets and the JSON envelope

Every command takes `--target/-t desktop|simulator|device` (`sim` is an
alias) plus the common options `--udid`, `--run-id`, `--artifacts`,
`--timeout`, `--human`, and `--verbose`. Options go after the command name:
`posato-control launch -t desktop --vm primary`.

The desktop target never drives the application on the host Mac, whose
installed Posato is the maintainer's real copy: outside a virtual machine
(`kern.hv_vmm_present` is 0) every desktop command except `build`, `doctor`,
and `artifacts` fails with `DESKTOP_HOST_REFUSED`. Add `--vm primary|peer|legacy`
([Tart VMs](#tart-vms)).

```json
{
  "ok": true,
  "command": "launch",
  "target": "desktop",
  "runId": "20260902-213901-2d4c",
  "durationMs": 1830,
  "result": { "pid": 86623, "logPath": "build/verification/runs/20260902-213901-2d4c/desktop-app.log", "windowId": 67889 },
  "artifacts": ["build/verification/runs/20260902-213901-2d4c/desktop-app.log"],
  "error": null
}
```

On failure `ok` is `false` and `error` carries `code`, `message`, and a
`hint` that names the next action. Exit codes:

| Exit | Meaning | Codes |
| --- | --- | --- |
| 0 | success | |
| 1 | the command failed | `COMMAND_FAILED`, `DRIVER_FAILED` |
| 2 | usage | `USAGE` (also argument parsing errors, which print the same envelope) |
| 3 | precondition, permission, or refusal | `TCC_ACCESSIBILITY_DENIED`, `TCC_SCREEN_RECORDING_DENIED`, `NO_BOOTED_SIMULATOR`, `NO_CONNECTED_DEVICE`, `DEVICE_AUTOMATION_LOCKED`, `VM_UNAVAILABLE`, `DESKTOP_HOST_REFUSED`, `DEVELOPMENT_TEAM_MISSING`, `APP_NOT_STAGED`, `APP_NOT_INSTALLED`, `APP_NOT_RUNNING`, `PROCESS_NOT_ALLOWED`, `PROCESS_NOT_INSPECTABLE`, `ALREADY_RUNNING`, `REFUSED_WITHOUT_CONFIRMATION` |
| 4 | element or expectation | `ELEMENT_NOT_FOUND`, `ELEMENT_AMBIGUOUS`, `WAIT_TIMEOUT`, `ASSERTION_FAILED`, `SCENARIO_INVALID` |
| 5 | build or install | `BUILD_FAILED`, `INSTALL_FAILED` |
| 6 | unsupported on this target | `UNSUPPORTED_ON_TARGET` |

## The provisioning gate

`doctor` reports every one-time condition a machine must satisfy before a run,
as a named check with a state and one remedy sentence:

| `state` | `ok` | `severity` | Meaning |
| --- | --- | --- | --- |
| `ok` | `true` | `info` | Observed and satisfied. |
| `missing` | `false` | `error`, `warn`, or `info` | Observed and not satisfied. |
| `unknown` | `false` | always `warn` | Not observable from the host. The remedy names the one command that reveals it. |

`result.ok` stays "no check is `missing` at `error` severity", so an `unknown`
condition is visible without blocking a run, and a condition is never guessed.
`error` is reserved for a configuration that packaging genuinely rejects: an
Apple Development identity that is not in the keychain, one that signs under a
different team than `posato.apple.developmentTeam`, or one configured without a
usable `app.posato.macos.sync` profile. An untouched ad-hoc checkout still
reports `ok: true`.

`desktop.signingIdentity` reads the team from the certificate subject's
organizational unit, not from the identifier inside its common name: in
`Apple Development: <name> (<id>)` that identifier belongs to the certificate,
not to the team, so the two are unrelated values. A team that cannot be read is
`unknown` rather than a guessed mismatch.

`desktop.helperBundle`, `desktop.proxyDaemon`, and `desktop.syncCompanion`
observe the three nested components of the staged package separately, so an
incomplete stage names the component that is missing instead of failing later
inside a recipe. `doctor` only reports presence; the content is verified at
build time, the helper and the daemon by `:desktopApp:verifyMacOsHelperStructure`
and the companion by `:desktopApp:verifyMacOsDevelopmentPackaging`, both of
which `build -t desktop --verify` runs.

`desktop.helperBackground` and `device.screenTime` are always `unknown`: the
macOS helper's background approval and Screen Time authorization are readable
only by the application itself. The tool reports what is missing; it never
grants a permission.

## Commands

| Command | Targets | What it does |
| --- | --- | --- |
| `doctor [--deep] [--request-permissions]` | all (or every target when `-t` is omitted) | Toolchain, configuration presence, TCC permissions, staged/installed/running state, driver state. With `--vm` the guest reports runtime checks only; build prerequisites (Xcode, Gradle wrapper) are checked by `doctor` on the host. |
| `devices list` / `devices boot [--device-type "iPhone 17"]` / `devices shutdown` | — | Simulator and paired-iPhone inventory; boot or shut down simulators. |
| `build [--signing-identity X] [--verify] [--driver]` | all | Desktop: `:desktopApp:stageMacOsDevelopmentPackage`. iOS: a Debug `xcodebuild` with persistent DerivedData under `build/verification/derived-data/`. `--driver` also builds the XCUITest driver, which is otherwise rebuilt on demand whenever its sources are newer than the last build. |
| `install` | simulator, device | `simctl install` or `devicectl device install app`. |
| `launch [--fresh] [--capture-logs] [--build] [--arg A] [--env K=V] [--adopt]` | all | Starts the app and tracks it in `build/verification/state.json`. Desktop launches the staged `Posato.app` binary, or the installed candidate after `vm install`, and records its window id. `--adopt` (desktop only, alone) tracks the one running instance instead, such as the build an update relaunched; it has no captured log. |
| `terminate` | all | Stops only the instance this tool started or adopted, on the simulator or device it was launched on; it does nothing when nothing is tracked. |
| `status` | all | Installed, running, pid, app path, container path, signing mode. |
| `screenshot [--name n] [--out file]` | all | Desktop window capture, `simctl io screenshot`, or a driver screenshot on the device. |
| `snapshot [--format json\|text] [--max-depth n] [query]` | all | Unified accessibility tree. `--format text` prints an outline with roles, labels, and desktop paths. |
| `find <query>` | all | Matching elements; never changes application state (on iOS it starts the app when it is not running). |
| `tap <query>` | all | Presses a button or taps an element. |
| `type <query> --input TEXT [--clear] [--submit]` | all | Types into a text field. |
| `press --key <key> [--modifiers cmd,shift]` | all | Keyboard input. Desktop: `return`, `escape`, `tab`, `delete`, `space`, arrows, digits, letters, with `--modifiers`. iOS: `return`, `delete`, `space`, `home`, `volumeUp`, `volumeDown` (device only), and `escape`/`tab` where the keyboard offers them. |
| `orient --to portrait\|portraitUpsideDown\|landscapeLeft\|landscapeRight` | simulator, device | Rotates the device through XCUITest and waits until the application window has the matching aspect and has settled. A target that shares the current aspect first goes through the other aspect, so every rotation is observable and an orientation the application does not support fails. The orientation outlives the run. The desktop refuses the command. |
| `wait --for exists\|absent\|enabled\|disabled\|settled [query] [--timeout-seconds 10]` | all | Polls until the condition holds. |
| `run --scenario file.json` (or `-`) | all | Runs a batched scenario and reports every step with its evidence. The primary path on iOS. |
| `logs [--tail n] [--stream-seconds s]` | all | Captured application log; the simulator can also stream the unified log for a few seconds. |
| `db path` / `db query --sql "…" [--database name]` | desktop, simulator | Read-only SQLite access to the local databases. |
| `reset [--dry-run] [--yes] [--keep-install]` | all | Deletes local state (desktop, simulator) or uninstalls (device). Refuses without `--yes`; deleted files are backed up into the run directory. |
| `cleanup [--dry-run] [--purge-derived-data]` | all | Stops tracked processes; never deletes run evidence. |
| `artifacts` | — | Prints the run directory layout. |
| `observe [--website URL] [--application NAME] --expect blocked\|allowed [--seconds N]` | desktop in a VM | Meets enforcement as a person would: requests the URL through the system proxy `scutil --proxy` reports, following up to five redirects (`paused` when the helper's pause page answers, `loaded` only for a final 2xx page), and opens the application by its bundle identifier, which counts as blocked when Launch Services lists no process for that bundle after N seconds (default 8). Fails with `ASSERTION_FAILED` when the observation contradicts `--expect`. iOS uses `observe-blocking-ios.json` and `observe-unblocked-ios.json`. |
| `menu [--open] [--choose TITLE]` | desktop in a VM | Reads the resident application's status-bar menu through its accessibility tree (description, item titles, enabled states); `--open` opens it through the status item's accessibility press, as VoiceOver does, and `--choose` presses the item with that exact title. Works with the window closed. |
| `close-window` | desktop in a VM | Presses the close button of the window titled Posato, which hides a resident application instead of quitting it. |
| `resources [--seconds N]` | desktop in a VM | Samples the application and its helper for N seconds (default 600): physical footprint, CPU seconds used, and idle wakeups per second from `top`. |
| `vm create\|sync\|destroy [--line primary\|peer\|legacy]` | desktop in a VM | Clone the line's golden Tart VM, boot it headless, and copy the staged package and driver onto its disk; recopy; shut down from inside and delete. `create` also reports `iCloudKeychain`; `destroy` refuses a running guest whose database shows a linked iCloud workspace (`WORKSPACE_LINKED`) unless `--keep-workspace`; a stopped guest or an unreadable database is not checked. See [Tart VMs](#tart-vms). |
| `vm icloud [--line] [--resume]` | desktop in a VM | Read iCloud Keychain's state from System Settings (`syncing`, `unknown`; exit 3 `ICLOUD_KEYCHAIN_PAUSED` when paused or signed out); `--resume` runs Resume Data Sync and answers its dialogs. |
| `vm prompt <kind> [--line] [--row text]` | desktop in a VM | Answer a system dialog over VNC: `admin`, `background`, `toggle`, `account-password`, `mac-password`, `device-passcode`, `gatekeeper`, `picker-bypass`. |
| `vm install --dmg file [--line] [--replace] [--app-label Posato] [--applications-label /Applications]` | desktop in a VM | Install a notarized candidate as a person would and drive it from then on; `--replace` first moves an installed release to the Trash. See [Candidates](#notarized-candidates). |
| `vm click\|press\|screenshot [--line]` | desktop in a VM | Click recognized text, press a key or chord, or capture the whole guest screen. |
| `vm text [--line] [--contains text]` | desktop in a VM | Print the recognized screen text, top to bottom, with positions; read dialogs this way instead of viewing screenshots. |
| `vm wait-text --text text [--line] [--exact] [--absent] [--timeout-seconds]` | desktop in a VM | Wait until recognized text appears or, with `--absent`, disappears. |
| `vm scroll --text text [--clicks N] [--line]` | desktop in a VM | Turn the mouse wheel over recognized text, positive `N` down and negative up, for Compose scroll areas that `scrollTo` cannot move, such as This Mac options below the window edge. |
| `vm exec --script script [--line]` | desktop in a VM | Run a `/bin/sh` script as the logged-in guest user and return its exit code and output, for checks the application interface cannot show, such as whether a process accepts a Java attach. |
| `vm type --text T \| --secret admin\|account\|phone [--strip-prefix P] [--line]` | desktop in a VM | Type text, or a Keychain secret without echoing it, into the focused guest field. |
| `vm boot\|shutdown [--line]` | desktop in a VM | Boot the line's existing VM without cloning (to prepare a golden image under the clone's name), or shut it down from inside and keep it; `forced` reports a fallback to `tart stop`. |
| `vm drag --from label --to label [--from-index n] [--to-index n] [--line]` | desktop in a VM | Drag one recognized label onto another, such as an application onto the Applications link. |

### Element queries

`snapshot`, `find`, `tap`, `type`, and `wait` share these options, and
scenario steps use the same keys in a `query` object:

| Option | Key | Matches |
| --- | --- | --- |
| `--id` | `id` | iOS accessibility identifier (a Compose `testTag`). Not exposed on the desktop. |
| `--text` | `text` | Exact label, value, or placeholder. |
| `--text-contains` | `textContains` | Substring of the label or value. |
| `--role` | `role` | `button`, `textField`, `text`, `group`, `window`, `progress`, `other`, `any`. |
| `--index` | `index` | The nth match, 0-based. |
| `--path` | `path` | Desktop accessibility path from a previous snapshot, e.g. `0/0/0/0/9/2`. |
| `--within-text` + `--within-role` | `within` | Scope: the nearest ancestor with the given role of the element carrying the text; the query then matches inside that scope. Works where the platform exposes containers (desktop rows). |
| — | `scope` | iOS only: `springboard` addresses system dialogs and sheets (Screen Time consent, the passcode keypad) without activating SpringBoard; a bundle identifier such as `com.apple.mobilesafari` addresses that application's tree, for example a Screen Time shield or a blocked page. |
| `--near-text` + `--near-role` | `near` | Prefer the match closest to the element carrying the text, with vertical distance weighted three times, so a control on the anchor's row wins over the neighbouring row; `index` then picks farther matches. Works on every target, including iOS lists whose rows expose no container. |

Example: open a website's menu with
`--text "Actions for example.com" --role button`, then choose
`--text Remove --role button`. Website add/search/edit surfaces each expose
one text field, so use `--role textField` after selecting the surface.
Nested Websites / Apps tabs include counts in their labels; select with
`--text-contains Websites --role button`, not a hardcoded count.

Desktop buttons expose labels; fields may expose only their value, and
`testTag` is not exposed. On iOS a focused field may append its value to its
label. Avoid selectors tied to the complete dynamic label. Desktop taps use
accessibility actions without explicit activation. Typing, key presses, and
`scrollTo` bring the tracked window forward: native verification found that
per-process key delivery alone did not populate the background Compose field.
The helper's non-inspectable picker also needs foreground delivery. Keep the
Mac unlocked and avoid competing foreground automation during input.

### Process targeting (desktop only)

`snapshot`, `find`, `tap`, `type`, `press`, `wait`, and `screenshot` accept
`--process <name|pid>`, which addresses another process inside the staged
`Posato.app` instead of the tracked application. The name is an executable file
name, for example `PosatoMacOSHelper`. Without the option nothing changes.

Resolution is deliberately narrow. The tracked application must be running,
and only a process whose executable resolves inside the staged bundle — after
symbolic links are followed — can be addressed. A pid outside the bundle, a
name that matches nothing, and a name that matches several processes are all
refused with `PROCESS_NOT_ALLOWED` and exit code 3, and the hint lists what is
addressable. `run --scenario` has no selector: scenarios stay on the tracked
application. On the Simulator and the device the option is
`UNSUPPORTED_ON_TARGET`, because those targets address the app by bundle
identifier and have no pid path.

The macOS helper is the reason this exists, and it also shows the limit
(`observed`, 2026-09-04). The helper presents its open panel with
`NSOpenPanel.runModal()` and never runs an `NSApplication` event loop, so it
owns a real window while exposing no accessibility server. Consequences:

- `snapshot` and `find` on it fail with `PROCESS_NOT_INSPECTABLE` rather than
  returning an empty tree, so an absent element is never mistaken for an
  absent window.
- `press` and `type` still reach it. When the addressed process answers
  accessibility requests the events are posted to it directly, exactly as
  before; when it does not, the bridge brings that process forward and posts
  to the session tap instead. The activation is why the events land in the
  window you addressed and nowhere else, and a process that cannot be brought
  forward is refused rather than typed into blindly.
- `type` with `--process` and no element query types into whatever that
  process has focused. This is the only way into a window with no tree.
- `wait --process <name>` with no query waits on that process's window:
  `--for exists` is the readiness gate after the action that opens it, and
  `--for absent` asserts it closed, which a process that exited also satisfies.
  A selector that never resolved, a misspelled name for instance, is
  indistinguishable from one that exited and so satisfies `absent` at once;
  assert `exists` first when the point is that a window was there and went away.
  Every other `--for` state is refused as `USAGE`, because the tree-based states
  would answer from an empty query instead of observing the window.
- `screenshot --process <name>` captures that process's largest window at any
  window layer, so a panel above the application window is captured; without
  the selector the tracked application is still captured at layer 0.

### Snapshot node

```json
{ "role": "button", "id": null, "label": "Add", "value": null, "enabled": true, "focused": false,
  "frame": { "x": 128, "y": 561, "w": 133, "h": 40 }, "platformRole": "AXButton", "path": "0/0/0/0/9/2", "children": [] }
```

### Scenario file

```json
{
  "version": 1,
  "launch": { "terminateExisting": true, "fresh": false, "arguments": [], "environment": {} },
  "defaults": { "timeoutSeconds": 10 },
  "onFailure": { "screenshot": true, "snapshot": true },
  "continueOnFailure": false,
  "steps": [
    { "name": "ready", "action": "waitFor", "state": "exists", "query": { "text": "Paused items", "role": "button" }, "timeoutSeconds": 30 },
    { "name": "open-paused-items", "action": "tap", "query": { "text": "Paused items", "role": "button" } },
    { "name": "targets-ready", "action": "waitFor", "state": "exists", "query": { "text": "Search" }, "timeoutSeconds": 30 },
    { "name": "enter-domain", "action": "type", "query": { "role": "textField" }, "text": "example.com", "clear": true, "submit": true },
    { "name": "finish-adding", "action": "tap", "query": { "text": "Done", "role": "button" } },
    { "name": "row-visible", "action": "scrollTo", "query": { "text": "example.com", "role": "text", "within": { "text": "Saved websites", "role": "group" } } },
    { "name": "after-add", "action": "screenshot" },
    { "name": "after-add", "action": "snapshot" }
  ]
}
```

The shell accounts for the software keyboard. Batch Add / Return keeps focus
for the next entry; tap Done to hide the keyboard and restore iOS bottom
navigation. Search / Back to adding and category changes also clear focus.
Rejected text stays editable and does not require a relaunch.

Actions: `waitFor` (`exists`, `absent`, `enabled`, `disabled`, `settled`),
`tap`, `type` (`text`, `clear`, `submit`), `press` (`key`, `modifiers`),
`assert`, `screenshot`, `snapshot` (`query`, `maxDepth`), `sleep`
(`seconds`), `scrollTo`, `orient` (`orientation`; iOS only), `terminate`, and
`relaunch`. iOS only: `launchApp` (`bundleId`, brought forward without being
terminated), `openURL` (`url`), and `pressKeys` (`secret`: the name
`devicePasscode`; the host reads the value from the configured Keychain item
and passes it only through the test runner environment, and key-tap lines are
removed from the xcodebuild log). Any step can set `optional: true`: a missing
element or a wait timeout then passes, for surfaces that appear only on some
paths such as a Face ID retry. On iOS a pending system sheet belongs to the
running app, and activating the app for a new driver run dismisses it, so a
consent flow must stay inside one scenario (see
`fixtures/scenarios/screen-time-consent.json`).

`scrollTo` performs native scrolling on both hosts, not an existence check.
It chooses the largest visible scroll area in the requested scope, moves
toward the end, reverses when visible content stops changing, and stops at the
timeout or attempt bound. A matching row must be inside the viewport. Use
`query.within: {"text":"Saved websites","role":"group"}` for website rows,
including on iOS where the outer Compose scroll wrapper spans the whole screen.
On compact Session (iPhone 13 mini), that wrapper fills the window and does
not move the inner vertical scroll; `scrollTo` then swipes the screen instead
of dragging the wrapper. Session's iCloud and Mac-only This Mac rows are
initially collapsed. Tap the row by `textContains` (`"iCloud,"` or
`"This Mac,"`) with `role: "button"` before addressing its actions, then
unscoped `scrollTo` for the offscreen action. The
[sync recipe](../../.agents/skills/verify-posato/features/sync.md) has the
compact-iPhone sequence; the
[Session recipe](../../.agents/skills/verify-posato/features/sessions.md)
covers explicit helper checks.
`scrollTo` reports reached only when the element's centre is on screen, so
a following tap lands on the element and not on a bar clipping its edge.
Visibility does not require static row text to be individually tappable.
When `scrollTo` fails on a collapsed row's actions, check whether the
section is expanded: a failure snapshot showing only the `iCloud,` header
and none of its actions is the signature of a still-collapsed section,
so expand it first and retry before assuming scrolling cannot reach them.
The 50-row fixture allows 60 seconds per step; cleanup allows 90 seconds for
finding the first row from an arbitrary retained scroll position. Desktop pointer and wheel
events are sent through the session event tap only after validating the
tracked process and bringing its window forward; losing the foreground is
a refusal. Coordinates come from the current accessibility bounds, never
hardcoded screen positions. Read-only find/wait/snapshot do not scroll.

`launch.fresh` resets application state, so the app opens on the first-install
flow instead of `Session`. `terminateExisting: true` (the
scenario default) restarts the app; false reuses it. A failed step records
`failure-<index>-screenshot.png` and `failure-<index>-snapshot.json`.

Canonical scenarios in `fixtures/scenarios/`:

- `first-install.json` / `first-install-skip.json`: the six-step first-run
  flow on a fresh database (`launch --fresh` or `reset`), full and skip
  variants. Run the skip variant as a prelude before any older recipe after a
  fresh launch or reset; older recipes assume the app opens on `Session`.
- `website-edit.json`: short-list editor/keyboard regression, draft retention,
  cancellation, saved edit and cleanup read back after real relaunches. Reserve
  `edit-proof.example` and `changed-proof.example` before running.
- add-website and remove-website (including desktop variants) use inline Add,
  Done, real scrolling, and row menus.
- website-batch-list adds 50 synthetic domains plus duplicate/invalid input,
  tests both list ends, search, and draft retention. Its cleanup companion
  removes those 50 domains through UI. Reserve these names before running.
- session-start variants select 25 minutes and use the real review/start path.
- session-early-end relaunches before verifying and ending the active session.
- session-expiry variants select a real five-minute duration with arrow
  buttons, await natural expiry, then remove the fixture website.

Read the maintained [.agents/skills/verify-posato](../../.agents/skills/verify-posato/SKILL.md)
and its feature map for exact setup, native picker limits, and cleanup.

## Tart VMs

`-t desktop --vm primary|peer|legacy` runs a desktop command inside a Tart guest: the
host forwards it to the guest's own copy of the driver through `tart exec`,
which runs in the logged-in user's Aqua session, sends a scenario file (or,
for `--scenario -`, the host's own standard input) on standard input, and copies the guest's run directory to
`build/verification/runs/<run>/guest/`. `build` stays on the host; follow it
with `vm sync`. The one-time setup of the golden VMs, device registration,
test Apple Account, and Keychain items is in
[the unattended verification guide](../../docs/development/unattended-verification.md).

- `vm create` refuses while the line's golden VM runs (a golden VM and its
  clone share a provisioning identity, and running both re-identifies one) or
  when two guests already run. The VNC address, which carries the session
  password, stays in an owner-only file under `build/verification/vm/<line>/`
  and is deleted by `vm destroy`.
- `vm prompt` locates dialogs by text recognition on the framebuffer and types
  secrets from the login Keychain; labels assume an English guest. The first
  click on a dialog that is not frontmost only activates it; the prompts click
  twice where needed.
- Code signatures do not validate from a directory share, so the package runs
  from the guest disk; only the JDK comes from a read-only share.

### Notarized candidates

`vm install --dmg <candidate>.dmg` replaces the development package in a clone
with a notarized candidate, for update and release checks:

1. It refuses when Posato runs or `/Applications/Posato.app` exists; a later
   build arrives through the update path, so each candidate needs a fresh clone.
   The exception is the manual move between releases, such as from 1.0, which
   has no updater, to a later version: with `--replace`, the installed
   application goes to the guest user's Trash first, as Finder's Replace does,
   and the user's data stays where it is. The result names the replaced
   version.
2. The image is copied to the guest and quarantined, as a download would be,
   then opened in Finder. The application is dragged onto the Applications
   link over VNC. A copy made with `ditto`, `cp`, or a scripted Finder
   `duplicate` is translocated at launch, and Sparkle refuses to update a
   translocated application; the drag sets the quarantine flag that prevents
   it.
3. The image is ejected, the synced development package deleted, and every
   other Posato bundle unregistered from LaunchServices (the golden image
   keeps stale entries). A second registered bundle can take over launch
   resolution for the helper and the installer, so the command fails if one
   remains.
4. It records Gatekeeper's assessment and the signature, opens the candidate
   through LaunchServices, answers Gatekeeper's first-open question, requires
   that the process runs from `/Applications`, and quits it. A replaced
   installation whose setup is complete opens with the update-consent sheet,
   which refuses the quit; the command then terminates the process instead of
   answering the sheet, and records that as `firstOpenQuit`.
5. A marker under the guest's `build/verification/` then points every desktop
   command at `/Applications/Posato.app`. `vm sync` copies only the driver and
   repeats the single-bundle check.
6. `launch` then opens the candidate through LaunchServices, as Finder does,
   and tracks the process it starts. Launched by the guest agent directly, the
   agent would be responsible for the application: macOS records its App
   Management denial for the agent, and Sparkle then asks for an administrator
   for every later update (`observed` 2026-09-25).

The evidence is `candidate-install.json` in the run directory. After an
update relaunches the application, `launch -t desktop --vm <line> --adopt`
tracks the new process.

## Evidence and state

- `build/verification/runs/<run-id>/` holds screenshots, snapshots, logs,
  driver result bundles, and reset backups. `build/verification/latest`
  points at the newest run that produced artifacts.
- `build/verification/state.json` tracks the processes this tool started;
  `terminate` and `cleanup` only touch those.
- `build/verification/transcript.log` records every helper command per run
  id; `--verbose` echoes them to standard error as well.
- Everything under `build/` is ignored by Git. Keep it that way: screenshots,
  logs, device identifiers, and paths never enter tracked files.

## How each target works

**desktop.** `build` stages `desktopApp/build/compose/binaries/main/development-package/Posato.app`
(ad-hoc signed unless `posato.macos.signingIdentity` is set; the macOS
application picker requires development signing). An Apple Development identity
also requires `-PposatoMacOsSyncProvisioningProfile` pointing at an untracked
`.provisionprofile` for `app.posato.macos.sync`; mixed ad-hoc companion signing
is not a workaround. `launch` runs the bundle's executable with its output in
the run directory. Inspection and interaction use
a single-file Swift bridge (`native/macos/PosatoAxBridge.swift`) compiled on
demand into `build/verification/native/`; it reads the accessibility tree,
presses buttons, focuses fields and types through keyboard events, and reports
window ids for `screencapture`. Databases: `~/Library/Application Support/Posato/`.

**simulator.** `build` runs `xcodebuild` against `iosApp/iosApp.xcodeproj`
for the booted (or configured) simulator with signing disabled; `install`,
`launch` (with stdout/stderr capture), `terminate`, `screenshot`, and `logs`
use `simctl`. `db` reads the policy database inside the app container.
Interaction goes through the XCUITest driver.

**device.** `build` signs with the configured team through automatic signing
(the driver signs with the same wildcard development profile and needs no
entitlement); `install`, `launch`, and `terminate` use `devicectl`.
`launch --capture-logs` keeps a `devicectl --console` attachment whose output
`logs` reads; the application lives only as long as that attachment, so
`terminate` ends both. Screenshots and all interaction go through the driver;
`db` is unsupported, and `reset` means uninstall.

**iOS driver.** `ios-driver/PosatoDriver.xcodeproj` contains a stub host app
and a UI-testing bundle that drives the installed Posato app by bundle
identifier. `build --driver` (or the first interaction command) runs
`xcodebuild build-for-testing`; every `snapshot`, `tap`, `type`, `wait`,
`run`, and device `screenshot` then runs `xcodebuild test-without-building`
with the scenario passed as a base64 environment variable and reads the
attachments back with `xcresulttool`. Each invocation costs roughly 5 s of
XCUITest startup on the Simulator and 6–12 s on an unlocked iPhone (about a
minute when the runner must be reinstalled or the phone is locked), so prefer
`run --scenario` over many single commands on iOS. The xcodebuild transcript
of every driver run is kept next to its result bundle in the run directory.

## Workflow for an agent

1. `doctor -t <target>` and act on any failed check.
2. `build -t <target>` (add `--driver` on iOS the first time), then `install`
   on iOS.
3. `launch -t <target>` to preserve state. Use `--fresh` only when an empty
   disposable target is intended; it is not routine developer-data cleanup.
4. `snapshot --format text` to learn the current labels, then `run --scenario`
   or single `tap`/`type`/`wait` commands.
5. `screenshot` and `db query` to collect evidence; `logs` when something is
   off.
6. Restore only the rows/selections changed by the run, then `terminate`
   and `cleanup`. Never reset developer data as a substitute for scoped cleanup.

## Development

```shell
./gradlew :posato-control:test :posato-control:detekt :posato-control:ktlintCheck :posato-control:swiftFormatCheck
```

Unit tests cover the query matcher, the scenario runner, the JSON contracts,
and local configuration parsing against synthetic fixtures. End-to-end checks
run the sequences above against the real applications.
