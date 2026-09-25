---
name: verify-posato
description: "Drive the real Posato macOS desktop app and iOS app (Simulator or connected iPhone) through the posato-control CLI to prove a change works: launch, doctor, batch-add, edit, and remove websites, choose device-local applications, start, end early, and expire a manual session, capture screenshots, accessibility snapshots, and database evidence, then clean up. Use after changing shared/, desktopApp/, iosApp/, macosHelper/, or tools/posato-control/, or whenever asked to verify Posato behavior on a real target."
---

# Verify Posato

Posato is a Kotlin Multiplatform app with two destinations on two hosts, a
Compose Desktop macOS app and a Compose iOS app: `Session` (the screen shown
after every launch) and `Paused items` (websites and the application group).
iPhone and iPad portrait use bottom navigation; macOS and iPad landscape use a
sidebar, with the buttons `Session` and `Paused items`; the choice is not
remembered across a relaunch.
The nested `Websites` / `Apps` tabs include counts in their accessibility labels.
The native prototype is a frozen reference, not the application under test. There is no web UI, no HTTP API, and no debug menu. The only
scripted way to drive either app is
the repository's own CLI, `posato-control` (`tools/posato-control/README.md`),
which builds, launches, inspects, drives, and resets three targets:
`desktop`, `simulator` (alias `sim`), and `device` (a connected iPhone).
Every command prints one JSON envelope: `ok`, `result`, `artifacts`, `error`
(`code`, `message`, `hint`), with exit codes 0 ok, 1 command or driver
failed, 2 usage, 3 precondition or refused, 4 element or expectation, 5 build
or install, 6 unsupported.

Read [`features/README.md`](features/README.md) before driving; it is the
maintained map of user-facing features and the recipes that prove them.

## Unattended by default

Verification is unattended and it is not optional (`AGENTS.md`, Application
verification): never ask the maintainer to click, type, or approve anything.
**The desktop application never runs on the host Mac**: no launch, install,
reset, uninstall, or replacement of the maintainer's own Posato. Build on the
host, run in a Tart VM; the driver refuses every desktop command without
`--vm` except `build`, `doctor`, and `artifacts`, and in every recipe below
`-t desktop` means `-t desktop --vm primary` (or `peer`). An attended iOS step
needs an extraordinary reason named in the execution record or pull request
first, such as an iOS version the test iPhone does not run. When the
environment below is missing a piece, report the failing command as a
blocker; when a dialog or flow cannot be driven yet, extend `posato-control`.
Device registrations for new Macs, iPhones, and golden VMs, certificates, and
development profiles come from `tools/posato-provisioning`
([`docs/development/apple-provisioning.md`](../../../docs/development/apple-provisioning.md)),
never from the Apple Developer portal. With the one-time setup in
[`docs/development/unattended-verification.md`](../../../docs/development/unattended-verification.md):

- **macOS in Tart VMs.** `$PC build -t desktop`, then
  `$PC vm create --line primary` (and `--line peer` for Mac-to-Mac sync).
  Every desktop command takes `--vm primary|peer` and runs inside the guest;
  evidence lands in `build/verification/runs/<run>/guest/`. System dialogs
  are answered with `$PC vm prompt <kind> --line <line>`:
  `admin` (SecurityAgent at session start and Resume restrictions),
  `background` (helper approval in Login Items), `toggle --row <text>` (privacy
  panes), `picker-bypass` (macOS 26 after screen captures), `gatekeeper`,
  `account-password`, `mac-password`, and `device-passcode` (iCloud
  recovery). Run the prompt right after the step that raises it; a scenario
  that waits on the confirmed state can run in the background while the
  prompt command answers. Finish with `$PC vm destroy --line <line>`; a
  broken guest is deleted, never repaired.
- **The test iPhone.** `-t device` as before. Screen Time consent is
  `fixtures/scenarios/screen-time-consent.json` in one run; the application
  picker is in the app's own accessibility tree.

## Launch

Run everything from the repository root. Provision and build the CLI once per
checkout; `local.properties` is ignored, so a new worktree starts without it
and every device command fails until the whole file is copied from the main
checkout:

```shell
cp ../posato/local.properties .   # whole file, in a fresh worktree
./gradlew :posato-control:installDist
PC=tools/posato-control/build/install/posato-control/bin/posato-control
```

Simulator (the default proof target; no permissions or signing needed):

```shell
$PC devices boot --device-type "iPhone 17"
$PC build -t sim --driver          # app + XCUITest driver, DerivedData under build/verification/
$PC install -t sim
$PC launch -t sim --fresh          # --fresh deletes the app's data on that simulator first
$PC wait -t sim --for exists --text "Paused items" --role button --timeout-seconds 30
```

Desktop, always in a Tart VM (the golden VM's `tart-guest-agent` holds
Accessibility and Screen Recording):

```shell
$PC build -t desktop               # on the host: staged Posato.app, signed with posato.macos.signingIdentity
$PC vm create --line primary       # disposable clone with the package; `vm sync` after a later build
$PC launch -t desktop --vm primary --capture-logs
$PC wait -t desktop --vm primary --for exists --text "Paused items" --role button --timeout-seconds 30
```

Connected iPhone (needs `posato.apple.developmentTeam` in the ignored
`local.properties`; keep the phone unlocked):

```shell
$PC build -t device --driver
$PC install -t device
$PC launch -t device
$PC wait -t device --for exists --text "Paused items" --role button --timeout-seconds 30
```

Ready means the `wait` above returns `ok: true`; the `Paused items` button is
present in every session state without the software keyboard. It is hidden
while the keyboard is open; `Done` clears focus and restores navigation. Before any Websites or Applications recipe, switch destination:

```shell
$PC tap  -t <target> --text "Paused items" --role button
$PC wait -t <target> --for exists --text "Search" --timeout-seconds 30
```

Every `launch` and scenario `relaunch` returns to `Session`, so repeat the
switch after each one. A fresh database shows the first-install flow instead:
run `first-install-skip.json` after every `--fresh` launch or `reset` before
any older recipe (see [First install](./features/onboarding.md)), then wait
for `Paused items`. Teardown is `$PC terminate -t <target>` followed by
`$PC cleanup -t <target>`.

Isolation: one instance per target. The tool tracks what it launched in
`build/verification/state.json` and refuses to stop anything else, so never
drive a Posato instance that was started outside this run. The desktop app
shares the developer's real local databases under `~/Library/Application
Support/Posato/`; do not use `--fresh` or `reset --yes` on the desktop
unless an empty state is required, and if you do, restore the files from the
run's `backup/desktop/` directory afterwards.

## Doctor

Before driving, and whenever something looks off:

```shell
$PC doctor -t <target> | jq '{ok: .result.ok, failing: [.result.checks[] | select(.ok | not) | {id, severity, detail, hint}]}'
```

Require `ok: true` with no `error`-severity check. Blocking checks:
`desktop.accessibility` and `desktop.screenRecording` (desktop driving is
impossible without them), `device.team` and `device.connected` (device),
`simulator.booted` and `simulator.installed` (simulator). `status -t <target>`
confirms the tracked process is the one you launched.

`doctor` is also the one-time provisioning gate. Every check carries a `state`
and one remedy:

- `ok` — satisfied.
- `missing` — not satisfied; fix it with the check's `hint` before driving.
- `unknown` — not observable from the host, always `warn`, never blocking. The
  hint names the one command that reveals it. `desktop.helperBackground` and
  `device.screenTime` are always `unknown`, because only the application can
  read the helper's background approval and Screen Time authorization.

`desktop.helperBundle`, `desktop.proxyDaemon`, and `desktop.syncCompanion`
report the three nested components the staged package must carry: the macOS
helper executable, the proxy-settings daemon with its launchd property list,
and the synchronization companion. A `warn` on any of them means the package
is incomplete rather than unsigned; rerun `$PC build -t desktop --verify` and
read the packaging verification failure.

A `warn` on `desktop.staged` means the package is ad-hoc signed: everything
except the macOS application picker still works. Two provisioning conditions
gate that picker, `desktop.signingIdentity` and `desktop.syncProfile`; both are
`info` while the checkout is ad-hoc, so an ad-hoc checkout is still `ok: true`.
Note that `./gradlew quality` restages an ad-hoc package, so rerun
`$PC build -t desktop` after it before any application-picker recipe.

## Drive

Address elements by what a user sees. Query options (shared by `find`,
`tap`, `type`, `wait`, and scenario `query` objects): `--text` exact label,
value, or placeholder; `--text-contains`; `--role` `button|textField|text|group|window|progress|other`;
`--index`; `--near-text` (the match closest to that text; the only reliable
way to pick a row's button or the domain field); `--within-text` with
`--within-role` (desktop rows only); `--id` (iOS accessibility identifier;
none exist yet); `--path` (desktop accessibility path from a snapshot).

```shell
$PC snapshot -t sim --format text --human       # learn the current labels first
$PC type -t sim --role textField --input example.com --clear --submit
$PC tap -t sim --text Done --role button
$PC wait -t sim --for exists --text example.com --role text
$PC tap -t sim --text "Actions for example.com" --role button
$PC tap -t sim --text Remove --role button
$PC run  -t sim --scenario tools/posato-control/fixtures/scenarios/add-website.json
```

On the desktop, `snapshot`, `find`, `tap`, `type`, `press`, `wait`, and
`screenshot` also take `--process <name|pid>`, which addresses another process
inside the staged `Posato.app` — in practice `PosatoMacOSHelper`, which owns
the application picker and, since `MACOS-004`, also the loopback proxy and the
browser-domain session. Only a process inside the staged bundle is addressable
and the tracked application must be running; anything else exits 3, including a
name that two processes inside the bundle share — pass a pid instead. The helper
never runs an `NSApplication` event loop, in either role, so it has no
accessibility tree at any time: `snapshot` and `find` on it report
`PROCESS_NOT_INSPECTABLE`, while `press`, a queryless `type`, a queryless
`wait --for exists` or `--for absent`, and `screenshot` do reach it. See
[macOS application mappings](./features/macos-application-mappings.md) for the
worked recipe. `run --scenario` has no selector; scenarios stay on the tracked
application.

Prefer `run --scenario` on iOS: every single command costs an XCUITest
launch (about 5 s on the Simulator, 6-12 s on the iPhone), while a scenario
pays it once. Scenario steps: `waitFor`, `tap`, `type`, `press`, `assert`,
`screenshot`, `snapshot`, `sleep`, `scrollTo`, `orient`, `terminate`,
`relaunch`; a failed step records `failure-<index>-screenshot.png` and
`failure-<index>-snapshot.json` automatically.

Rotate an iOS target with `orient --to portrait|portraitUpsideDown|landscapeLeft|landscapeRight`
or the scenario step `{"action":"orient","orientation":"landscapeLeft"}`. It
rotates through XCUITest, passing through the other aspect when the target
shares the current one, and waits until the window has the new aspect, so it
never needs the Simulator window in front. An unsupported orientation, such as
upside down on the iPhone, fails. The orientation outlives the run: finish
rotation work, including after a failed run, with `orient --to portrait`. Do not rotate with synthesized
keystrokes or clicks: the Simulator window may sit under the maintainer's
windows, and the events land there instead. Simulator screenshots of a
landscape device stay in the portrait pixel orientation; rotate them only for
viewing.

Platform traps that invalidate a run:

- Batch entry keeps focus after Add or Return so another domain can be entered.
  Tap `Done` to hide the keyboard before using bottom navigation or scrolling
  the list. Search / Back to adding and category changes also clear focus;
  rejected input no longer requires a relaunch.
- Websites exposes one text field: the add draft, search, or active editor.
  Use `--role textField` after confirming the relevant surface. Labels with
  counts use `--text-contains Websites` or `--text-contains Apps` plus
  `--role button`; do not hardcode a count.
- Row menus open through `Actions for <domain or app name>`, then `Edit`
  or `Remove`. These actions are not permanently visible in each row.
- Desktop taps use accessibility actions without explicitly activating the
  application. Typing, key presses, and `scrollTo` activate the tracked window
  and refuse if it cannot become frontmost: per-process key delivery alone
  did not populate the background Compose field in native verification.
  The helper's non-inspectable picker also requires foreground delivery.
  Keep the Mac unlocked and avoid competing foreground automation during input.
  Read-only inspection does not activate the window.
- Lazy lists expose only composed rows. Use a scenario `scrollTo` step before
  an offscreen-row action; `find` and `wait` do not scroll. Desktop scrolling
  sends real pointer/wheel events inside the largest visible scroll area,
  reverses at the end, and stops at its timeout or attempt bound. Use
  `query.within: {"text":"Saved websites","role":"group"}` for website rows;
  Compose's outer iOS scroll wrapper can span the whole screen, so the named
  list provides the actual gesture viewport. Compact Session (iPhone 13 mini)
  has no named list: after expanding iCloud, unscoped `scrollTo` for the
  action; the iOS driver swipes the screen when that wrapper fills the window
  and reports reached only when the element's centre is on screen.
  Static row text need not be tappable to be visibly reached. No fixed
  coordinates, Tab-count workaround, or product test hook is needed.
- Time setup has presets `25 min`, `45 min`, `60 min` and two wheels.
  The explicit buttons are `Increase Hours`, `Decrease Hours`,
  `Increase Minutes`, and `Decrease Minutes`. There is no minutes text field.
- Choosing apps no longer requires manually creating a group. A successful
  nonempty native selection creates `Applications` only if the group is absent;
  existing names survive. Partial metadata-save failure retains the selection
  and offers `Enable selected apps`. Do not inject a group or native tokens
  to stand in for this user path.

## Evidence

Every command that produces artifacts writes them under
`build/verification/runs/<run-id>/` (`screenshots/`, `snapshots/`,
`driver/<target>-<n>/` for iOS results, the app log, and `backup/` for
reset) and lists them in the envelope's `artifacts`; `build/verification/latest`
points at the newest run and `$PC artifacts` prints the layout. Everything
under `build/` is ignored by Git; never copy screenshots, logs, or device
identifiers into tracked files or task records (they are categorical only).

Proof standard for a feature:

1. Exercise the real user path (type, tap, submit), never the database or a
   test hook.
2. Capture the action and the resulting state: a `snapshot` or `wait` that
   names the new element, plus a `screenshot` whose content shows `Paused
   items` and the changed row.
3. Verify the side effect: `$PC db query -t <desktop|sim> --sql "select canonical_domain from exact_domain_policy"`
   (websites) or `"select canonical_name from application_policy"` (group).
   The device database is not readable; use `find -t device --text <value>`
   after a relaunch instead.
4. Restore the state you changed (remove the row you added) and confirm the
   restoration the same way.

Report an unreachable path with the exact command and the failing check
(`TCC_ACCESSIBILITY_DENIED`, `NO_CONNECTED_DEVICE`, `DEVELOPMENT_TEAM_MISSING`).
Do not report a path as verified through a different target.

## Out of scope

The driver proves user paths. Native work with no user action yet is not
driven and must not be faked through the UI: provisioning and signing, and the
internals behind the synchronization adapters. Since `SYNC-009` the iOS
synchronizable-Keychain adapter (`SYNC-005`), the macOS sync companion with its
pipe protocol and entitlements (`SYNC-006`), and the CloudKit adapters
(`SYNC-008`) are reachable only through the one consent action in
[Sync with iCloud](./features/sync.md); never drive them another way. Session start now applies enforcement (`SESSION-002`): the macOS
browser-domain denial (`MACOS-004`), the macOS application restriction
(`MACOS-005`), and the Posato-owned iOS restrictions (`IOS-001` plus the
`IOS-002` suspended-expiry schedule) are drivable through the session recipes
in `features/sessions.md`, with the Swift and Kotlin test suites,
`./gradlew quality`, the packaging verifier, and, for device-only behavior, an
XCTest run on the iPhone as the remaining proof:

```shell
xcodebuild build-for-testing -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS,name=<device name>" 2>&1 | grep --line-buffered -E "error:|warning:|BUILD"
xcodebuild test-without-building -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS,name=<device name>" 2>&1 | grep --line-buffered -E "Test Case|error:|TEST"
```

Split the build from the run and keep the output line-buffered, otherwise a
hung device run looks identical to a slow one. After a native packaging
change the only driver check is the launch smoke above (`Paused items`
readiness on the desktop). The `Sync with iCloud` action has its own
feature file, and it drives a real iCloud account, so read
[Sync with iCloud](./features/sync.md) before pressing it.

The macOS browser matrix has its own proof: the environment-gated JVM harness
`MacOsBrowserDomainPhysicalHarnessTest` in `desktopApp`, which enables the
helper, applies a synthetic exact domain, holds enforcement active while the
maintainer performs the browser rows, and clears afterwards. Its gate is not a
Gradle input, so the task needs `--rerun`:

```shell
POSATO_MACOS_004_PHYSICAL=1 \
  ./gradlew :desktopApp:test --tests '*MacOsBrowserDomainPhysicalHarnessTest*' --rerun
```

`POSATO_MACOS_004_HELPER` overrides the installed helper path and
`POSATO_MACOS_SIGNING_IDENTITY` the identity that signs the harness parent
process; both fall back to the harness default and `local.properties`. The run
is steered by marker files under `build/verification/macos-004/`
(`ENABLE_APPROVED`, `APPLY_GO`, `ROWS_DONE`, `ABORT`), so it needs the
maintainer at the Mac and is not an unattended check; like every desktop run it
never runs on the host Mac.

A harness run leaves state that `reset -t desktop` cannot clear, because that
command only deletes the local databases: the root-owned ownership record at
`/Library/Application Support/Posato/ProxySettings/ownership-v1.plist` and,
after companion work, the workspace key in the synchronizable Keychain, which
is an iCloud item rather than a local one. `doctor` does
not report either. Treat a desktop run that follows a harness or companion run
as carrying that state; do not delete the root-owned path from a recipe.

## Cleanup

```shell
$PC terminate -t <target>           # stops only the instance this run launched
$PC cleanup -t <target>             # stops log tailers and console attachments; never deletes runs
$PC cleanup -t <target> --purge-derived-data   # only when a clean rebuild is wanted
```

Cleanup never removes evidence: `build/verification/runs/<run-id>/` survives
and is the location to cite. If `reset --yes` or `launch --fresh` was used
on the desktop, copy the databases back from that run's `backup/desktop/`
so the developer's local data is unchanged.

## Helpers

- `tools/posato-control/build/install/posato-control/bin/posato-control` is the
  only helper; `--help` on any command lists its options and
  `tools/posato-control/README.md` documents the JSON contracts.
- `tools/posato-control/fixtures/scenarios/add-website.json` adds
  `example.com` through the real UI (relaunches the app, switches to `Paused
  items`, submits with Return, asserts the row, captures a screenshot and a
  snapshot).
- `tools/posato-control/fixtures/scenarios/remove-website.json` switches to
  `Paused items` in the running app, removes that row, and waits for it to
  disappear.
- `add-website-desktop.json` and `remove-website-desktop.json` use the same
  user controls on Mac, with real `scrollTo` before row interaction.
- `website-edit.json` checks the software-keyboard editor, draft retention,
  Cancel, saved edits after restart, and removal after restart. Reserve its
  two synthetic domains and use a short list; see the Websites recipe.
- `website-batch-list.json` adds 50 synthetic domains, a duplicate, and an
  invalid entry; proves scrolling in both directions, filtering, and draft
  retention across destination changes. Start with no `design-proof-*.example`
  rows. Run `website-batch-list-cleanup.json` afterwards and check the database.
  A failed run may need cleanup limited to the rows that actually saved.
- `session-start.json` and `session-start-desktop.json` add `example.com`
  and start 25 minutes through setup/review. `session-early-end.json` relaunches
  before ending early. The expiry fixtures select 25 minutes, decrease minutes
  twenty times, wait for a real five-minute expiry, and remove `example.com`.
  Desktop session runs split: `session-start-action-required-desktop.json` proves the
  action-required path when the administrator prompt goes unconfirmed. The full desktop
  start and expiry with enforcement run unattended in a Tart VM, where
  `vm prompt admin` confirms the SecurityAgent dialog. See `features/sessions.md`.
- `onboarding-sync-desktop.json` and `onboarding-helper-finish-desktop.json` take a fresh
  desktop install through iCloud consent and helper approval; run `vm prompt background`
  between them in a VM.
- `screen-time-consent.json` grants Screen Time access on the test iPhone through the
  system sheets and the passcode keypad, reading the passcode from the Keychain item in
  `local.properties`.
- `onboarding-sync-consent-ios.json` takes a fresh iPhone install through iCloud consent,
  Screen Time consent (the same steps in one run), and the remaining onboarding;
  `choose-app-ios.json` picks Calculator in the picker; `observe-blocking-ios.json` and
  `observe-unblocked-ios.json` open Calculator and `http://example.com` and assert the
  Screen Time shield and Safari's "Website Not Allowed" page, or their absence; on the
  desktop `observe` does the same in a VM (`features/sessions.md`, Observe blocking).
