# Release 1.3: pause set rules (`SCHEDULE-003`)

- **Status:** Accepted. The product decisions are `user-confirmed`
  (2026-09-29 and 2026-09-30). The ADR 0006 amendment they need passed an
  independent security review after fixes and was accepted by the
  maintainer on 2026-09-30 (`user-confirmed`).
- **Owner:** `SCHEDULE-003` decides; `SCHEDULE-004` delivers. Release 1.6
  adds [website windows](#website-windows-targets-013), decided by
  `TARGETS-013` and `user-confirmed` by the owner on 2026-11-03.
- **Authorities:** [product scope](pause-sets.md),
  [`DESIGN.md`](../../DESIGN.md#release-13-pause-sets),
  [ADR 0006](../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md#schedule-003-pause-set-operations-amendment),
  [1.2 schedule rules](schedules-decisions.md).

The rules keep the 1.2 promise: the simplest behavior a non-technical person
can predict. A set is a named list of websites plus this device's app
choices. A running pause only ever gains items until it ends.

## What a person sees

- **Pause sets** replaces **Paused items** in the main navigation. It lists
  the sets; the default is marked. Opening a set shows its websites and this
  device's apps, as Paused items does today.
- Start a session: choose a set (the default is preselected), then a
  duration. Add or edit a schedule: choose its set with the other fields.
- An upgrade changes nothing that is paused: the current items become the
  first set, which is the default, and every schedule uses it.

## Decisions

### Sets and the default

- **Limit.** At most 10 sets per workspace, including the first set. The
  existing website limit counts unique websites across all sets: an address
  in three sets counts once (`user-confirmed` 2026-09-29).
- **Names.** 1-80 bytes of text, like schedule names. Names need not be
  unique. The first set has no stored name until someone renames it; each
  device shows it as "My set" in its own language.
- **One synchronized default** (`user-confirmed` 2026-09-29). **Make
  default** on any device changes it everywhere. The default cannot be
  deleted; make another set the default first. If the chosen default is
  deleted on another device at the same moment, the first set becomes the
  default, or, when that is gone too, the oldest remaining set. With no set
  left, Start and Add schedule offer **New set**.

### Running pauses

A pause consists of **parts**: the manual session and each running schedule
occurrence. Each part uses one set.

- **Additions apply at once.** A website or app added to a set that a part
  uses is paused at once on every device that runs the part and has
  received the change (`user-confirmed` 2026-09-29).
- **A running part never releases an item early** (`user-confirmed`
  2026-09-30). The one exception for enforcement is a website's window
  (see [Website windows](#website-windows-targets-013)), which never removes
  the website from what the part holds. Everything a part has paused on this device stays paused
  until that part ends: after the item is removed from the set, after a
  running schedule is switched to another set (the new set's items are
  added at once), and after the set is deleted on another device. The part
  ends at its end time, by **End early**, or by a schedule edit that stops
  the occurrence under the 1.2 editing rule; that edit releases everything
  the occurrence held.
- **Local memory.** Each device remembers what each part has paused, across
  relaunch, and forgets it when the part stops for any reason. It is not
  synchronized: a device that never paused an item for a part does not
  start pausing it after its removal. A part that is running when the
  device upgrades keeps the first set's items at that moment
  (`user-confirmed` 2026-09-30: a 1.2 session's start-time list is not
  kept, so an item removed during that session may be released then).
- **Device limits.** Everything all running parts pause together,
  including what they keep, must fit the device's limits: 1,024 websites
  and 64 apps on the Mac; on iPhone and iPad 50 web domains, which is 25
  websites with their `www` counterparts, and 50 apps (see Execution,
  iPhone). An edit that would go beyond them pauses none of its new items
  and says they are not paused yet; what is already paused stays.
  `user-confirmed` (2026-09-30): a new part pauses as many of its new
  items as still fit and says how many are not paused; it never leaves
  the whole website filter off. Websites are taken in alphabetical order
  (`inferred`: a fixed order every device computes alike).
  `user-confirmed` (2026-10-01): items an edit could not pause are tried
  again whenever the pause is recomposed and are paused once they fit.
- **Mac expiry while Posato is quit** (`user-confirmed` 2026-10-01, known
  limit). The helper holds one request with the latest end of all parts,
  so a manual session that ends while Posato is quit keeps its own items
  paused until the latest running part ends or Posato opens and
  recomposes.
- **Overlap** (`user-confirmed` 2026-09-29). A manual session and scheduled
  occurrences with different sets pause the union of their sets, as
  overlapping schedules do. When one part ends, only items no remaining part
  needs are released. **End early** still ends every part.
- **Manual start during a scheduled pause** stays unavailable, as in 1.2
  (`user-confirmed` 2026-09-30). A schedule that starts during a manual
  session joins it with its own set.
- **A session received from another device** keeps the 1.2 rule: a running
  occurrence on the Mac does not enforce it, and when the occurrence ends
  the Mac offers **Resume restrictions** for that session.

### Deleting a set

- A set that a schedule or a running part uses cannot be deleted
  (`user-confirmed` 2026-09-29). Delete names its users and offers **Change
  their set**, which moves the schedules to another set and then deletes.
  While a running part uses the set, Delete is unavailable until the part
  ends.
- The default set cannot be deleted.
- Deleting a set deletes its websites from that set only; the same address
  in another set stays.

### A set this device does not have

`user-confirmed` (2026-09-30): a schedule or session that names a set this
device does not have (deleted on another device at the same moment, or not
received yet) treats it as an empty set. Nothing new starts for it; a part
that is already running keeps what it paused. Schedules shows "This
schedule's set was deleted. Choose a set." for a deleted set, "This set is
over the limit of 10. Delete a set to use it." for a set refused at the
limit, and "Waiting for this set from your other devices." for one not
received yet.

### Readiness

- **Websites without apps.** A pause starts with the set's websites when
  this device has no app choices for the set, with no notice: a set of
  websites only is complete, and **Choose apps** stays in the set's Apps
  tab (`user-confirmed` 2026-10-20, idea 40; this supersedes the notice
  with **Choose apps** confirmed on 2026-09-29).
- **Nothing to pause.** A set with no websites and no apps chosen on this
  device cannot start here: Start is replaced by **Add websites or apps**,
  and a schedule shows "Nothing to pause on this Mac" (or iPhone, iPad).
- **The application group is retired.** `observed`: the synchronized
  application-group name is always "Applications", acts only as a readiness
  gate, and enforcement already uses the whole local app choice without it.
  From 1.3 the set replaces the group: apps chosen for a set count on this
  device, and no synchronized flag says that another device chose apps.

### Migration

- **Identity.** The first set has a fixed identifier, the same on every
  device. Existing websites, schedules, and sessions already refer to it,
  so two devices that upgrade at the same time cannot create two first sets.
  Each device assigns its own current app choices to the first set locally.
- **What is published.** In a linked workspace, the upgrade publishes one
  "pause sets enabled" operation at once (`user-confirmed` 2026-09-29), so
  linked devices still on 1.2 stop receiving right away instead of at some
  later edit. The upgrade never publishes a name or a default, so a device
  that upgrades later cannot overwrite a rename or a default chosen on
  another device.
- **Nothing else changes.** Paused websites, app choices, schedules, a
  running session, skip and end facts, and schedule pins stay as they are.
- **Local-only devices** migrate locally and publish nothing. Linking later
  merges their first set with the workspace's first set by identity (the
  union of websites, as linking does today) and publishes their other sets
  as new sets. The workspace's set names and default win. Beyond 10 sets,
  sync reports action required with the limit, as it does for websites.
- **Known limit** (`user-confirmed` 2026-09-30). A device that links, or
  upgrades from 1.2, after the first set was deleted on another device
  loses its old websites and app choices from that set. No extra rule
  covers this. It is recorded only in internal documents, not in the
  README, on `posato.app`, or on the limits page. Likewise
  (`user-confirmed` 2026-09-30), a device that creates a new workspace
  does not publish its first set's name or its default at first link, so
  they return to "My set" and the first set.

### Synchronized operations (ADR 0006 amendment)

Eight new mandatory format-1 kinds; the
[amendment](../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md#schedule-003-pause-set-operations-amendment)
is normative.

| Kind | Payload | Reduction |
| --- | --- | --- |
| `12` `set-put` | set id, name | Greatest total-order put names the set; a set-remove wins. |
| `13` `set-remove` | set id | Permanently removes that set, whatever the order. |
| `14` `set-domain-present` | set id, domain | Adds the domain to that set; one website cap across sets. |
| `15` `set-domain-absent` | set id, domain | Removes the domain from that set. |
| `16` `set-default` | set id | Greatest total-order choice is the default. |
| `17` `schedule-set-put` | kind 8 payload, set id | A schedule put that names its set. |
| `18` `session-set-start` | kind 6 payload, set id | A session start that names its set. |
| `19` `pause-sets-enabled` | empty | Marks the workspace as migrated. |

The older kinds 2, 3, 6, and 8 stay valid and mean the first set, so history
and changes from a device that has not updated yet still apply.

### Compatibility

- `observed`: a 1.2 device rejects an unknown mandatory kind as an invalid
  operation, and stops receiving with "Sync needs attention"
  (`AppleMailboxExchange.acceptPage`). It still publishes its own changes
  first on each pass (`AppleSync.exchangeLegsOrHalt`): its website edits
  and sessions keep arriving and apply to the first set, and are dropped
  once the first set is deleted. Decision (`user-confirmed` 2026-09-29):
  accepted for 1.3, as 1.2 accepted it for 1.1. The first Pause sets
  screen on a linked workspace and the release notes say: "Update Posato
  on your other devices to keep them in sync."
- A 1.2 installation that opens a 1.3 database holding the new operations
  fails closed, as a downgrade did in 1.2; the supported path is to install
  1.3 again. `SCHEDULE-004` decides the local-only downgrade, whose
  database holds none.
- The optional kinds 128-255 are not used: these operations change what is
  paused.

## Website windows (`TARGETS-013`)

A website in a pause set can have a **window rule**: during a pause that
uses the set, it opens by itself for a few minutes at fixed clock times and
is blocked again afterwards, on every device of the workspace. A website
without a rule behaves exactly as before.

The decisions are `user-confirmed` (2026-10-30, questionnaire); the task
brief is
[`targets-013-timed-website.md`](../tasks/specifications/targets-013-timed-website.md).
The rules below them were decided on 2026-11-01 by the owner's agent under
the owner's delegation of 2026-10-31 and `user-confirmed` by the owner on 2026-11-03 in the `TARGETS-013` pull request (#221); each was a gate question with a recommendation,
and the pull request lists them. The normative operation is the
[ADR 0006 amendment](../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md#targets-013-set-domain-window-amendment);
the Mac page is in the
[ADR 0005 amendment](../decisions/0005-macos-browser-enforcement-and-coexistence.md#targets-013-window-page-amendment),
the helpers in the
[ADR 0004](../decisions/0004-macos-helper-ownership-and-lifecycle.md#targets-013-window-plan-amendment)
and [ADR 0010](../decisions/0010-linux-android-and-folder-workspace.md#targets-013-window-plan-amendment)
amendments, and the screens in
[`DESIGN.md`](../../DESIGN.md#release-16-website-windows).

### Decisions (`user-confirmed` 2026-10-30)

- **Per website in a set.** The rule belongs to one website inside one set.
  The same website in another set can have no rule.
- **Fixed clock times.** Windows start at clock times aligned to the hour:
  every 30 minutes, 1 hour, or 2 hours, starting at :00. They are not
  counted from the pause's start.
- **Fixed lengths.** A window lasts 5, 10, or 15 minutes.
- **Opens by itself.** The website opens with no action during its window,
  and the pause page says when it opens next.
- **Websites only**, as one exact website and its `www` form (ADR 0005,
  `TARGETS-006`). Applications, patterns, a daily budget, usage counting,
  and browsing history are non-goals.
- **Decide first.** These rules, the screens, and the ADR amendments are
  accepted before product code.

### Rules

- **When a window is open.** A rule is a period (30, 60, or 120 minutes) and
  a length (5, 10, or 15 minutes). It is open when the local clock's reading
  (hour × 60 + minute, as the clock shows it, not minutes elapsed since
  midnight), modulo the period, is less than the length.
  Example: 1 hour and 5 minutes opens 10:00-10:05, 11:00-11:05, and so on;
  every 2 hours opens at 00:00, 02:00, 04:00.
- **A website and its `www` form are one website.** When a set, or two
  sets in use, hold both `example.com` and `www.example.com`, the website
  opens only when every stored entry for it has a rule and is in its window.
- **Several parts (the most restrictive wins).** A website opens only while
  every running part that covers it is inside its own window. A covering
  part whose set has no rule for the website keeps it blocked, including a
  part that keeps the website only because it paused it before the website
  left the set. Two parts with different rules open only when both are
  open, which is the single rule (longest period, shortest length): every 30
  minutes for 15 and every hour for 5 open together for 5 minutes on the
  hour.
- **Paused, then opened.** What "Running pauses" above says about what a
  part holds is unchanged: retention, the device limits, and the items
  shown as not paused yet are computed as if no window existed. A window only
  lets the device leave a held website unblocked while it is open, so it
  never frees room for other items and never releases anything a part holds
  for good. When the window ends the website is blocked again because it was
  never let go.
- **All websites open.** When every paused website is in its window and no
  app is paused, the pause stays active; Session keeps it and **End early**
  still ends it. Blocking returns at the window's end.
- **Breaks are independent.** A break in a scheduled pause (`SCHEDULE-007`)
  already releases that part entirely; its windows do not matter during it,
  and after the break the device follows the windows again.
- **Each device's own clock.** Windows follow the device's own wall clock and
  time zone, as schedules do; devices in different time zones may open at
  different instants. A device that is offline or asleep follows its own
  clock. On a daylight saving change, a window inside a skipped hour does not
  occur and no window moves: the next window is the next aligned time that
  exists on the clock. In a repeated hour a window opens once for each time
  the clock shows it; an iPhone or iPad skips the second one when it cannot
  arm it unambiguously, which only blocks more.
- **A rule set or changed during a pause** applies to running parts as soon
  as the device receives it, like an added website: a new or edited rule
  opens the website only inside its window. Clearing a rule, removing the
  website, or deleting the set closes an open window at once on each device
  that receives and applies the change. At most 15 minutes in any 30 can
  open, and only while every covering part has a rule. A closed iPhone, and a
  Linux or Windows device whose app is not running, apply a cleared rule
  only when Posato runs there again; until then, or until the pause ends,
  they keep opening the website on its old rule.
- **Removing and adding a website again** starts with no rule: removing a
  website clears its rule on every device, also when an older Posato or an
  import removed it. `inferred`: a rule set on another device after the
  removal, before that device saw it, applies once the website is back; it
  shows in the website's row.
- **Linking.** When a device with rules links to a workspace, the
  workspace's rules win, as its set names and default do: the device
  publishes a rule only for a website the workspace's set has and has never
  ruled.
- **A set this device does not have** has no rules, so nothing opens for it.
- **Where "opens next" shows.** On the Mac pause page (Safari and Chrome),
  and on Session on every platform: "Opens at 10:30" for a blocked website
  with a next window in the running pause and "Open until 10:35" while it is
  open. The iPhone and iPad Safari page stays the system's restricted page,
  and Linux, Android, and Windows have no pause page; a shield configuration
  extension is a non-goal.
- **Older devices.** A device on 1.5 or a 1.6 build without windows stops
  receiving changes once any device sets or clears a rule, until it updates,
  and keeps blocking every website without a window. The release notes say
  "Update Posato on your other devices to keep them in sync."
- **Export and import (`TARGETS-007`).** The file carries the rules in the
  same content version, as an optional field on each set beside its list of
  websites, which stays a list of plain names; the field maps a website to
  its period and length. An older Posato ignores the field and imports the
  websites without rules, which only blocks more. A newer Posato drops an
  invalid entry (a pair outside the fixed choices, a website not in the set,
  or a duplicate) and imports that website without a rule. The import review
  shows the rules with the websites. **Replace my pause sets** takes the
  file's rules. **Add to my pause sets** with **Combine sets with the same
  name** keeps the rule of a website already in the set here and takes the
  file's rule for a website new to that set; with **Keep them separate**
  every imported set takes the file's rules.
- **Open connections and caches.** Closing a window blocks new loads. On
  the Mac, Posato also ends the connections it carries for that website. On
  Windows, Linux, and Android, a connection that is already open, or an
  address a browser cached during the window, can keep the site working
  until it closes or expires. Verification uses fresh loads; the limits page
  names this.

### Per platform

- **Mac.** The helper decides each request and connection from the window
  plan with its own clock and moves between windows and part ends without
  the app's request. Closing the window keeps Posato in the menu bar and
  windows keep working; quitting Posato stops all blocking on the Mac, as it
  does today. The pause page shows the opening time through a fixed address
  that holds no website (ADR 0005 amendment).
- **Linux and Windows.** The helper follows the window plan, checking the
  clock every second, and keeps doing so after the app's process is terminated, until the
  pause's end. On Windows a website that shares an address with a blocked
  website stays unavailable during its window (ADR 0010 amendment).
- **Android.** The VPN service decides each lookup from the window plan;
  exact alarms at each boundary refresh it, as for breaks.
- **iPhone and iPad.** The monitor extension changes the shields at window
  boundaries while Posato is closed, from the table it already keeps for
  scheduled parts and the manual session. The transition contract:
  - **One activity per boundary (G21).** Every upcoming opening and close
    is its own named Device Activity, registered by the app when it
    applies a pause and topped up by every callback: an interval ending at
    the boundary when that leaves its start at least 15 minutes ahead,
    otherwise one starting at the boundary, so no registration starts in
    the past. A callback reads the clock and registers the boundaries
    ahead that no activity covers; it never restarts or stops the activity
    it was called for, and it stops other fired window activities only
    after it released the compose lock. The device probe of 2026-11-01 showed that
    re-registering one activity from its own callback lost the next close.
    Breaks keep their own activity.
  - **Close before open.** A website opens only while an activity is
    registered for its close. If that registration fails, or the platform's
    limit of 20 activities leaves no room for it, nothing opens; the next
    callback or app start retries. A callback frees room: once the compose
    lock is released, it stops window activities whose instant passed more
    than the fallback delay plus the closing lead ago, never its own, and
    then registers the boundaries ahead again, so windows keep opening for
    as long as Posato stays closed.
  - **The clock decides.** Every callback recomputes what is blocked from
    the real clock, so callbacks are idempotent.
  - **Never early.** The 60-second early-callback margin of breaks is not
    used for windows. A callback that arrives before an opening, by any
    amount, keeps the website blocked. An end of the opening's own activity
    before its boundary no longer covers it, so the callback registers the
    opening's fallback, the opening plus 20 seconds, as a separate
    activity, once; the fallback's own early callback adds nothing more.
    Re-registering an activity with the same name and interval is accepted
    and does not end it early (device probe, 2026-11-01).
  - **Closing.** A callback within 60 seconds before a close closes early,
    which only blocks more; an earlier end of the close's activity
    registers the close's fallback in the same way.
  - **Late, duplicate, stale.** A late callback applies the current state,
    so a late opening after its window ended stays blocked. A duplicate or
    stale callback tops up the boundaries ahead and changes nothing else.
  - **Manual sessions too.** The window callback evaluates the manual
    session's windows even when no scheduled pause runs.
  - **Opening Posato repairs.** Each app start evaluates the windows from
    the clock and tops up the boundaries, so a missed callback lasts at
    most until the next callback or until Posato opens.
  - **Timing tolerance.** An opening happens in `[boundary, boundary + 60
    s]`. A close happens in `[end - 60 s, end + 60 s]`, where the early part
    is the restrictive direction. An early opening is never allowed.
  - The 50-domain limit is unchanged: an open window frees no room.

### Verification

During a pause with a 30-minute, 5-minute rule in real time, a fresh browser
load is blocked before the window, open inside it, and blocked after it on a
Mac in a Tart guest (app window closed, because quitting Posato stops all
Mac blocking), the test iPhone and Linux and Windows (app process
terminated), and Android, while a website without a rule in the same set
stays blocked. A part that ends while the app's process is terminated is
proven on Linux. A rule added on one Mac during a running pause opens a
window on a peer, and clearing it closes the window. The task plan holds the
measurement rule.

## Execution

### iPhone

- The App Group table becomes version 2: sets with their domains and app
  tokens, each schedule's set, and the running parts with what each has
  paused. The extension applies, at `intervalDidStart`, the union of the
  running occurrences' sets plus what they already hold, and, at
  `intervalDidEnd`, keeps what another running part still needs. An
  unreadable table clears, as today.
- **Upgrade before the app opens.** An App Store update does not open
  Posato, and the extension's callbacks run on their own. Until the app
  commits a version-2 table, the 1.3 extension keeps reading a valid
  version-1 table as the first set: its global items belong to the first
  set, every schedule in it uses the first set, and its running entries
  keep what they paused. Existing schedules therefore keep starting, and an
  earlier occurrence ending never releases a later one. The app deletes
  the version-1 table in the same step as its first version-2 commit. Once
  a version-2 table exists, an unreadable one clears as today and never
  falls back to version 1.
- The manual session keeps its own named store and the schedules share
  theirs. `observed` (2026-09-30, test iPhone): named stores compose, and
  the web filter's 50-domain bound counts their union; past it iOS drops
  the whole website filter, so nothing is blocked (wiki
  `ios-enforcement`). Release 1.2 has this defect for more than 25
  websites; `user-confirmed` (2026-09-30): it is fixed in 1.3 by this
  row, not in a 1.2 patch.
- App choices move from one file to one file per set. `user-confirmed`
  (2026-09-30): the ceiling drops from 64 to 50 tokens, Apple's
  documented shield bound (`source-claim`, not measured), counted over
  unique tokens across sets on the device.

### Mac

- Today's helper holds one configuration (`observed`, `PauseClaims`): a
  joining claim either keeps the held request or replaces it, and nothing
  forms a union. `SCHEDULE-004` makes the app compose one
  request from all parts: the union of their sets' domains and apps, their
  retained items, and the latest end. A part ending recomposes it.
- `observed`: a change is a helper clear followed by an apply, inside one
  app lock. Sets make such changes more frequent (additions, part ends).
  `observed` (2026-09-30, Tart VM): each clear and apply left the proxy
  off for about 0.2-0.25 s, and a blocked page loaded in that moment.
  `user-confirmed` (2026-09-30): accepted for 1.3 as an internal known
  limit; the atomic replacement is backlog row `MACOS-025`.
- App choices gain a set column in the Mac's app-choice database; the
  existing rows move to the first set.

## Privacy

`PRIVACY.md` is published to `posato.app` when it changes on `main`, so it
changes with the release that ships pause sets, not with this decision.
Proposed edits:

- **On the device:** "the names of your pause sets, which websites each
  contains, and which apps you chose for each on that device".
- **Sync history:** "every pause set created, renamed, or deleted, every
  website added to or removed from a set, and which set is the default and
  which set each session and schedule used". The history already describes
  the retired app group name; that sentence stays for older entries.
- **iCloud sync:** add "your pause sets: their names, their websites, and
  which is the default" to what is encrypted before storage.
- **Screen Time on iPhone:** the extension's copy holds each set's websites
  and app choices. `observed`: that copy and the app-choice file are
  excluded from device backups (`ScheduleMonitorShared.swift`,
  `IosApplicationMappingsProvider.swift`), while the policy says they can be
  included. The release edit corrects this sentence.
- **Privacy manifests:** set names are synchronized only to the person's
  own iCloud and are never collected by the developer, so the app manifest
  keeps "no data collected". `open`: the monitor extension reads a file's
  attributes (`attributesOfItem`, size only) and declares no required-reason
  API; `SCHEDULE-004` checks whether the extension needs the file-timestamp
  entry already declared by the app.

## Implementation plan for `SCHEDULE-004`

Slices, stacked, each proven before the next:

1. **Model and sync.** Kinds 12-19, set liveness and cap, per-set domain
   reduction with the shared cap, the default fallback, legacy kinds as the
   first set, and reference resolution. Cross-target golden vectors for
   every payload and isolated reducer tests written failing first for
   delivery permutations, a remove racing a put, a domain racing its set's
   removal, the capacity edges, and the default fallback: a codec or
   reduction disagreement never shows in a single-device E2E.
2. **Migration and storage.** SQLDelight migration 13 (sets, per-part
   retention), the Mac app-choice migration, the iPhone per-set app files,
   retention for parts running at migration, and kind 19 on first open in a
   linked workspace. Migration tests from earlier schemas built from
   tracked history with synthetic rows, including a running session and
   schedule pins (`user-confirmed` 2026-09-30; supersedes seeding from
   1.2-produced databases).
3. **Pause sets UI.** The destination, set editor, Make default, Delete
   with **Change their set**, the set choice in Session and the schedule
   editor, readiness copy, and the 10-set cap. Proven by E2E.
4. **Hosts.** Mac union request and retention, the iPhone table version 2
   and extension, overlap release per part. Proven by E2E.

Verification, unattended (`AGENTS.md`):

- **Tart, two VMs** (`--vm primary` and `--vm peer`), starting from a 1.2
  workspace with websites, a schedule, and a running session:
  - upgrade both; the first set has the old websites, the schedule and
    session continue, no duplicate set, and `observe --expect blocked`
    holds throughout;
  - a 1.2 peer stops receiving after the other VM upgrades;
  - two sets, a manual session on one and a schedule on the other starting
    inside it: both sets blocked, the manual end releases only its set;
  - add during a pause blocks at once; remove during a pause stays blocked
    until the part ends, also across a relaunch; delete on the peer while
    running keeps it blocked;
  - a 1.2 peer that edits after it stopped receiving: its websites land in
    the first set;
  - an 11th set is refused on both VMs with the limit named;
  - a local-only VM with two sets links: the first sets merge and the other
    set is published;
  - offline peer: the schedule runs with its set; sync converges afterwards.
- **Test iPhone** (`-t device`): upgrade with app choices and Screen Time
  consent, per-set app choice, websites-only start, overlap of a manual
  session and a schedule with different sets, and extension start and end
  with Posato closed.
- **Upgrade without opening the app** (test iPhone): install a 1.2 build
  with two overlapping schedules, update to 1.3 while the earlier
  occurrence runs, and do not open Posato. When the earlier one ends, the
  later one stays blocked; a start after that still applies; opening
  Posato afterwards writes the version-2 table without a gap and the
  version-1 table is gone.
- **Cross-device:** a set created on the Mac appears on the iPhone with no
  apps chosen there, and a schedule using it starts with its websites.

Maintainer gates:

- the ADR 0006 amendment accepted before `SCHEDULE-004` starts;
- an independent High-risk plan review of the `SCHEDULE-004` brief;
- the Pause sets screens reviewed on screenshots after slice 3;
- the migration run on synthetic 1.2 data reviewed before `RELEASE-005`;
- the `PRIVACY.md` edits accepted with the release.

Checks: `./gradlew quality` after the last correction, an independent
completed-change review per slice, and /visual-pr descriptions.
