# Release 1.2: schedule rules (`SCHEDULE-001`)

- **Status:** Accepted. The product decisions are `user-confirmed`
  (2026-09-26, delegated night mandate); the ADR 0004, ADR 0006 and ADR 0009
  amendments they need passed an independent security review and were
  accepted by the maintainer on 2026-09-27 (`user-confirmed`).
- **Owner:** `SCHEDULE-001` decides; `SCHEDULE-002` delivers.
- **Authorities:** [product scope](schedules-and-mac-setup.md),
  [`DESIGN.md`](../../DESIGN.md#release-12-setup-and-schedules),
  [ADR 0004](../decisions/0004-macos-helper-ownership-and-lifecycle.md),
  [ADR 0006](../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md),
  [ADR 0009](../decisions/0009-macos-menu-bar-presence.md).

The maintainer delegated these choices overnight and asked for the simplest
behavior a non-technical person can predict. Every rule below prefers "what
the clock on this device says" and "one pause at a time" over flexibility.
Each rule is marked as a decision; each supporting fact carries its
provenance label.

## What a person sees

- A schedule has a name, the weekdays it repeats on, a start time, an end
  time, and an on/off switch. Up to 10 schedules per workspace.
- A schedule starts restrictions at its start time on every device that is
  set up, and ends them at its end time. Nothing else to remember.
- **Skip next session** and **End early** affect only one occurrence. The
  plan keeps repeating.
- If the device was asleep, off, or closed at the start time, it catches up
  when it wakes or opens during the interval and still ends at the planned
  end.

## Decisions

### Time and calendar

- **Local wall clock.** A schedule runs at the stated times on each device's
  own clock and time zone. A person who travels sees their schedule follow
  them. Two linked devices in different time zones each run it at their own
  local time. There is no "home" time zone.
- **Weekdays belong to the start.** The selected weekdays name the day on
  which an occurrence starts.
- **Crossing midnight.** An end time equal to or earlier than the start time
  ends on the next day. 22:00 to 06:00 on Friday runs Friday 22:00 to
  Saturday 06:00.
- **Length.** An occurrence lasts at least 15 minutes and less than 24
  hours; the start and end times differ. The 15-minute floor matches the Device Activity minimum that Posato
  already uses on iPhone (`observed`, `SuspendedExpiryActivity.minimumInterval`
  = 15 minutes). The 24-hour ceiling matches the ADR 0006 session bound.
- **Daylight saving.** A start time that does not exist on the day of a
  spring-forward change starts at the first valid minute after it. A start
  time that happens twice on a fall-back day starts at its first instance.
  The end is the stated wall-clock end on the end day. An occurrence can be an
  hour shorter or longer on those two days, but never runs longer than 24
  hours of real time; a longer one ends at 24 hours.
- **Clock changes.** Each device evaluates schedules against its current
  clock whenever it starts, wakes, changes time zone or time, and at least
  once a minute while Posato for Mac is running. A change never creates an
  occurrence for an interval that has already ended.

### One pause at a time

- **Overlaps.** Restrictions are active while any enabled schedule
  occurrence or a manual session is active. Session shows one pause: it names
  the running schedule, or the first one when several overlap, and shows the
  latest end.
- **End early ends the pause.** On Session, **End early** ends everything
  that is currently restricting on this workspace: the manual session and
  every running scheduled occurrence. This keeps one button with one meaning.
- **Manual start during a scheduled pause** is not offered. Start is replaced
  by the running pause. After **End early**, a manual session can start as
  usual.
- **A session received from another device** is not enforced by a running
  occurrence on the Mac. When the occurrence ends while that session is still
  active, the Mac returns to **Resume restrictions** for it.
- **A scheduled occurrence during a manual session** joins it. Restrictions
  continue until the later end.

### Paused items

- A scheduled occurrence restricts the current paused items at each moment,
  like a manual session (ADR 0006 keeps session policy current-state based).
  Application choices stay device-local.

### Occurrence identity and convergence

- **Identity.** An occurrence is `(schedule identifier, local start date)`.
  Each device derives it from its own clock, so no operation announces an
  occurrence start.
- **Skip and end.** **Skip next session** publishes `schedule-skip` and
  **End early** publishes `schedule-occurrence-end` for that
  `(schedule, date)`. Both are grow-only facts: any copy of either fact, in
  any order, makes that occurrence inactive on every device that receives it.
  They are never undone. A person who changes their mind uses a manual session.
- **Offline devices.** A device that has not received a skip or end yet runs
  the occurrence and stops when the fact arrives. This is the existing
  best-effort sync contract; there is no remote wake. `inferred`: a device in
  another time zone applies the fact to its own occurrence with the same
  local date.
- **Local persistence.** A device remembers its local skip and end facts
  across restart and relaunch before the first sync (the product scope
  requirement), in the same local transaction as the operation it authors.
  It also keeps a local terminal marker for every occurrence that ended or
  expired, so a clock rollback or a later edit never recreates it.
- **Dates.** A skip or end names a date at most 400 days ahead.
- **Editing a schedule** that is running ends nothing by itself. The running
  occurrence follows the edited end time, which may extend it. Turning a schedule off or deleting
  it ends its running occurrence at once.

### Synchronized operations (ADR 0006 amendment)

Four additive format-1 operation kinds, from the accepted ADR 0006 amendment:

| Kind | Payload | Reduction |
| --- | --- | --- |
| `8` `schedule-put` | 16-byte schedule id, name (1-80 UTF-8 bytes, NFC), `u8` weekday mask (bits 0-6, Monday first, at least one), `u16` start minute (0-1439), `u16` end minute (0-1439), `u8` enabled | Greatest total-order put per id wins, unless the id is removed. |
| `9` `schedule-remove` | 16-byte schedule id | Permanently removes that id, whatever the order. |
| `10` `schedule-skip` | 16-byte schedule id, `u16` year, `u8` month, `u8` day | Grow-only set of skipped occurrences. |
| `11` `schedule-occurrence-end` | same as kind 10 | Grow-only set of ended occurrences. |

- The effective set holds at most 10 live schedules. A put beyond the cap
  gets a truthful `schedule-capacity` outcome, like the domain cap.
- **Compatibility.** `observed`: a 1.1 device rejects an unknown kind as
  `INVALID_OPERATION`, and its mailbox exchange then reports action required
  and stops advancing (`AppleMailboxExchange.acceptPage`). So once any device
  saves a schedule, a linked device still on 1.1 stops syncing until it
  updates. Decision: accept this for release 1.2. The iPhone updates through
  the App Store and the Mac through the in-app updater. Release notes and the
  first **Add schedule** on a linked workspace say: "Update Posato on your
  other devices to keep them in sync."
- **Forward compatibility.** From 1.2, kinds 128-255 are optional
  extensions. A 1.2 device accepts and stores an unknown optional kind,
  advances the author's sequence, and ignores it in projection. Later
  releases can then add optional operations without stopping 1.2 devices.
  Kinds 12-127 stay mandatory and are rejected as today.

### iPhone execution

- **Mechanism.** `source-claim` (Apple DeviceActivity documentation): a
  repeating `DeviceActivitySchedule` calls the monitor extension's
  `intervalDidStart` and `intervalDidEnd`. Posato registers one repeating
  daily activity per enabled schedule. At `intervalDidStart` the extension
  checks the weekday, skip and end facts, then applies the shields. At
  `intervalDidEnd` it clears them unless a manual session or another
  occurrence is still active.
- **Limit.** `source-claim`: iOS monitors about 20 activities per app. With
  10 schedules plus the existing suspended-expiry activity, Posato stays
  under it.
- **What the extension reads.** `observed`: today the extension reads no
  selection tokens and no domains (`DeviceActivityMonitorExtension.swift`).
  Scheduled starts need them. Decision: the app writes one versioned App
  Group file whenever paused items, schedules, skips, or ends change. It holds
  the selection tokens, the domains, and the schedule table, and it stays on
  the device. The extension reads only that file.
- **Catch-up.** `hypothesis`: `intervalDidStart` may not fire for a device
  that was off at the start time. The app evaluates on launch and when it
  returns to the foreground, and applies an occurrence that is due.
  `SCHEDULE-002` verifies this on the test iPhone.
- **Consent.** Screen Time authorization is the consent on iPhone. A plan
  can be saved before it; it runs only once authorization is granted.

### Mac execution

- The resident process (ADR 0009) evaluates schedules at launch (including a
  login launch), on wake, on clock and time-zone changes, and every minute.
- A due occurrence applies through the unified setup's standing grant, with
  no prompt (the accepted ADR 0004 and ADR 0009 amendments). If the grant is
  missing, refused, or the setup is incomplete, Posato never shows a password
  dialog. It shows "Setup required on this Mac" in Session and Schedules, and
  posts one notification: "A scheduled pause couldn't start on this Mac.
  Open Posato to finish setup."
- While another account uses the Mac, the occurrence waits for the person's
  account to return. This is not "Setup required" and posts nothing.
- At most one attempt runs per trigger per occurrence.

### Notifications (with `NOTIFY-001`)

- A scheduled start posts "Scheduled pause started: <name>, until <end>".
  The end posts "Your scheduled pause has ended". Both follow the
  `NOTIFY-001` preference and system permission.
- On Mac they post when restrictions are actually applied or cleared.
- On iPhone, `inferred`: a monitor extension can post a local notification.
  If that fails in verification, the app pre-schedules calendar
  notifications per enabled schedule and removes them on skip, end, or
  disable.
- A notification is not proof of enforcement. Session and the menu show the
  truth.

### Setup and migration

- The unified setup's single action records the consent to automatic starts
  shown in its caption, which says they include "schedules added on your
  other devices". It is a local flag, stored with the grant state.
- A Mac that opted in through `MACOS-014` before the schedule wording has the
  grant but not the automatic-start consent. It sees the dismissible upgrade
  offer once. Only actively accepting it records the consent; dismissing it
  does not. Accepting needs no second administrator password, because the
  grant itself is unchanged. Until then, its schedules show "Setup required
  on this Mac".
- The consent counts only while Posato confirms the grant on this Mac.
  Revoking the grant, Disable, Remove, or a check that finds the grant absent
  or unknown clears it.

## Integration contracts (maintainer review, 2026-09-27)

The combined review of #92-#97 asked for five contracts to be closed before
the remaining slices. Each is decided here, `user-confirmed` (delegated,
2026-09-27 goal: "decide autonomously in the spirit of the changes").

### 1. One truthful readiness result

- **Two results, one screen.** "Ready for pauses" is the `ONBOARDING-004`
  result: blocking, opening at login, and starts without a password, all
  verified. "Ready for schedules" adds a valid automatic-start consent. Mac
  schedule creation and automatic starts both read the second result; manual
  starts keep reading the first.
- **Consent is its own record.** It is stored as a local value under a key
  that names its wording's version (`automaticStartConsentV1`), never
  derived from the setup-offer marker; a new wording uses a new key.
  **Set up Posato** records it when pressed, because its caption names
  automatic starts "including schedules added on your other devices"; it
  counts only while a Status confirms the grant, and it is cleared by
  Revoke, Disable, Remove, or an absent or unknown grant.
- **No hidden grantees.** `observed`: the standing grant (`MACOS-014`) never
  shipped in 1.1, so no installation outside development has a grant
  without the unified setup. A grant turned on only through This Mac's
  switch has no consent: Schedules then shows one card, "Allow schedules to
  start on this Mac", whose single action records the consent without a
  password (the grant is unchanged). Dismissing it records nothing.
- **Honest claims.** With a verified setup but no consent, Session and This
  Mac still say ready for pauses; Schedules says "Schedules can't start on
  this Mac yet" with the one action. No screen says "ready" while a
  requirement it depends on is missing.

### 2. Notices follow the combined pause

- One owner, the notifier, plans from the **combined pause**: the manual
  session and every running occurrence on this device. Its end notice is
  scheduled for the latest end and rescheduled whenever that end changes; a
  manual session ending inside a schedule never posts **Pause over**.
- A scheduled start posts "Scheduled pause started: <name>, until <time>".
  The end posts the existing **Pause over**, so there is one end message.
- On iPhone the extension posts only what the app planned: the app writes
  the switch's state into the App Group file with the schedule table, and the
  extension reads it before posting. Turning notices off rewrites the file
  and removes pending requests.

### 3. A permission path for schedule-only use

- The system is asked once per device, at the first of: a pause started
  here, or the first schedule saved here. Never at launch and never at a
  scheduled start.
- A device that only receives schedules shows, in Schedules, a quiet "Turn
  on pause notices" action while the permission is undetermined and the
  switch is on. Refusing never affects blocking or setup.

### 4. A running pause survives edits and restarts

- When a host first sees an occurrence running, it **pins** it locally:
  the occurrence key and its original start. The pin survives relaunch.
- A pinned occurrence keeps running under edits: its end is the plan's
  current end time on its start date (the next day when that end is at or
  before the pinned start's time), capped at 24 hours after the original
  start. Moving the start, removing the weekday, or a remote edit does not
  end it; shortening the end can.
- Turning the schedule off, deleting it, **Skip** or **End early** for that
  key ends it; the host then writes the terminal marker and drops the pin.
  The engine takes pins as an input (slice 2 gains it in slice 4).

### 5. Promises agree with automatic starts

- Copy that says nothing is paused "until you start a session" changes to
  name schedules too; the grant's caption says restrictions apply "only
  during a pause you start or a schedule you set".
- Setup explains that it creates no schedule, and that an existing schedule
  can start once the device is ready.
- **Quit versus close.** Closing the Mac window leaves Posato in the menu
  bar and schedules keep starting. Quit stops new scheduled starts until
  Posato opens again (a login launch counts); when a schedule is enabled,
  Quit asks for confirmation with that sentence. Release notes say the same.
- **End early.** It ends the manual session and every occurrence running on
  this device, and publishes an end fact for each of those keys. Other
  devices running the same `(schedule, local start date)` end too.
  `inferred` limit, stated in the confirmation's caption: a device whose
  local date differs (another time zone around midnight) keeps its own
  occurrence; its own **End early** ends it.

## Implementation plan for `SCHEDULE-002`

Slices, each on its own branch, stacked:

1. **Model and sync.** Kinds 8-11, the terminal markers, and the optional-kind
   rule in the codec and reducer, with cross-language golden vectors for
   every payload invariant (including start different from end, the
   15-minute circular duration, and names that are not NFC). These are isolated
   tests, because a codec disagreement between JVM and iOS never shows in a
   single-device E2E.
2. **Occurrence engine.** A pure function from plans, facts, the clock, and
   the time zone to the active occurrence and the next run. Isolated tests
   written failing first for daylight saving, crossing midnight, the weekday
   of the start, and the 15-minute and 24-hour bounds; E2E cannot pick those
   dates.
3. **Schedules UI and storage.** Replace the PR #92 shells: save, edit,
   delete, enable, skip next, next-run and readiness states, and the cap.
   Proven by E2E.
4. **Mac host.** The resident evaluator, automatic Apply through the grant,
   the "setup required" path, and notifications. Proven E2E in a Tart clone:
   - an occurrence 2 minutes ahead starts with no prompt;
   - `observe --expect blocked`, then `allowed` after the end;
   - a relaunch and a login launch inside the interval catch up to the
     original end;
   - skip next and End early;
   - a revoked grant shows setup required and no prompt;
   - a peer VM receives the plan.
5. **iPhone host.** Device Activity registration, the extension's apply and
   clear, catch-up on launch, and notifications. Proven on the test iPhone
   with `observe-blocking-ios.json` at a scheduled start 15 or more minutes
   ahead.
6. **Setup integration.** The automatic-start consent flag in the unified
   setup and the upgrade offer.

Checks: `./gradlew quality` after the last correction, an independent
completed-change review, and /visual-pr descriptions with screenshots and
recordings.
