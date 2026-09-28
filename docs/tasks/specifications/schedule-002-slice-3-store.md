# `SCHEDULE-002` slice 3: Schedules you can save, share and manage

- **Review tier:** `high-risk`
- **Tier reason:** A new schema migration, a new synchronized feature store with intents authored inside local transactions, and a reconciler inside every sync exchange.
- **Dependencies:** slice 1 (#98, operations 8-11), slice 2 (#97, the engine), `NOTIFY-001` (#94, the permission trigger), `ONBOARDING-004` (#95, Mac readiness).
- **Integration group:** `PR-SCHEDULE-DELIVERY`, milestone `1.2.0`.
- **Authority:** [schedule rules](../../product/schedules-decisions.md) (incl. "Integration contracts"), [`DESIGN.md`](../../../DESIGN.md#release-12-setup-and-schedules), [SCHEDULE-002 brief](schedule-002-shared-schedules.md).

## Outcome

On Mac and iPhone a person adds, edits, turns on or off, skips the next occurrence of, and deletes named weekday/time schedules. Plans work without iCloud, and in a linked workspace they reach the other devices. Each schedule shows its next run in concrete local terms. Nothing starts automatically yet: the Mac and iPhone hosts are slices 4 and 5.

## Boundaries

- **Local first.** The local store is the source for the UI and the future hosts; sync mirrors it through intents recorded in the same transaction, like paused items and sessions.
- **Linking.** On the first exchange after a workspace is linked, local plans and future facts are published once; after that the store shows the synchronized plans overlaid by any pending local change.
- **Cap and names.** At most 10 schedules; names 1-80 bytes as on the wire; the editor refuses what the wire rules refuse.
- **Readiness in this slice.** A Mac shows "Set up this Mac" until the unified setup is verified and offers Add schedule only then, as `DESIGN.md` requires (consent arrives in slice 6); an iPhone shows "Allow Screen Time" until authorized and saves plans either way.
- **Notices.** The first schedule saved on a device asks for notification permission once (integration contract 3); a device that only receives schedules shows "Turn on pause notices" while the permission is undetermined.
- **Non-goals:** starting, enforcing or catching up occurrences; End early; the Session screen (slices 4-6).

## Acceptance

- `AC-01` — Isolated tests written first: store transactions record exactly one intent per change when linked and none when not; seeding publishes local plans once per workspace; a pending intent is never undone by an older projection; a removal on another device wins over a local edit; capacity is reported; the 10 to 11 migration keeps existing data.
- `AC-02` — The iOS time-zone port resolves gaps and overlaps like the JVM port (common algorithm tested with the synthetic zone).
- `AC-03` — Mac (Tart): add, edit, turn off, skip next, delete, the cap, and persistence across relaunch; two linked VMs: a plan saved on one appears on the other.
- `AC-04` — iPhone (test iPhone, or the Simulator when the device is locked for automation): add, edit, skip next and delete.

## Verification

<!-- Unattended by default (AGENTS.md). -->

- `./gradlew quality`; E2E recipes `schedules-desktop.json` and `schedules-device.json` updated; screenshots and a recording in the PR; an independent plan review before code and a completed-change review.
