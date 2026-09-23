# First install

A fresh install opens a six-step first-run flow before Session and Paused
items: purpose, privacy, Sync with iCloud, this-device permission, first
website, and a summary read back from the services. Purpose and privacy
cannot be skipped. Every service step offers a defer action that leaves the
device local-only or unpermitted. Finishing the summary persists completion;
every later launch opens Session directly. An install upgraded by a build
that seeds completion opens on Session without the flow.

## Sub-features

- `first-install-full` completes purpose, privacy, declined iCloud,
  unavailable permission, one website, and the service-read summary.
- `first-install-skip` dismisses the flow without adding anything, so older
  recipes keep passing after a fresh launch or reset.
- `first-install-upgrade` opens an upgraded database with existing product
  state directly on Session.
- `first-install-degraded` reports a retryable or action-required iCloud
  outcome without writing a bootstrap row.

- `second-install-join` joins an existing workspace in both device orders;
  delayed keys expose Continue primary and Check again secondary. See the
  [sync recipe](./sync.md#second-install-join-and-delayed-key) for setup,
  cleanup, foreground opportunities, and evidence limits.

## How to get to it (user POV)

- Fresh install, reinstall, `launch --fresh`, or `reset` shows the flow; the
  navigation chrome is absent until the summary is finished.
- Make some space opens privacy, then Continue opens iCloud. Not now
  defers an unconfigured service or leaves the website list empty. Configured
  services offer Continue. Go to Session finishes and persists completion.
- On iPhone the permission step asks for Screen Time access through the real
  system request and shows the read-back answer. On Mac Enable on this Mac
  enables the helper; approval required opens System Settings with Check
  again. After Not now, expand the Session screen's This Mac row to reach the
  same route later (see [Sessions](./sessions.md)). Removing a workspace never
  returns to the flow.

## Driving it with posato-control

Preconditions:

- Run `first-install-skip.json` after every `--fresh` launch or `reset`
  before any older recipe; without it the `Paused items` readiness wait
  cannot pass on a fresh database.
- Reserve `example.com` for the full fixture; start with no bootstrap row
  (`db query -t <desktop|sim> --sql "select count(*) from sync_bootstrap_state"`
  returns 0) and no `PosatoMacOSHelper` process (`pgrep -f PosatoMacOSHelper`
  is empty) before the permission step on Mac.

- **Full flow:** `$PC run -t sim --scenario tools/posato-control/fixtures/scenarios/first-install.json`.
  It declines iCloud, takes the unavailable permission answer, adds
  `example.com`, waits for `1 website saved` on the summary, captures a screenshot
  and a snapshot, and lands on Session. Confirm the side effects with
  `$PC db query -t sim --sql "select canonical_domain from exact_domain_policy"`
  (one row) and zero `sync_bootstrap_state` rows.
- **Continuous entry:** on the website step, submit two websites one at a
  time with Return and no tap in between. After each submission the field
  stays focused and the keyboard stays up; the caption below the field reads
  `1 website saved`, then `2 websites saved`, while the field feedback
  describes only the last submission. A duplicate or invalid entry leaves the
  total unchanged, and invalid text stays in the field. **Continue** is
  visible above the keyboard without scrolling on iPhone, iPad portrait, and
  iPad landscape, and in iPad landscape the whole field and the total stay
  visible above it. The total is a live region, so on iOS it is exposed as a
  group: select it by text without `role`. The iOS snapshot reports the
  field's `focused` state; the desktop bridge does not, so on Mac prove focus
  with a screenshot showing the caret and focus border.
- **Expanded actions:** on iPad and Mac, the iCloud and permission steps
  show their actions in one wrapping row in primary, secondary, quiet order.
  Compact pages keep the full-width primary action above the others.
- **Late summary refresh:** during an attended second-install join, leave
  the joining device on Summary while the peer adds or removes a reserved
  fixture website. After a completed exchange, capture the updated saved
  count without navigating away. Repeat for removal. This needs two linked
  devices; the local-only full fixture does not prove remote arrival.
  Gated common tests cover an apply during the initial read, a failed read
  retaining the prior count, and cancellation when Summary closes.
- **Skip prelude:** `$PC run -t <target> --scenario tools/posato-control/fixtures/scenarios/first-install-skip.json`
  after a fresh launch or reset. It writes nothing except the completion row;
  confirm with one `local_setup_state` row and an immediate Session landing
  on the next launch.
- **Upgrade:** back up the desktop databases, `reset -t desktop`, restore a
  database that already holds a domain, policy, bootstrap row, or session,
  and launch. The app opens on Session with no flow. Restore the backup
  afterwards.
- **Degraded iCloud:** on a Simulator with no signed-in account, launch fresh,
  reach the iCloud step, and press Sync with iCloud. Expect a retryable or
  action-required status, never success, and still zero bootstrap rows.
- **Mac permission:** during the flow before Enable on this Mac, `pgrep -f
  PosatoMacOSHelper` is empty. Press Enable on this Mac on an approved Mac
  and expect the enabling caption immediately, then the enabled state. **Not
  now** stays usable while that call runs and does not cancel it; finish to
  Session and expect the shared This Mac row to show the late result without
  starting enforcement. The approval-required branch is unit-tested; only
  drive it attended, then use Check again after allowing Posato. A lost
  Enable reply shows that the request did not finish; Check again reconciles
  it. Registered but unlaunchable is not treated as enabled and does not
  offer Login Items removal from the app. After a Background Items database
  reset, Enable on this Mac is the Check result, not unavailable. An Enable that
  attempts registration and leaves the service unregistered reports that setup
  could not be completed instead of offering the same Enable again.
- **Accessibility:** capture large-text, keyboard, and VoiceOver passes for
  at least one step on each platform with a screenshot and a snapshot.

## Gotchas

- The flow has no navigation chrome; do not wait for `Paused items` until
  Go to Session is pressed.
- A joining device whose workspace already holds a website shows only
  Continue at the website step, not Not now; the summary then counts the
  synced website.
- The first Sync with iCloud press gives consent for one setup attempt.
  A fresh join waiting for a key may then continue on Check again or foreground
  without creating resources. Restart forgets waiting and requires consent
  again. For second-install verification, deliberately retain the established
  peer's workspace; only creator/from-empty recipes clear it first.
- Typing the website needs the desktop window frontmost; keep the Mac
  unlocked and avoid competing foreground automation during input.
- The desktop shares the developer's real databases; restore them from the
  run's `backup/desktop/` directory after any `--fresh` or `reset`.
