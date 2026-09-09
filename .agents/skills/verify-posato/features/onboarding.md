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

## How to get to it (user POV)

- Fresh install, reinstall, `launch --fresh`, or `reset` shows the flow; the
  navigation chrome is absent until the summary is finished.
- Continue moves through purpose and privacy. Not now leaves iCloud
  local-only. Later leaves the permission unrequested. Skip leaves the
  website list empty. Open Session finishes and persists completion.
- On iPhone the permission step asks for Screen Time access through the real
  system request and shows the read-back answer. On Mac Enable on this Mac
  enables the helper; approval required opens System Settings with Check
  again. Removing a workspace never returns to the flow.

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
  `example.com`, waits for `1 website.` on the summary, captures a screenshot
  and a snapshot, and lands on Session. Confirm the side effects with
  `$PC db query -t sim --sql "select canonical_domain from exact_domain_policy"`
  (one row) and zero `sync_bootstrap_state` rows.
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
  and expect the enabled state. The approval-required branch is unit-tested;
  only drive it attended, then use Check again after allowing Posato.
- **Accessibility:** capture large-text, keyboard, and VoiceOver passes for
  at least one step on each platform with a screenshot and a snapshot.

## Gotchas

- The flow has no navigation chrome; do not wait for `Paused items` until
  Open Session is pressed.
- The iCloud press runs exactly one bootstrap attempt. Do not press Sync with
  iCloud on a maintainer account without emptying the workspace first with
  Remove workspace.
- Typing the website needs the desktop window frontmost; keep the Mac
  unlocked and avoid competing foreground automation during input.
- The desktop shares the developer's real databases; restore them from the
  run's `backup/desktop/` directory after any `--fresh` or `reset`.
