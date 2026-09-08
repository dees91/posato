# Sync with iCloud

The Session screen carries one explicit **Sync with iCloud** control at the
bottom, under the "Saved on this device." caption. Nothing reaches iCloud
before a person presses it. One press runs one bootstrap attempt against the
person's own iCloud account and the status line above the button reports only
the outcome that attempt returned: linked, waiting for the key from the other
device, needs attention, or didn’t finish. There is no synchronization time, no
device list, and no in-app way to unlink; removal belongs to `SYNC-010`.

## Sub-features

- `sync-consent-gate` shows the description copy and touches neither CloudKit
  nor the synchronizable Keychain until the button is pressed.
- `sync-establish` turns the first consented press on an empty account into one
  workspace and reports linked.
- `sync-join` lets the second device adopt the workspace the first established,
  in either device order, without a second workspace.
- `sync-waiting-key` reports the truthful waiting state when the anchor exists
  but the key item has not arrived yet, and adopts it on a later press.
- `sync-degrade` reports a truthful non-linked outcome with no partial
  establishment when the account is missing, restricted, or the macOS package
  is not verifiable.
- `sync-adopt-relaunch` shows the linked status after a relaunch without a
  second press.
- `sync-single-attempt` disables the button for the duration of one attempt, so
  a double press cannot start two.

## How to get to it (user POV)

- Launch either app; the Session destination opens by default and the control
  is the last element on it. Scroll to the bottom when the window is short.
- Read the status line, then press **Sync with iCloud**.
- Leave for Paused items and come back: an attempt started before leaving keeps
  running and its outcome is still shown on return.
- Relaunch: a linked device states it is linked without another press.

## Driving it with posato-control

Preconditions:

- The maintainer's own iCloud account is signed in on every target used, and
  the same account on both when a two-device row is driven. The account's data
  is real; treat every row as mutating it.
- Desktop needs a signed staged package: `posato.macos.signingIdentity` and
  `posato.macos.syncProvisioningProfile` in the ignored `local.properties`, and
  `doctor -t desktop` reporting `desktop.syncCompanion`, `desktop.signingIdentity`,
  and `desktop.syncProfile` as `ok`. An ad-hoc package proves `sync-degrade`
  only.
- Device needs `posato.apple.developmentTeam`, a connected unlocked iPhone, and
  a Debug iphoneos build.
- A from-empty row needs an empty account first; see the reset bullet below.

- **Read the consent state:** `$PC snapshot -t <target> --format text --human`
  and expect `Sync with iCloud links this device to your private iCloud
  workspace.` above the button. Prove that nothing ran before the press with
  `$PC db query -t <desktop|sim> --sql "select count(*) from sync_bootstrap_state"`,
  which must be `0`.
- **Press once:** `$PC tap -t <target> --text "Sync with iCloud" --role button`,
  or the same step inside a scenario on iOS, followed by a screenshot step.
- **Read the outcome:** `$PC wait -t <target> --for exists --role text
  --text "This device is linked to your iCloud workspace." --timeout-seconds 60`.
  The other four outcomes are `Waiting for the workspace key from your other
  device.`, `Sync with iCloud needs attention before it can continue.`,
  `Sync with iCloud didn’t finish.` (typographic apostrophe), and, while an
  attempt runs, `Sync with iCloud is running.`.
- **Verify the side effect:** `$PC db query -t <desktop|sim> --sql "select
  count(*) from sync_bootstrap_state"` must be `1` after a linked outcome, and
  stay `1` after a second press. To tell a waiting device from a linked one
  without reading any identifier, select null-ness only: `select
  candidate_workspace_id is null, established_workspace_id is null from
  sync_bootstrap_state`. A loser mid-race is `0|1` and becomes `1|0` once it
  adopts. Never select the identifier or binding columns themselves; they must
  not reach evidence.
- **Adopt after relaunch:** relaunch without `--fresh`, then wait for the linked
  status again. The count must still be `1`.
- **Second device joins:** run the press on the second target only after the
  first reported linked. Expect linked, or the waiting status followed by linked
  on a later press once iCloud Keychain has delivered the item.
- **Simultaneous opt-in:** aim both presses at one absolute wall-clock second,
  not at a delay from each command's own start. A device scenario spends about
  7 s launching XCUITest before its first step, so start it at `T` minus 9 s
  with a 2 s `sleep` step before the tap, and have the desktop tap wait for `T`
  itself; that lands the two presses within a few tenths of a second. Expect one
  device linked and the other reporting the waiting status, then press the
  waiting device again to adopt.
- **Device read-back:** the device database is not readable. Relaunch and use
  `$PC find -t device --text "This device is linked to your iCloud workspace."
  --role text` instead.
- **Confirm one workspace:** this needs the maintainer in the CloudKit Console
  (Private Database, Development environment): exactly one `PosatoSyncV1` zone
  next to the system `_defaultZone`, holding one `PosatoWorkspaceV1` record.
  The driver cannot see the account.
- **Reset to empty for a from-empty rerun:** ask the maintainer to delete the
  `PosatoSyncV1` zone in the console, clear the local state with `sqlite3
  "$HOME/Library/Application Support/Posato/posato-policy.db" "delete from
  sync_bootstrap_state"` while the desktop app is quit, and delete the app from
  the iPhone. Do not use `reset -t desktop` for this: it clears the whole local
  database rather than this one table.

## Gotchas

- `./gradlew quality` restages an ad-hoc desktop package. Rerun `$PC build -t
  desktop` with the signing identity configured before any row other than
  `sync-degrade`, or the press degrades instead of establishing.
- Every consented press writes to a real iCloud account. After the first
  establishing row the account is no longer pristine, and every later
  from-empty row costs the maintainer a console deletion, because the app has
  no unlink path until `SYNC-010`.
- Deleting the app from the iPhone and clearing `sync_bootstrap_state` on the
  Mac do not remove the workspace key from the synchronizable Keychain. A
  leftover item is inert, because the next workspace mints a fresh identifier
  and the item is addressed by that identifier, but it stays on the account.
- Deleting the zone alone is not a reset. A device that keeps its local state
  still reports linked against a workspace that no longer exists in the cloud.
- The status line is the only truthful source. A screenshot of the button
  proves nothing about the outcome; capture the caption text with it.
- One press equals one attempt and the button is disabled while it runs. The
  driver is too slow to observe `Sync with iCloud is running.` reliably; unit
  tests own that state, so do not report its absence as a defect.
- The control is absent, rather than failing, when the graph provides no
  bootstrap. Treat a missing button as a composition problem, not a degraded
  outcome.
- The simulator has no iCloud account by default, so it proves the truthful
  non-linked path and nothing about establishing or joining.
- A `wait` for the linked status times out on the losing side of a real race.
  That is the correct outcome, not a driver failure: read the status line before
  calling the row failed.
- `waiting-for-workspace-key` is hard to catch outside a race. When one device
  presses well before the other, iCloud Keychain usually delivers the item
  first and the second device reports linked immediately.
- The count of surviving Keychain accounts and the absence of a losing
  candidate's item are not observable here. Synchronizable items are invisible
  to both `security find-generic-password` and the driver; the coordinator
  tests own that half of the convergence claim.
