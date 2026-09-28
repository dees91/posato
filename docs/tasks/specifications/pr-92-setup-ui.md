# PR #92: unified Mac setup UI

- **Review:** Standard; UI routing and accepted product copy, with no new authorization mechanism.
- **Record path:** Recorded; changes the visible prerequisites for session and schedule creation.
- **Owners:** a maintainer-requested UI slice ahead of release 1.2 rows; `ONBOARDING-004` and `SCHEDULE-002` later replace its shells, and `SCHEDULE-001` decides the rules they wire.
- **Dependencies and integration:** after `MACOS-014`; its own pull request, #92.
- **Authorities:** [Product scope](../../product/schedules-and-mac-setup.md),
  [DESIGN.md](../../../DESIGN.md#release-12-setup-and-schedules), ADR 0004/0009.

## Outcome

Present one guided Mac setup for blocking, quiet login launch and starts
without repeated passwords. The maintainer accepted this simplified flow on
2026-09-26, replacing separate onboarding choices and schedule prerequisites.

## Boundaries

Keep the unified setup action, schedule saving and execution inactive. Retain
existing helper controls under disclosure and their current Session readiness
gate. Do not add orchestration, polling, persistence, grants or scheduler
behavior. Automatic Apply still needs the accepted security-reviewed amendment
owned by `SCHEDULE-001`. Do not promise one system prompt or fabricate readiness.

## Acceptance

- Onboarding, Session and Schedules use one explanation and setup action,
  without separate login/password switches in the primary flow.
- Deferral preserves editing and a persistent Finish setup route. Existing
  helper actions remain usable without claiming unified setup completion.
- Editor exploration is explicitly a preview; it cannot save or bypass setup.
- Product, design, roadmap and project mirror describe the same unified setup,
  its verification requirements and the UI-shell boundary.

## Verification

Run `./gradlew quality`, independent completed-change review, and native UI
flows through `posato-control` in a Tart VM and on the test iPhone. Inspect
screenshots and replace the PR attachments. Record any device blocker precisely.
