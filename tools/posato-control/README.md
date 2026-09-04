# posato-control

`posato-control` is the agent-facing driver for the Posato applications. It
builds, launches, drives, inspects, screenshots, and resets the macOS desktop
application and the iOS application on the Simulator and on a connected
iPhone. Every command prints one JSON envelope on standard output, so a coding
agent can verify its own change without a person in the loop.

It is a platform-build and manual-inspection driver, not an automated UI test
suite. Nothing it drives runs in `./gradlew quality` or in CI; the aggregate
gate only runs this module's ktlint, Detekt, unit tests, and Swift format
check.

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
| `posato.control.simulator` | `POSATO_CONTROL_SIMULATOR` | Simulator name or UDID to use instead of the booted one. |
| `posato.control.device` | `POSATO_CONTROL_DEVICE` | Device name or UDID to use instead of the first connected iPhone. |
| — | `POSATO_CONTROL_TARGET` | Default for `--target`. |

When `posato.macos.signingIdentity` is an Apple Development identity, Gradle
packaging also needs
`-PposatoMacOsSyncProvisioningProfile=/absolute/path/to/untracked.provisionprofile`
for App ID `app.posato.macos.sync` (iCloud/CloudKit). The profile is never
tracked. `keychain-access-groups` comes from that team profile; it is not a
separate App ID capability. Without the profile the development package fails
closed. Do not ad-hoc-sign only the companion to bypass it; mixed signing is
rejected.

## Targets and the JSON envelope

Every command takes `--target/-t desktop|simulator|device` (`sim` is an
alias) plus the common options `--udid`, `--run-id`, `--artifacts`,
`--timeout`, `--human`, and `--verbose`. Options go after the command name:
`posato-control launch -t desktop`.

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
| 3 | precondition, permission, or refusal | `TCC_ACCESSIBILITY_DENIED`, `TCC_SCREEN_RECORDING_DENIED`, `NO_BOOTED_SIMULATOR`, `NO_CONNECTED_DEVICE`, `DEVELOPMENT_TEAM_MISSING`, `APP_NOT_STAGED`, `APP_NOT_INSTALLED`, `APP_NOT_RUNNING`, `ALREADY_RUNNING`, `REFUSED_WITHOUT_CONFIRMATION` |
| 4 | element or expectation | `ELEMENT_NOT_FOUND`, `ELEMENT_AMBIGUOUS`, `WAIT_TIMEOUT`, `ASSERTION_FAILED`, `SCENARIO_INVALID` |
| 5 | build or install | `BUILD_FAILED`, `INSTALL_FAILED` |
| 6 | unsupported on this target | `UNSUPPORTED_ON_TARGET` |

## Commands

| Command | Targets | What it does |
| --- | --- | --- |
| `doctor [--deep] [--request-permissions]` | all (or every target when `-t` is omitted) | Toolchain, configuration presence, TCC permissions, staged/installed/running state, driver state. |
| `devices list` / `devices boot [--device-type "iPhone 17"]` / `devices shutdown` | — | Simulator and paired-iPhone inventory; boot or shut down simulators. |
| `build [--signing-identity X] [--verify] [--driver]` | all | Desktop: `:desktopApp:stageMacOsDevelopmentPackage`. iOS: a Debug `xcodebuild` with persistent DerivedData under `build/verification/derived-data/`. `--driver` also builds the XCUITest driver, which is otherwise rebuilt on demand whenever its sources are newer than the last build. |
| `install` | simulator, device | `simctl install` or `devicectl device install app`. |
| `launch [--fresh] [--capture-logs] [--build] [--arg A] [--env K=V]` | all | Starts the app and tracks it in `build/verification/state.json`. Desktop launches the staged `Posato.app` binary and records its window id. |
| `terminate` | all | Stops only the instance this tool started, on the simulator or device it was launched on; it does nothing when nothing is tracked. |
| `status` | all | Installed, running, pid, app path, container path, signing mode. |
| `screenshot [--name n] [--out file]` | all | Desktop window capture, `simctl io screenshot`, or a driver screenshot on the device. |
| `snapshot [--format json\|text] [--max-depth n] [query]` | all | Unified accessibility tree. `--format text` prints an outline with roles, labels, and desktop paths. |
| `find <query>` | all | Matching elements; never changes application state (on iOS it starts the app when it is not running). |
| `tap <query>` | all | Presses a button or taps an element. |
| `type <query> --input TEXT [--clear] [--submit]` | all | Types into a text field. |
| `press --key <key> [--modifiers cmd,shift]` | all | Keyboard input. Desktop: `return`, `escape`, `tab`, `delete`, `space`, arrows, digits, letters, with `--modifiers`. iOS: `return`, `delete`, `space`, `home`, `volumeUp`, `volumeDown` (device only), and `escape`/`tab` where the keyboard offers them. |
| `wait --for exists\|absent\|enabled\|disabled\|settled [query] [--timeout-seconds 10]` | all | Polls until the condition holds. |
| `run --scenario file.json` (or `-`) | all | Runs a batched scenario and reports every step with its evidence. The primary path on iOS. |
| `logs [--tail n] [--stream-seconds s]` | all | Captured application log; the simulator can also stream the unified log for a few seconds. |
| `db path` / `db query --sql "…" [--database name]` | desktop, simulator | Read-only SQLite access to the local databases. |
| `reset [--dry-run] [--yes] [--keep-install]` | all | Deletes local state (desktop, simulator) or uninstalls (device). Refuses without `--yes`; deleted files are backed up into the run directory. |
| `cleanup [--dry-run] [--purge-derived-data]` | all | Stops tracked processes; never deletes run evidence. |
| `artifacts` | — | Prints the run directory layout. |

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
| `--near-text` + `--near-role` | `near` | Prefer the match closest to the element carrying the text, with vertical distance weighted three times, so a control on the anchor's row wins over the neighbouring row; `index` then picks farther matches. Works on every target, including iOS lists whose rows expose no container. |

Example: the Remove button of the `example.com` row is
`--text Remove --role button --near-text example.com`, and the domain field is
`--role textField --near-text "Add website"`.

Desktop specifics (observed on Compose Multiplatform 1.10.3): buttons expose
their label, static text exposes its text as both label and value, text fields
have no label of their own (address them with `--role textField --near-text
"Add website"` or by `--path`), and `testTag` is not exposed. On iOS the text
field's label is its floating label ("Exact domain"), and the first text field
in tree order is the application group name, so prefer `near` over `index`. Typing goes through keyboard
events after focusing the field, so the desktop window may be anywhere but
must not be minimized.

### Snapshot node

```json
{ "role": "button", "id": null, "label": "Add website", "value": null, "enabled": true, "focused": false,
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
    { "name": "ready", "action": "waitFor", "state": "exists", "query": { "text": "Add website" }, "timeoutSeconds": 30 },
    { "name": "enter-domain", "action": "type", "query": { "role": "textField", "near": { "text": "Add website" } }, "text": "example.com", "clear": true, "submit": true },
    { "name": "row-visible", "action": "waitFor", "state": "exists", "query": { "text": "example.com", "role": "text" } },
    { "name": "after-add", "action": "screenshot" },
    { "name": "after-add", "action": "snapshot" }
  ]
}
```

iOS keyboard note: Compose drops the accessibility label of content hidden
behind the software keyboard or clipped at the screen edge, and this screen
does not pad for the keyboard, so the "Add website" button is unreachable
while the domain field has focus. When a `near` anchor is visible but the
target is not, the driver scrolls the anchor toward the centre of the screen
(up to three swipes) before giving up. Finish
text entry with `"submit": true` (the Return key triggers the field's IME
action) or `press --key return` before tapping controls below the field. The
`add-website` fixture does exactly that on every target.

Actions: `waitFor` (`state`: `exists`, `absent`, `enabled`, `disabled`,
`settled`), `tap`, `type` (`text`, `clear`, `submit`), `press` (`key`,
`modifiers`), `assert` (`state`), `screenshot`, `snapshot` (`query`,
`maxDepth`), `sleep` (`seconds`), `scrollTo` (iOS swipes until the element is
hittable; the desktop only checks that it exists), `terminate`, `relaunch`.
`launch.fresh` resets the application state on every target before the run. With
`terminateExisting: true` (the default) the scenario restarts the app; single
commands such as `tap` reuse the running app. A failed step records
`failure-<index>-screenshot.png` and `failure-<index>-snapshot.json`.

Canonical scenarios live in `fixtures/scenarios/`: `add-website.json` and
`remove-website.json` exercise the Websites section on every target.

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
3. `launch -t <target> --fresh` for a clean state, or `launch` to keep it.
4. `snapshot --format text` to learn the current labels, then `run --scenario`
   or single `tap`/`type`/`wait` commands.
5. `screenshot` and `db query` to collect evidence; `logs` when something is
   off.
6. `terminate` (and `reset --yes` if the state should not leak into the next
   run). `cleanup` stops anything left behind.

## Development

```shell
./gradlew :posato-control:test :posato-control:detekt :posato-control:ktlintCheck :posato-control:swiftFormatCheck
```

Unit tests cover the query matcher, the scenario runner, the JSON contracts,
and local configuration parsing against synthetic fixtures. End-to-end checks
run the sequences above against the real applications.
