# `SCHEDULE-002` slice 6: Consent to automatic starts on a Mac

- **Review tier:** `high-risk`
- **Tier reason:** It adds the local authorization that lets the Mac host (slice 4) start restrictions without a person present, and changes when a Mac counts as ready for schedules.
- **Dependencies:** slice 3 (#99, the Schedules screen and its readiness card), `ONBOARDING-004` (#95, the unified setup and the standing grant).
- **Integration group:** `PR-SCHEDULE-DELIVERY`, milestone `1.2.0`. It runs before slices 4 and 5 so that both hosts read one readiness result.
- **Authority:** [schedule rules](../../product/schedules-decisions.md), "Setup and migration" and integration contracts 1 and 5; [`DESIGN.md`](../../../DESIGN.md#release-12-setup-and-schedules).

## Outcome

A Mac records, as its own local value, that the person agreed to automatic starts, including schedules added on their other devices. Pressing **Set up Posato** records it, because the setup caption says so. A Mac whose grant was turned on only through This Mac's switch sees one card in Schedules, **Allow schedules to start on this Mac**, whose single action records it without a password. The consent counts only while the grant is confirmed and is cleared by turning the grant off, removing the helper, or a read that finds the grant absent or unknown. Schedules on a Mac offer **Add schedule** only when the Mac is ready for schedules, and copy that promised nothing starts "until you start a session" now names schedules too.

## Boundaries

- **Two results.** "Ready for pauses" stays the `ONBOARDING-004` result and keeps driving Session and This Mac. "Ready for schedules" is that result plus the consent; Schedules and the slice 4 host read it.
- **No password for consent.** Recording or clearing the consent never calls the helper.
- **Storage.** A versioned value in this Mac's user defaults, next to the setup-offer marker but independent of it (`automaticStartConsentV1`; a new wording uses a new key). Dismissing the offer never records it.
- **iPhone.** Screen Time authorization remains the consent; nothing changes there.
- **Non-goals:** starting anything (slice 4); the Quit confirmation (slice 4); release notes (RELEASE-004).

## Acceptance

- `AC-01` — Isolated tests: **Set up Posato** records the consent; ready for schedules needs setup complete and the consent; a grant read as off, unknown or unsupported clears it, as do the grant switch turned off and Remove; the Schedules card records it without calling the helper; a dismissed offer does not record it.
- `AC-02` — Mac (Tart): a fresh setup from Schedules offers **Add schedule** directly; turning the grant off and on in This Mac makes Schedules show the card, whose action restores **Add schedule** without a password dialog.
- `AC-03` — The onboarding, This Mac and setup copy says that pauses start when the person starts one or a schedule begins, and that setup creates no schedule.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac. -->

- `./gradlew quality`; the E2E above with screenshots; an independent plan review before code and a completed-change review.
