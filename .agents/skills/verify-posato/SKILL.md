---
name: verify-posato
description: "Drive the real Posato macOS desktop app and iOS app (Simulator or connected iPhone) through the posato-control CLI to prove a change works: launch, doctor, add, edit, and remove websites and the application group, capture screenshots, accessibility snapshots, and database evidence, then clean up. Use after changing shared/, desktopApp/, iosApp/, macosHelper/, or tools/posato-control/, or whenever asked to verify Posato behavior on a real target."
---

# Verify Posato

Posato is a Kotlin Multiplatform app with one screen, `Paused items`, on two
hosts: a Compose Desktop macOS app and a Compose iOS app. There is no web UI,
no HTTP API, and no debug menu. The only scripted way to drive either app is
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

Run everything from the repository root. Build the CLI once per checkout:

```shell
./gradlew :posato-control:installDist
PC=tools/posato-control/build/install/posato-control/bin/posato-control
```

Simulator (the default proof target; no permissions or signing needed):

```shell
$PC devices boot --device-type "iPhone 17"
$PC build -t sim --driver          # app + XCUITest driver, DerivedData under build/verification/
$PC install -t sim
$PC launch -t sim --fresh          # --fresh deletes the app's data on that simulator first
$PC wait -t sim --for exists --text "Add website" --timeout-seconds 30
```

Desktop (needs Accessibility and Screen Recording for the terminal or IDE
that runs the agent; `doctor` names the host to grant):

```shell
$PC build -t desktop               # staged Posato.app; ad-hoc signed unless posato.macos.signingIdentity is set
$PC launch -t desktop --capture-logs
$PC wait -t desktop --for exists --text "Add website" --timeout-seconds 30
```

Connected iPhone (needs `posato.apple.developmentTeam` in the ignored
`local.properties`; keep the phone unlocked):

```shell
$PC build -t device --driver
$PC install -t device
$PC launch -t device
$PC wait -t device --for exists --text "Add website" --timeout-seconds 30
```

Ready means the `wait` above returns `ok: true`. Teardown is
`$PC terminate -t <target>` followed by `$PC cleanup -t <target>`.

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
`simulator.booted` and `simulator.installed` (simulator). A `warn` on
`desktop.staged` means the package is ad-hoc signed: everything except the
macOS application picker still works. `status -t <target>` confirms the
tracked process is the one you launched.

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
  minimized.
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
  `example.com` through the real UI (relaunches the app, submits with
  Return, asserts the row, captures a screenshot and a snapshot).
- `tools/posato-control/fixtures/scenarios/remove-website.json` removes that
  row from the running app and waits for it to disappear.
