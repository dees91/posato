# Release 1.3: pause set rules (`SCHEDULE-003`)

- **Status:** Accepted. The product decisions are `user-confirmed`
  (2026-09-29 and 2026-09-30). The ADR 0006 amendment they need passed an
  independent security review after fixes and was accepted by the
  maintainer on 2026-09-30 (`user-confirmed`).
- **Owner:** `SCHEDULE-003` decides; `SCHEDULE-004` delivers.
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
  2026-09-30). Everything a part has paused on this device stays paused
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
  device upgrades keeps what it paused at that moment.
- **Device limits.** Everything all running parts pause together,
  including what they keep, must fit the device's limits (1,024 websites on
  the Mac, and the app limit on each device). An edit or a new part that
  would go beyond them pauses none of its new items and says they are not
  paused yet; what is already paused stays.
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
  this device has no app choices for the set, and says that apps are not
  chosen here, with **Choose apps** (`user-confirmed` 2026-09-29).
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
  README, on `posato.app`, or on the limits page.

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

## Execution

### iPhone

- The App Group table becomes version 2: sets with their domains and app
  tokens, each schedule's set, and the running parts with what each has
  paused. The extension applies, at `intervalDidStart`, the union of the
  running occurrences' sets plus what they already hold, and, at
  `intervalDidEnd`, keeps what another running part still needs. An
  unreadable or version-1 table clears, as today.
- The manual session keeps its own named store and the schedules share
  theirs; iOS combines named stores. `hypothesis`: the system union of the
  two stores is the most restrictive of both; `SCHEDULE-004` verifies it on
  the test iPhone with different sets in each.
- App choices move from one file to one file per set, with the existing
  64-token ceiling applying to unique tokens across sets on the device.
  `open`: whether a union that large fits the system shield limit, and
  the website limit of the web content filter; `SCHEDULE-004` measures
  both before choosing lower caps.

### Mac

- Today's helper holds one configuration (`observed`, `PauseClaims`): a
  joining claim either keeps the held request or replaces it, and nothing
  forms a union. `SCHEDULE-004` makes the app compose one
  request from all parts: the union of their sets' domains and apps, their
  retained items, and the latest end. A part ending recomposes it.
- `observed`: a change is a helper clear followed by an apply, inside one
  app lock. Sets make such changes more frequent (additions, part ends).
  `open`: whether the helper leaves a measurable unblocked moment between
  the two; `SCHEDULE-004` measures it in a Tart VM, and a gap becomes an
  ADR 0004 question rather than a silent change.
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
   linked workspace. Migration tests seeded from databases that a released
   1.2 build produced with synthetic content in a Tart VM or on the test
   iPhone, including a running session and schedule pins.
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
