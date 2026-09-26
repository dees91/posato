# Release 1.2: schedule rules (`SCHEDULE-001`)

- **Status:** Proposed by `SCHEDULE-001`. The product decisions are
  `user-confirmed` (2026-09-26, delegated night mandate). The ADR amendments
  they need are `proposed` until an independent security review passes and
  the maintainer accepts them.
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
  hours. The 15-minute floor matches the Device Activity minimum that Posato
  already uses on iPhone (`observed`, `SuspendedExpiryActivity.minimumInterval`
  = 15 minutes). The 24-hour ceiling matches the ADR 0006 session bound.
- **Daylight saving.** A start time that does not exist on the day of a
  spring-forward change starts at the first valid minute after it. A start
  time that happens twice on a fall-back day starts at its first instance.
  The end is the stated wall-clock end on the end day, so an occurrence can
  be an hour shorter or longer on those two days.
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
- **Editing a schedule** that is running ends nothing by itself. The running
  occurrence follows the edited end time. Turning a schedule off or deleting
  it ends its running occurrence at once.

### Synchronized operations (ADR 0006 amendment)

Four additive format-1 operation kinds, proposed in the ADR 0006 amendment:

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
  no prompt (proposed ADR 0004 and ADR 0009 amendments). If the grant is
  missing, refused, or the setup is incomplete, Posato never shows a password
  dialog. It shows "Setup required on this Mac" in Session and Schedules, and
  posts one notification: "A scheduled pause couldn't start on this Mac.
  Open Posato to finish setup."

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
  shown in its caption. It is a local flag, stored with the grant state.
- A Mac that opted in through `MACOS-014` before the schedule wording has the
  grant but not the automatic-start consent. It sees the dismissible upgrade
  offer once. Accepting it records the consent and needs no second
  administrator password, because the grant itself is unchanged. Until then,
  its schedules show "Setup required on this Mac".
- Revoking the grant in This Mac also stops automatic starts.

## Implementation plan for `SCHEDULE-002`

Slices, each on its own branch, stacked:

1. **Model and sync.** Kinds 8-11 and the optional-kind rule in the codec
   and reducer, with cross-language golden vectors. These are isolated
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
