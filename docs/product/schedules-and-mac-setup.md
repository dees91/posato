# Release 1.2: schedules and Mac setup

- **Status:** Accepted product scope; implementation pending
- **Accepted:** 2026-09-26
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`, eight-question clarification of PR #92
- **Owners:** `ONBOARDING-004`, `SCHEDULE-001`, `SCHEDULE-002`

Release 1.2 delivers working recurring schedules on Mac and iPhone. Both the
schedule decision and its implementation belong to this release. The
[roadmap](../tasks/release-roadmap.md) owns ordering and release gates;
[`DESIGN.md`](../../DESIGN.md#release-12-setup-and-schedules) owns the UI.
The maintainer subsequently requested UI shells in PR #92, with saving and
execution inactive. That UI slice does not activate the schedule engine,
change permission grants or establish platform proof for automatic starts.

## Schedule behavior

- A person can create multiple named schedules. Each has selected weekdays,
  a start time, an end time, and an enabled state. Date ranges, a calendar of
  exceptions, and irregular recurrence are outside this first delivery.
- Devices linked through the existing iCloud workspace share the plan. Each
  device evaluates a previously synchronized plan locally, including while
  offline. Schedule changes still follow the existing best-effort sync
  contract; there is no promise of immediate arrival or remote wake.
- With the schedule enabled and the required consent and permissions in
  place, starting or waking the Mac during its interval starts restrictions
  for the remaining time. For a 09:00-11:00 interval and a 09:30 wake, the end
  remains 11:00. Show which schedule is running. An elapsed interval does not
  create a catch-up session after its end.
- **Skip next session** skips one upcoming occurrence without disabling the
  recurring plan. **End early** ends the current occurrence. Neither action
  allows that occurrence to restart on wake or application relaunch. The
  occurrence identity, persistence and cross-device convergence needed to
  preserve this result belong to `SCHEDULE-001`.
- On Mac, creating a schedule requires a ready background helper, enabled
  **Open Posato at login**, and **Start sessions without the password** with
  explicit authorization for automatic scheduled blocking. This supersedes
  the earlier permission to create Mac plans before local setup. On iPhone,
  the existing direction of saving a plan before local permission remains.
- A shared plan received from another device remains visible when this Mac
  lacks setup, with a direct route to the missing requirements. Other prepared
  devices may execute it. A saved or globally enabled plan alone is not proof
  of local readiness or active restrictions.

## Mac setup and consent

`user-confirmed` (2026-09-26, PR #92 follow-up): the background helper is
required to block websites and applications. The existing permission step
leads with **Enable blocking on this Mac** and explains that requirement.
Deferral keeps editing and synchronization available. Session then shows a
persistent setup notice and **Finish setup** instead of Start. Unknown helper
state asks for a check; it does not claim a missing permission. The setup action
opens the existing helper controls, and their returned state determines the
next screen. Do not repeatedly reopen a modal after **Not now**.

**Open Posato at login** and **Start sessions without the password** remain
independent, initially off settings. Both are required before creating a
schedule on a Mac. They remain optional for manual sessions with a ready
helper. Present them together under **Required for schedules**, separate from
the helper requirement. Continuing or deferring never grants consent.
Existing installations get one dismissible offer after updating; This Mac
keeps the settings available afterwards.

The Mac schedule entry leads to **Prepare this Mac for schedules** when any
requirement is missing or unknown. Check actual system and helper state before
allowing creation. A disabled **Continue to schedule** names what is missing;
there is no working save path around this step. The UI shell offers an
explicit **Preview schedule editor** route, with saving inactive, so the form
can still be reviewed before implementation.

Login launch starts Posato in the menu bar after sign-in, without opening its
window. Without it, a restart leaves Posato absent until manual launch. A
running host is technically able to evaluate schedules without login launch,
but the accepted product rule requires it for schedule readiness. Quitting
Posato prevents new Mac scheduled starts until it runs again; the quit flow
must disclose this under ADR 0009. If a required setting is later disabled or
revoked, show setup required on this Mac without deleting the shared plan or
claiming another device has stopped. Never request an administrator password
spontaneously when an occurrence is due.

Automatic scheduled enforcement requires explicit consent with a clear
explanation of startup and wake within a scheduled interval. The existing
`MACOS-014` grant covers person-initiated Start and Resume only. This product
decision does not silently broaden that grant or the daemon's authority.
`SCHEDULE-001` must obtain acceptance and independent security review of the
ADR 0004/0009 amendments before delivery. The choice of whether existing
grantees need additional authorization is part of that review. Missing
consent or permissions must lead to setup-required UI, not an unsolicited
administrator prompt at the scheduled time.

Do not offer password-requiring configuration during an active session.
Respect the existing guard while enforcement starts or changes. A hint can
wait until configuration is safe without interrupting the current session.

## Decisions still owned by SCHEDULE-001

Before `SCHEDULE-002` starts, settle and record:

- Time-zone ownership, travel, daylight-saving transitions, clock changes,
  and intervals that cross midnight.
- Overlapping schedules, conflicts with a manual session, and the paused
  items a scheduled occurrence uses.
- Occurrence identity and skip/early-end convergence when devices are
  offline, receive a late edit, or disagree. Already-known local skips must
  survive restart; an offline peer cannot know an undelivered change.
- The additive ADR 0006 vocabulary, older-client compatibility, local-only
  use, and iOS execution and recovery within Device Activity limits.
- The reviewed consent and revocation rules for automatic Mac Apply, and
  the local notification behavior with `NOTIFY-001` preferences and system
  permission. Notification delivery is not proof of enforcement.

These decisions refine the accepted outcome. A platform limitation that
would reduce it returns to the maintainer before changing the release scope.
