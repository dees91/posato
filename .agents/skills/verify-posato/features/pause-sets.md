# Pause sets

Pause sets replaces Paused items. Its root is a list of every set on this
device; a set's row opens that set's Websites and Apps. The first set shows as
"My set" until renamed. Behavior: `DESIGN.md` "Release 1.3 pause sets" and
`docs/product/pause-sets-decisions.md`.

## Recipes

- `pause-sets-desktop.json` (Mac, after onboarding with the helper and Set
  up this Mac; iCloud is not needed): New set with a website, Make default,
  Rename, a schedule saved off with the default set preselected, the set
  choice in Session setup and `Set: <name>` in Review, Delete with **Move to
  My set and delete**, the limit of ten with **New set** disabled, and the
  list after a relaunch. A schedule saved on can start while the run
  continues; the recipe saves it off for that reason.
- `pause-sets-notice-desktop.json` (Mac, right after `flow icloud link`,
  before any scenario or `flow set` opens Pause sets): the one-time "Update
  Posato on your other devices" notice on the first visit and no notice after
  a relaunch. The first linked visit uses up the notice whatever opened the
  screen. Run it only when iCloud is in scope (`SKILL.md`, "When to link
  iCloud").
- `pause-sets-device.json` (test iPhone): "Apps on this iPhone" wording, a new
  set with a website, its choice in Session setup and Review, and its deletion.

- Export and import (`TARGETS-007`, every target but the Simulator): `flow
  export --code [--out F]` returns the code and keeps the file in the run's
  evidence; `flow import --file F --code C --mode combine|separate|replace
  [--turn-on]` returns the review's line and the result, or `refused` with
  its reason. On a Mac push the file first (`vm push --to name.posato`) and
  pass `/Users/posato/name.posato`; a panel left open by a failed run stays
  open (presses bypass its modality), so close it with `CancelButton` before
  the next run. Proof for a coded file: it opens with the code outside
  Posato too (PBKDF2, HKDF, AES-GCM as in the record). For the refusal,
  start a pause on a set the file lacks with `flow session`, then replace.
  A file sealed with another round count does not open by design.

- Website windows (`TARGETS-013`, desktop, Linux, Windows, and Android; iOS
  later): `flow set --name Work --website window.example --website
  blocked.example --window window.example=30/5` creates the set and sets the rule through the row's
  **Open briefly…** dialog, waiting for the row caption "Opens every 30 min
  for 5 min"; `--window window.example=off` presses **Don't open**. Then
  `flow session --set Work` and `run --scenario
  fixtures/scenarios/website-window-session-desktop.json`: Session lists
  `window.example` with "Opens at HH:MM" or "Open until HH:MM" (the row's
  accessible label is "window.example, opens at 10:30") and no row for the
  unruled `blocked.example`. Clearing the rule during the session removes the
  row within a second. The times follow the guest's clock (`vm exec date`).
- Window enforcement on the Mac (hidden window, because quitting Posato ends
  all Mac blocking): start the session, `close-window`, then before the
  boundary run `$PC window-probe -t desktop --vm primary --website
  http://window.example/ --website http://blocked.example/ --boundary 10:30
  --boundary 10:35`. It loads each URL through the guest's system proxy with
  a cache-busting `probe` query at b-90, -60, -45, -30, -15 and -3 s and every
  15 s to b+120 s, on the guest's clock, and reports each probe
  (`paused`/`loaded`, `opensAtPage` for "This site opens at") and each URL's
  bounded transition interval. A row passes when every probe before the
  opening is `paused`, one is `loaded` by b+60 s, the close is `paused` by
  end+60 s, and the unruled control stays `paused`. Start it a minute before
  b-90 s; it runs about 3.5 minutes per boundary. The page itself: `vm exec
  --script "open -a Safari https://window.example/"`, `vm prompt
  automation-allow` once, then `vm wait-text --text "This site opens at"`.
- Window enforcement on the iPhone: Safari probes are `probeURL` steps
  (`url`, `text`) after `sleepUntil` steps (`atEpochMs` on the device clock)
  in one scenario with `launch.skip` and `continueOnFailure`, so one driver
  launch covers the series; each step reports `observed` (`blocked`, `open`,
  `none`) and `observedAtEpochMs`. Each boundary is its own Device Activity
  named `app.posato.window.<epoch second>`; `monitor -t device` lists them
  after the app's last evaluation. A Safari load that starts within a second
  of a shield change can hang and report `none`, which is a missing
  observation, not a pass: give each `probeURL` a short `timeoutSeconds`
  (8) and `retries: 1`, keep every sample on its own `sleepUntil` instant,
  and probe the unruled control on its own instants away from the boundary
  and the b+20 s fallback (for example b-75, -52, -22, +10, +37, +67,
  +97 s), never right after a window probe at b-3 s. A probe still `none`
  after its retry makes the run insufficient; rerun it. On iOS `flow set --window` still refuses;
  set the rule with a scenario: `swipeLeft` on the website row,
  **Open briefly…**, `targets_window_period_30`, `targets_window_length_5`,
  `action_save_short`.
- Window enforcement on Linux, Windows, and Android (`TARGETS-013` phase 4):
  each enforcer follows the plans of the last apply on its own clock, so
  terminate Posato (`terminate -t linux|windows`) after the pause starts and
  the window still opens and closes. Probe inside the guest or phone on its
  own clock with a script started detached before the boundary: on Linux
  `getent ahosts` and a `curl` load with a `probe` query (`setsid nohup`
  from `linux exec`); on Windows `curl.exe` (status `000` is blocked;
  `Start-Process -WindowStyle Hidden` from `windows exec`); on Android
  `ping -c 1 -W 2 <name>` for the resolved address (`127.0.0.1` is blocked;
  `adb shell 'nohup sh script &'`). Linux and Windows evaluate on a
  one-second tick, so a probe right at b can still read the old state.
  Android caches DNS answers by TTL: a blocked answer lives 30 s and an open
  one at most the window's rest (at least 30 s). On Windows choose a ruled
  website and a control that share no address, or the ruled one stays
  blocked by design. On Linux `sudo cat /var/lib/posato/state` shows the
  apply line with its plans field.
- To overlap a scheduled pause with a manual session on one device, start
  the manual session first and add a schedule that starts during it;
  Session offers no new session while a scheduled pause runs.
- On the Galaxy XCover7 the new-set dialog's **Save** and a website row's
  popup menu carry no resource id in the UIAutomator tree, so `flow set`
  stops there; press them by their read bounds with `adb shell input tap`
  and read the row caption back.
- On a Mac clone whose iCloud row is offered, `flow folder link` cannot
  reveal the folder field: type the path into the field under "Or sync
  through a folder instead of iCloud." and press **Use this folder**, then
  `flow folder join` works. A rule added or removed on one Mac reaches a
  running pause on the other through the relay in under a minute.

## Driving notes

- List rows are buttons labelled with their whole text; query them with
  `textContains` and role `button` (`"Work"`, `"My set"`). The sidebar button
  `Pause sets` also contains "Pause set", so query the setup row as
  `textContains "Pause set,"`.
- On the Mac each row and the set screen have `More actions for <name>` with
  Rename, Make default (not on the default), and Delete (not on the default).
  A website row's menu is `Actions for <website>` with Edit, Open briefly…
  (`targets_open_briefly`), and Remove; the dialog's choices are
  `targets_window_period_30|60|120` and `targets_window_length_5|10|15`, then
  `action_save_short` or `targets_window_clear`.
  On iOS a row slides out Rename and Delete under `swipeLeft`, and the set
  screen's bar keeps `More actions for <name>` with all three.
- The set choice is a `Pause set, <current>` button (on iOS a system pop-up
  labelled `Pause set` with the set as its value) whose menu rows read
  `<name>, <n> websites`, the default as `<name>, Default · <n> websites`.
- Delete is refused while a running session or schedule uses the set; the
  set screen then says removed items stay paused until that pause ends.
- A run that links iCloud ends with Remove workspace before `vm destroy`.
