# `SCHEDULE-001`: Decide shared recurring schedules and automatic Mac starts

- **Review tier:** `high-risk`
- **Tier reason:** It proposes ADR 0004 and ADR 0009 amendments that let the resident Mac application apply restrictions without a person's action, and an ADR 0006 amendment to the synchronized operation format.
- **Dependencies:** `MACOS-013`, `MACOS-014`, `ONBOARDING-004` (the unified setup direction from PR #92).
- **Integration group:** `PR-SCHEDULE-DECISION`, stacked on PR #92.
- **Authority:** [release roadmap](../release-roadmap.md) row `SCHEDULE-001`; [product scope](../../product/schedules-and-mac-setup.md); the maintainer's delegated night mandate of 2026-09-26.

## Outcome

Every rule that `SCHEDULE-002` needs is decided, the ADR amendments for schedule operations and automatic Mac starts are written for security review and acceptance, and an implementation plan exists.

## Boundaries

- Settle time zones, daylight saving, clock changes, intervals crossing midnight, overlaps, manual-session conflicts, paused items, occurrence identity, skip and early-end convergence, operation compatibility, iPhone Device Activity execution, the resident Mac host, and notifications.
- Propose, do not accept: ADR 0004, ADR 0006, and ADR 0009 amendments. The maintainer accepts them after an independent security review.
- Update `PRIVACY.md` for synchronized schedules and name any privacy-manifest impact.
- Non-goals: product code, exceptions calendars, date ranges, iPad-specific layouts.

## Acceptance

- `AC-01` — Each item in the product scope's "Decisions still owned by SCHEDULE-001" has a recorded decision with provenance.
- `AC-02` — The automatic Mac Apply amendment names what may apply, when, what the daemon still checks, the threat-model delta, residuals, revocation, and the migration of `MACOS-014` grantees.
- `AC-03` — An independent security review of the amendments passes.
- `AC-04` — `SCHEDULE-002` has a sliced implementation plan with its isolated test seams and E2E verification.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- Documents only: the independent security review of the amendments, and a consistency read against the product scope, `DESIGN.md`, and ADR 0002, 0004, 0006, 0009.

## Decisions or blockers

- `user-confirmed` (2026-09-26, delegated night mandate): the rules in [schedule rules](../../product/schedules-decisions.md).
- The maintainer's acceptance of the proposed ADR amendments.
