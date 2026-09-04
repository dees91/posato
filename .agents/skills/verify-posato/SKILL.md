---
name: verify-posato
description: "Drive the real Posato macOS desktop app and iOS app (Simulator or connected iPhone) through the posato-control CLI to prove a change works: launch, doctor, add, edit, and remove websites and the application group, start, end early, and expire a manual session, capture screenshots, accessibility snapshots, and database evidence, then clean up. Use after changing shared/, desktopApp/, iosApp/, macosHelper/, or tools/posato-control/, or whenever asked to verify Posato behavior on a real target."
---

# Verify Posato

Posato is a Kotlin Multiplatform app with two destinations on two hosts, a
Compose Desktop macOS app and a Compose iOS app: `Session` (the screen shown
after every launch) and `Paused items` (websites and the application group).
A segmented control at the top of both shells switches between them with the
buttons `Session` and `Paused items`; the choice is not remembered across a
relaunch. There is no web UI, no HTTP API, and no debug menu. The only
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

Desktop (needs Accessibility and Screen Recording for the terminal or IDE
that runs the agent; `doctor` names the host to grant):

```shell
$PC build -t desktop               # staged Posato.app; ad-hoc signed unless posato.macos.signingIdentity is set
$PC launch -t desktop --capture-logs
$PC wait -t desktop --for exists --text "Paused items" --role button --timeout-seconds 30
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
present in every session state, while `Add website` is not on screen until
you navigate. Before any Websites or Applications recipe, switch destination:

```shell
$PC tap  -t <target> --text "Paused items" --role button
$PC wait -t <target> --for exists --text "Add website" --timeout-seconds 30
```

Every `launch` and scenario `relaunch` returns to `Session`, so repeat the
switch after each one. Teardown is `$PC terminate -t <target>` followed by
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
$PC type -t sim --role textField --near-text "Websites" --input example.com --clear --submit
$PC wait -t sim --for exists --text example.com --role text
$PC tap  -t sim --text Remove --role button --near-text example.com
$PC run  -t sim --scenario tools/posato-control/fixtures/scenarios/add-website.json
```

On the desktop, `snapshot`, `find`, `tap`, `type`, `press`, `wait`, and
`screenshot` also take `--process <name|pid>`, which addresses another process
inside the staged `Posato.app` — in practice `PosatoMacOSHelper`, which owns
the application picker. Only a process inside the staged bundle is addressable
and the tracked application must be running; anything else exits 3. The helper
runs no `NSApplication` event loop, so it has no accessibility tree: `snapshot`
and `find` on it report `PROCESS_NOT_INSPECTABLE`, while `press`, a queryless
`type`, `wait --for exists` without a query, and `screenshot` do reach it. See
[macOS application mappings](./features/macos-application-mappings.md) for the
worked recipe. `run --scenario` has no selector; scenarios stay on the tracked
application.

Prefer `run --scenario` on iOS: every single command costs an XCUITest
launch (about 5 s on the Simulator, 6-12 s on the iPhone), while a scenario
pays it once. Scenario steps: `waitFor`, `tap`, `type`, `press`, `assert`,
`screenshot`, `snapshot`, `sleep`, `scrollTo`, `terminate`, `relaunch`; a
failed step records `failure-<index>-screenshot.png` and
`failure-<index>-snapshot.json` automatically.

Platform traps that invalidate a run:

- iPhone and Simulator: content hidden behind the software keyboard loses its
  accessibility label, and the `Add website` and `Add group` buttons sit
  under the keyboard while their field has focus. Finish text entry with
  `--submit` (or `"submit": true`), which triggers the field's IME action,
  before tapping anything below the field. A rejected submit keeps the
  keyboard open; relaunch (`launch -t <target>`, no `--fresh`) to recover.
- Anchor text fields on the section headers, which stay visible and labeled
  whatever the keyboard does: the domain field is `--role textField
  --near-text "Websites"`, the group field is `--role textField --near-text
  "Applications"`. Text fields expose no label on the desktop and only their
  floating label on iOS, so never address them by label or by index.
- Desktop typing goes through keyboard events, so the window must not be
  minimized and must be frontmost. When the desktop does not react: the
  first `tap` may only activate the window, so repeat it; `press --key
  return` into a field that lost focus is silently dropped, so prefer `tap`
  on the visible submit button (`Add website`, `Add group`) where one exists;
  the session minutes field has no such button, because `Review session`
  reads the last submitted value, not the field text, so there `type`, wait
  for `settled`, then `press --key return`. Do not `tap` a field and `type`
  into it as consecutive steps,
  because the tree is rebuilt while the field takes focus and the second
  lookup fails with `ELEMENT_NOT_FOUND` (`type` clicks the field itself).
  Put `waitFor` with `"state": "settled"` between a `type` and the `tap` or
  `press` that submits it; then decide with `find` or `db query` whether the
  app ignored the input or the driver never delivered it.
- Desktop lists compose only the rows inside the window. A row below the
  fold (the website rows sit under `Add website`, and an application group
  pushes them further down) is absent from the accessibility tree, so
  `find`, `wait`, and `tap` do not see it and `scrollTo` on the desktop only
  checks existence, it does not scroll. The working way to reach such a row
  is keyboard focus traversal, which scrolls the focused control into view:
  `tap` the domain field, then `press --key tab` four times, and the
  `example.com` row with its `Edit` and `Remove` buttons is in the tree.
  `add-website-desktop.json` and `remove-website-desktop.json` do exactly
  that. Treat a missing row as this blind spot before calling it an app
  defect (`db query` settles it).
- The `Remove` and `Edit` buttons exist in the application group row and in
  every website row. Always add `--near-text <row text>`.
- Copy is exact and case-sensitive (`Add website`, `Save change`, `Cancel`,
  `No websites added`). Do not paraphrase labels.

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
driven and must not be faked through the UI: the iOS synchronizable-Keychain
adapter (`SYNC-005`), the macOS sync companion, its pipe protocol, and its
entitlements (`SYNC-006`), CloudKit adapters, provisioning, and signing. Their
proof is the Swift and Kotlin test suites, `./gradlew quality`, the packaging
verifier, and, for device-only behavior, an XCTest run on the iPhone:

```shell
xcodebuild build-for-testing -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS,name=<device name>" 2>&1 | grep --line-buffered -E "error:|warning:|BUILD"
xcodebuild test-without-building -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "platform=iOS,name=<device name>" 2>&1 | grep --line-buffered -E "Test Case|error:|TEST"
```

Split the build from the run and keep the output line-buffered, otherwise a
hung device run looks identical to a slow one. After a native packaging
change the only driver check is the launch smoke above (`Paused items`
readiness on the desktop). Synchronization gets its own feature file when
`SYNC-009` adds the `Sync with iCloud` action.

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
- `add-website-desktop.json` and `remove-website-desktop.json` are the
  desktop variants: they submit with the `Add website` button and scroll the
  row into view through Tab focus traversal before asserting or removing
  it. Use them on `desktop`; the plain fixtures are for `sim` and `device`.
- `session-start.json` (Simulator and iPhone) and `session-start-desktop.json`
  relaunch, add `example.com`, and start a 30-minute session through setup
  and review; `session-early-end.json` proves the active session survived a
  relaunch and ends it through the confirmation dialog;
  `session-expiry.json` and `session-expiry-desktop.json` start a 5-minute
  session and wait for the real expiry (`timeoutSeconds` 400), then remove
  the website. See `features/sessions.md`.
