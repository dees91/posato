# `DESIGN-001`: Adopt the native prototype design in the MVP

- **Review tier:** `high-risk`
- **Tier reason:** Native window code enters the signed desktop package; batch input and automatic application-policy activation cross persistence boundaries.
- **Dependencies:** `SESSION-001`, `TARGETS-001`–`TARGETS-005`, `MACOS-006`, `QUALITY-002`; prototype reference `c879ff7`.
- **Integration group:** One design-adoption PR, including the `QUALITY-003` desktop scrolling outcome.
- **Authority:** Maintainer-approved implementation plan, 2026-09-07.

## Outcome

The real MVP presents Session and Paused items with the approved prototype's
design system and interactions on iPhone and Mac, backed by existing services
and ViewModels, and remains verifiable through the maintained native driver.

## Boundaries

- Adopt the product components in the existing shared design-system package;
  freeze the prototype implementation at `c879ff7` without an MVP dependency.
- Preserve real session, availability, authorization, and persistence outcomes;
  do not integrate onboarding, synchronization, enforcement, or inspection controls.
- Support bounded domain/HTTP(S)-URL batches and automatic creation of the
  singleton application group after a successful nonempty native selection.
- Preserve stored names, schemas, identifiers, local choices on partial failure,
  and existing editing availability during active sessions.
- Update the driver, scenario fixtures, feature recipes, and `verify-posato` skill.

## Acceptance

- `AC-01` — Shared product components and both native shells reproduce the reference design, including text scaling, keyboard behavior, and real failure states.
- `AC-02` — Session setup, review, start, early end, expiry, summaries, and restart retain their real contracts with the new UI.
- `AC-03` — Batch entry, filtering, retained drafts, menus, and automatic app-group creation preserve data through cancellation, conflicts, and partial failure.
- `AC-04` — Updated native scenarios prove the changed flows, including long lists and real desktop scrolling, without product test hooks.
- `AC-05` — The signed desktop package launches; applicable tests, native checks, completed-change review, and design/verification authorities agree.

## Verification

- Focused common and driver regression tests, aggregate quality, iOS builds,
  signed desktop packaging, and native runs on Mac, Simulator, and iPhone.
- Matched visual inspection against the frozen prototype; evidence stays ignored.

## Decisions or blockers

- The maintainer reviews and approves this plan instead of a separate agent
  plan review. This task-specific exception does not waive independent review
  of the completed change or change the repository-wide workflow.
