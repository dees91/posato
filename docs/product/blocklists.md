# Release 1.3: blocklists

- **Status:** Accepted product scope; implementation pending
- **Accepted:** 2026-09-28
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`, release 1.3 planning
- **Owners:** `SCHEDULE-003` completes the decisions; `SCHEDULE-004` delivers

## Outcome

A person can reuse named blocklists in one-time manual sessions and recurring
schedules. Each list contains the websites and device-local application choices
that the person wants to block. Names and purposes are user-defined; work and
leisure are examples rather than built-in categories.

## Accepted scope

- Create and manage named blocklists. The same list can serve several
  schedules and manual sessions.
- Starting a manual session selects one blocklist and a duration. Creating
  a schedule selects one blocklist, weekdays, and start and end times.
- One blocklist is the default offered for a new manual session or schedule.
  The person can choose another before starting or saving. Each session and
  saved schedule has one selected blocklist.
- Existing paused items become the first, default blocklist. Existing
  schedules use that list, preserving their configured blocking after upgrade.
- When schedules overlap, apply the union of their blocklists. Ending one
  occurrence removes only restrictions no other active occurrence requires.
  This describes an individual occurrence ending; it does not redefine the
  existing **End early** action that ends the combined pause.
- Blocklist definitions and website selections synchronize through the
  existing iCloud workspace. Application choices remain device-local and
  are configured separately for each blocklist. A shared list does not make
  an iPhone application selection portable to a Mac or another iPhone.

## Decisions required before delivery

`SCHEDULE-003` must settle these questions with the maintainer and update
`DESIGN.md` and the affected product and architecture authorities before
`SCHEDULE-004` implements them:

- Whether edits to a list affect a running pause immediately, and how to
  handle deleting or replacing a list referenced by a schedule or session.
- How a scheduled occurrence combines with an already-running manual
  session using a different list, including received sessions and early end.
- Whether the default choice is local or synchronized; list limits, empty
  selections, and readiness when application choices are missing locally.
- Migration across linked devices, identities and convergence of shared
  lists, session and schedule references, and compatibility with older apps.
  Preserve saved targets, local application choices, and existing schedules.

The current [1.2 schedule rules](schedules-decisions.md) remain the shipped
behavior. This scope accepts the blocklist direction and schedule overlap
rule for 1.3; it does not establish a new wire format or completed platform
support. Release composition, activation, and dependencies remain in the
[roadmap](../tasks/release-roadmap.md).
