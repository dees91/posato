# PR #92: required Mac setup UI

- **Review:** Standard; UI routing and accepted product copy, with no new authorization mechanism.
- **Record path:** Recorded; changes the visible prerequisites for session and schedule creation.
- **Owners:** `ONBOARDING-004`, `SCHEDULE-001`, `SCHEDULE-002`.
- **Authorities:** [Product scope](../../product/schedules-and-mac-setup.md),
  [DESIGN.md](../../../DESIGN.md#release-12-setup-and-schedules), ADR 0004/0009.

## Outcome

Show helper setup as required for Mac blocking, and both Open Posato at login
and Start sessions without the password as required before creating a Mac
schedule. The maintainer accepted this correction on 2026-09-26.

## Boundaries

Keep schedule saving, execution and new permission controls inactive. Use
existing helper state and setup actions for the Session route. Do not add
automatic status polling, persistence, grants or scheduler behavior. Automatic
Apply still needs the separate accepted amendment owned by `SCHEDULE-001`.

## Acceptance

- Onboarding explains required helper setup and the consequence of deferral.
- Session shows a persistent setup notice and Finish setup when helper readiness
  is missing or unknown; the existing setup route remains accessible.
- Mac schedule creation leads to the prerequisite screen. Editor exploration
  is explicitly a preview and cannot save or bypass configuration.
- Product, design, roadmap and project mirror agree on the corrected prerequisites.

## Verification

Run `./gradlew quality`, independent completed-change review, and native UI
flows through `posato-control` in a Tart VM and on the test iPhone. Inspect
screenshots and replace the PR attachments. Record any device blocker precisely.
