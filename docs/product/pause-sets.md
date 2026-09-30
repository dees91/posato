# Release 1.3: pause sets

- **Status:** Accepted product scope; implementation pending
- **Accepted:** 2026-09-28; renamed 2026-09-29 and completed 2026-09-30 (`SCHEDULE-003`)
- **Decision owner:** Project maintainer
- **Provenance:** `user-confirmed`, release 1.3 planning and `SCHEDULE-003`
- **Owners:** `SCHEDULE-003` completes the decisions; `SCHEDULE-004` delivers

## Outcome

A person can reuse named **pause sets** in one-time manual sessions and
recurring schedules. Each set contains the websites and device-local
application choices that the person wants to pause. Names and purposes are
user-defined; work and leisure are examples rather than built-in categories.

`user-confirmed` (2026-09-29): the feature is called **Pause sets** ("Pause
set", "New set", "Set: Work"); the planned Polish term is "zestaw". It
replaces the working name "blocklist" in product, design, and roadmap text.

## Accepted scope

- Create and manage named pause sets. The same set can serve several
  schedules and manual sessions.
- Starting a manual session selects one set and a duration. Creating a
  schedule selects one set, weekdays, and start and end times.
- One set is the default offered for a new manual session or schedule. The
  person can choose another before starting or saving. Each session and
  saved schedule has one selected set.
- Existing paused items become the first, default set. Existing schedules
  use that set, preserving their configured blocking after upgrade.
- When schedules overlap, apply the union of their sets. Ending one
  occurrence removes only restrictions no other active occurrence requires.
  This describes an individual occurrence ending; it does not redefine the
  existing **End early** action that ends the combined pause.
- Set definitions and website selections synchronize through the existing
  iCloud workspace. Application choices remain device-local and are
  configured separately for each set. A shared set does not make an iPhone
  application selection portable to a Mac or another iPhone.

## Completed decisions

The questions this scope left open are decided in the
[pause set rules](pause-sets-decisions.md): live edits and deletion, overlap
with a manual session, the synchronized default, limits and readiness,
migration identity, synchronization, and older-app compatibility. The
product choices there are `user-confirmed`; the accepted
[ADR 0006 amendment](../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md#schedule-003-pause-set-operations-amendment)
(2026-09-30) defines the operations, and
[`DESIGN.md`](../../DESIGN.md#release-13-pause-sets) the screens.

The current [1.2 schedule rules](schedules-decisions.md) remain the shipped
behavior until `SCHEDULE-004` delivers pause sets. Release composition,
activation, and dependencies remain in the
[roadmap](../tasks/release-roadmap.md).
