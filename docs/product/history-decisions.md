# A calm history of scheduled pauses (`HISTORY-001`)

This page records the product and privacy decision for idea 47: a quiet
calendar of days, in the spirit of a contribution graph, that shows which days
a person's scheduled pauses ran as planned. It proposes the `DESIGN.md`
section and the `PRIVACY.md` text, and plans the delivery row `HISTORY-002`.
Nothing here is product code; the [schedule rules](schedules-decisions.md)
own occurrences, skips, End early, and breaks.

The owner answered the first questions on 2026-10-30 and accepted the rest
of this page, the visual form, the local day record, and the privacy text, on
2026-11-03 in PR #219.

## Decisions

### What a day records

- `user-confirmed` (2026-10-30, the owner's wording): a day is kept as
  planned "when every scheduled occurrence that day ran to its end without
  End early"; "a `SCHEDULE-007` break does not spoil it, skipped occurrences
  do not count, and a day without a plan is neutral rather than empty". A
  day with only skipped occurrences is neutral too: it shows as a day
  without a scheduled pause, never as a gap or a failure.
- `user-confirmed` (2026-11-03): a day is **ended early** when at least one
  of its occurrences was ended early, on this device or on another device
  whose End early has reached this one, whenever it arrives. These are the
  only two recorded outcomes; a neutral day records nothing.
- `user-confirmed` (2026-11-03): an occurrence belongs to the local date it starts on,
  as the schedule weekdays do. Friday 22:00 to Saturday 06:00 is Friday.
- `user-confirmed` (2026-11-03): editing, turning off, or deleting a schedule is a change
  of plan, not an outcome. An occurrence that a plan change stopped does not
  count, so a day whose only occurrence was stopped that way stays neutral.
- `user-confirmed` (2026-11-03): the history follows the plan and the person's choices,
  not proof of enforcement. "Ran as planned" means that nobody ended the
  pause early; it does not claim that every website and app was blocked the
  whole time, which Session reports as before. A device that was switched
  off during an occurrence still records the day by its plan and facts.
- Never recorded (`user-confirmed`, the brief and `PRIVACY.md`): websites
  visited or opened in a window, and blocked attempts.
- Not recorded either (`user-confirmed` (2026-11-03)): apps opened, manual sessions,
  durations, times of day, or any count. Manual sessions stay out because
  the owner's definition of a kept day names only scheduled occurrences.

### When a day is written

- `observed` (current code): the facts a day needs do not stay long enough
  to derive a history from them later. Skip and End early facts stay until
  their schedule is deleted, and then they go with it. A natural end that a
  device observed is forgotten after about a day (`ScheduleHostPolicy`,
  `forgotten`). The schedule table holds only the current plan, so a past
  day cannot be read back after an edit.
- `user-confirmed` (2026-11-03): each device therefore keeps one small local row per
  day, written from the existing schedule and occurrence facts. The row is
  never synchronized, so the `user-confirmed` boundary holds: no new
  synchronized record and no ADR 0006 amendment.
- `user-confirmed` (2026-11-03): **ended early** is written at once, for the
  occurrence's start date, when End early is authored on this device or
  received from another, even for a day already written. That keeps the fact
  even when its schedule is deleted later the same day, and it lets an End
  early that arrives late still count. A day's outcome can change only from
  ran as planned to ended early, never back. It is written only for a date
  that ran as planned could also reach: a date that already has a row, or a
  date after the last final date and within the 7-day catch-up bound below.
  An End early for an older date, before the history began, or for a day
  removed by **Clear history** is ignored, so the history never shows an
  ended-early ring in weeks whose kept days it could not record.
- `user-confirmed` (2026-11-03): **ran as planned** is written when the day is final:
  its local date has passed and every occurrence that started on it has
  ended or been skipped, so Friday 22:00 to Saturday 06:00 is final on
  Saturday at 06:00. Today is never final, because an edit or a new schedule
  can still run later the same day. The check runs whenever Posato already
  evaluates schedules: on start, on wake or return to the foreground, after
  sync applies changes, and once a minute while a desktop application runs.
  After that, an edit or deletion never rewrites the day; only a late End
  early does, as above.
- `user-confirmed` (2026-11-03): when Posato did not run for a while, the first check
  catches up at most 7 days back, using the plan and facts as they are then.
  Older days stay neutral. If a plan changed on another device while this
  one was off, those caught-up days follow the changed plan; this limit is
  stated, not hidden. Catching up follows the device's current time zone,
  as schedules do.
- `user-confirmed` (2026-11-03): the history keeps the last 53 weeks and deletes older
  rows during the check.
- `user-confirmed` (2026-11-03): **Clear history** deletes the rows but keeps the last
  final date, so the next check does not catch the cleared days up again.
  Only days that become final after the clear, and End early facts for them,
  are written.

### Where it lives

- `user-confirmed` (2026-10-30): on each device only. Devices can show
  different histories, for example when one was off for more than a week or
  sits in another time zone.
- `user-confirmed` (2026-11-03): the history is not part of an export file, which
  already never holds history. Turning sync off or **Remove workspace** does
  not delete it: like the device's own schedules, it is local data, not the
  synchronized change history. **Clear history** deletes it, and so does
  removing Posato's data from the device. It can be included in device
  backups like other Posato data, except on Android, which excludes them.
  Windows follows whatever the Windows privacy text of release 1.6 says for
  the rest of Posato's data; `HISTORY-002` uses that text.

### Where it sits and how it is turned off

- `user-confirmed` (2026-10-30): in **Schedules**, below the list, with no
  new tab or screen. It is visible by default and can be hidden and
  cleared.
- `user-confirmed` (2026-11-03): the section is a disclosure row titled **History**,
  open by default. Collapsing it hides the calendar, and Posato remembers
  that on the device; the collapsed **History** row stays, so hiding means
  collapsing, not removing the row. Hiding changes only what is shown; the days are still
  written, so opening it again shows them. **Clear history** at the end of the
  open section deletes the recorded days after a confirmation.
- `user-confirmed` (2026-11-03): the section shows whenever there is a schedule or a
  recorded day. With neither, it is absent, as the empty state already
  explains recurring pauses. After the last schedule is deleted, the
  recorded days and **Clear history** therefore stay reachable.

### The visual form

`user-confirmed` (2026-11-03), the brief's open item:

- **Grid.** Seven rows, the device's first weekday at the top (Monday in
  the sketch), and one column per week, the current week at
  the right. As many recent weeks show as fit the width, up to 53; there is
  no horizontal scroll. An iPhone in portrait shows about 13 weeks, a
  desktop window more.
- **Three day shapes, not only colors.** Ran as planned: a filled rounded
  square in `primary`. Ended early: the same square as a thin ring in
  `onSurfaceVariant`, empty. No scheduled pause, days still to come, and
  days before the history began: a small `outline` dot, the grid's quiet
  background. No red, no `tertiary` caution color,
  no cross, and no badge.
- **Today.** Today stays a dot until the day is over and final; then it takes its
  shape. Nothing on the screen invites finishing today.
- **No totals.** No count, percentage, streak, best run, comparison,
  progress bar, celebration, animation, or notification. Nothing announces
  a new day.
- **Words.** One caption and a three-item legend; short weekday names on
  the rows (Mon, Wed, Fri, as the device's locale abbreviates them) and short
  month names above the columns where a month starts.

Rejected forms, kept for the record: a running count of kept days (a score),
a weekly percentage (a score), a current or longest run (a streak), a red or
amber mark for an ended day (shame), and a separate History tab (the owner
chose Schedules).

## `DESIGN.md` section

Accepted on 2026-11-03 and added to `DESIGN.md` under "Current Screens
and Interactions" as "Calm history of scheduled pauses", pending delivery by
`HISTORY-002`:

> - **Where.** Schedules, below the list, as a disclosure row **History**,
>   open by default and remembered on the device when collapsed. Shown
>   while there is a schedule or a recorded day.
> - **Grid.** Seven weekday rows from the device's first weekday and one
>   column per week, the current week at the right, as many weeks as fit
>   (up to 53), without horizontal scroll. Short weekday names on the rows
>   and short month names above the column where a month starts, as the
>   device's locale abbreviates them.
> - **Days.** Ran as planned: a filled rounded square in `primary`. Ended
>   early: an empty square with a thin `onSurfaceVariant` ring. No scheduled
>   pause, a day still to come, or before the history began: a small
>   `outline` dot. Shape carries the state, so color is never the only cue.
>   Today always stays a dot; a day takes its shape after it is over and
>   its scheduled pauses have ended.
> - **Copy.** Caption: "Days your scheduled pauses ran as planned on this
>   device." Legend: "Ran as planned", "Ended early", "No scheduled pause".
>   Before any day is recorded: "Days show here after your scheduled pauses
>   end."
> - **Clear.** A quiet text action **Clear history** at the end of the open
>   section; confirmation title "Clear history?", body "Posato removes the
>   days recorded on this device. Your schedules don't change.", actions
>   **Clear** (destructive) and **Cancel**.
> - **Never.** No counts, percentages, streaks, best runs, comparisons,
>   progress, celebrations, animation, or notifications; no warning color or
>   mark for an ended day.
> - **Accessibility.** Each week column is one element, labelled with its
>   week and its recorded days, for example "Week of 26 October: ran as
>   planned Monday and Tuesday, ended early Thursday"; a week with no
>   recorded day reads "Week of 26 October: no scheduled pause". The filled
>   square and the ring meet the 3:1 non-text contrast on the surface in both
>   appearances (`primary` 6.7:1 and 8.2:1, `onSurfaceVariant` 5.0:1 and
>   7.8:1); the dot is background, and the week label carries its meaning.
>   Under Increase Contrast the ring and the dot use `onSurface`. The grid
>   has no action: a week column is a non-interactive accessibility
>   element, so the 44-point target rule does not apply to it.
>
> Reuse the disclosure row, caption, text action, and confirmation
> components; the day square is the only new drawing, built from existing
> tokens.

The sketch below shows an iPhone in portrait with two schedules.

```text
Schedules
 ┌──────────────────────────────────────────┐
 │ Morning   Mon–Fri 07:00–09:00   [on]     │
 │ Evening   Every day 21:00–23:00 [on]     │
 └──────────────────────────────────────────┘
 History                                   ⌄
   Days your scheduled pauses ran as
   planned on this device.
        S        O            N
   Mon  ■ ■ ■ □ ■ ■ ■ ■ ■ ■ ■ ■ ·
   Tue  ■ ■ ■ ■ ■ ■ □ ■ ■ ■ ■ ■ ·
   Wed  ■ ■ ■ ■ ■ ■ ■ ■ ■ · ■ ■ ·
   Thu  ■ ■ ■ ■ □ ■ ■ ■ ■ ■ ■ ■ ·
   Fri  ■ ■ ■ ■ ■ ■ ■ ■ ■ ■ ■ ■ ·
   Sat  · ■ ■ ■ ■ ■ ■ · ■ ■ ■ ■ ·
   Sun  ■ ■ □ ■ ■ ■ ■ ■ ■ ■ ■ ■ ·
   ■ Ran as planned  □ Ended early  · No scheduled pause
   Clear history
```

## `PRIVACY.md` text

Accepted on 2026-11-03; applied by `HISTORY-002`, when the history ships, because `PRIVACY.md`
describes the shipped product:

- In "Data Posato keeps on your devices", after the schedules item:

  > - a history of your scheduled pauses: for each day of the last year
  >   that had one, whether they ran as planned or one was ended early.
  >   Each device works it out from your schedules and keeps its own; it is
  >   never synchronized or exported, and **Clear history** in Schedules
  >   deletes it;

- The closing sentence of that section becomes:

  > Posato does not collect browsing history, the pages you visit, which
  > pages were blocked, how often you open apps, usage scores, or any other
  > activity record. The history of scheduled pauses above stays on the
  > device and holds no websites, apps, or times.

- Privacy manifest and App Store label: `inferred`, no change. The data
  never leaves the device, so it is not "collected" in Apple's sense, and no
  new required-reason API is used. `HISTORY-002` rechecks this against the
  manifests from `PRIVACY-001`.

## Delivery plan (`HISTORY-002`)

- **Platforms.** All five, from the shared Compose Schedules screen: Mac,
  iPhone and iPad, Linux, Android, and Windows.
- **Data.** A local SQLDelight table of day rows (date and one of two
  outcomes) and a single row holding the last final date, with a migration.
  Nothing is added to sync intents, operation kinds, export, or diagnostics.
- **Derivation.** A pure function in the schedules domain takes the plans,
  the skip, end, and break facts, the zone, the current time, and the last
  final date, and returns the new rows and the new last final date. It runs
  inside the existing schedule evaluation points and deletes rows older than
  53 weeks.
- **Interface.** The disclosure section, grid, legend, confirmation, and
  accessibility labels above, with the copy translated into every shipped
  language in the same commit.
- **Tests, failing first.** With an injected clock and zone: ran as planned;
  ended early here and by a received fact; a break still ran as planned; all
  skipped stays neutral; an occurrence crossing midnight is final the next
  morning and belongs to its start day; a daylight-saving day; an edit or a
  deletion after the day leaves its row; the 7-day catch-up bound; the 53-week
  retention; Clear history, and no catch-up of the cleared days afterwards;
  the hidden section still writes rows; End early followed by the same-day
  deletion of its schedule; an End early received after the day was written;
  an End early received for a day older than the catch-up bound, before the
  history began, or cleared, which writes nothing;
  today never final, even after its only occurrence ended; a time-zone change
  between two occurrences; a clock rollback after a day was written.
- **Tooling.** `posato-control` reads the history section through the
  accessibility tree (a `flow history` read), and the `verify-posato` feature
  map and scenarios are updated in the same pull request.
- **Device verification.** Under the proportionate rule, one representative
  real-time run per target: a 15-minute schedule ends naturally and, once
  the day is over, the day's square shows **Ran as planned**. In Tart guests
  (Mac, Linux, Windows) the guest clock is moved past midnight after the
  occurrence; on the test iPhone and the Android phone the schedule crosses
  midnight (23:50 to 00:05), so the check after 00:05 shows the previous
  day. On the Mac, also **Clear history**. The ended-early, received-fact,
  and boundary cases are proved by the tests above.
- **Privacy.** The `PRIVACY.md` text above, adjusted to the Windows text
  that release 1.6 ships, and the manifest recheck.
- **Review tier.** High-risk, with a plan review: it adds a behavioral
  record on the device and changes `PRIVACY.md`, which `docs/tasks/README.md`
  treats as sensitive data, although it changes no synchronization or
  enforcement.
- **Row.** `HISTORY-002` in the roadmap backlog, assigned at the next
  release composition.
