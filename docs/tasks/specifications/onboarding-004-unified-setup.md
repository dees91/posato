# `ONBOARDING-004`: One guided Mac setup

- **Review tier:** `high-risk`
- **Tier reason:** The setup action drives the helper registration, background approval, the login item, and the `MACOS-014` administrator grant in one flow, and it gates the manual Start.
- **Dependencies:** `MACOS-014` (merged), PR #92 (shells and accepted design), `NOTIFY-001` (PR #94, base; serialized because both touch onboarding and setup).
- **Integration group:** `PR-MAC-SETUP`, milestone `1.2.0`.
- **Authority:**
  - [product scope](../../product/schedules-and-mac-setup.md);
  - [`DESIGN.md`](../../../DESIGN.md#release-12-setup-and-schedules);
  - [ADR 0004](../../decisions/0004-macos-helper-ownership-and-lifecycle.md): the unified setup note and the `MACOS-014` amendment;
  - [ADR 0009](../../decisions/0009-macos-menu-bar-presence.md): the unified setup amendment.

## Outcome

One **Set up Posato** action on the Mac makes Posato ready, with no separate choices to find:
- blocking works: the helper is enabled and approved;
- Posato opens quietly at login;
- manual starts need no repeated password.

The same action appears in onboarding, in Session's **Finish setup**, in Schedules, and in a one-time dismissible offer to existing users. It skips finished steps, resumes after interruption, names the one thing the person has to do, and shows **This Mac is ready** only after it has verified every result.

## Boundaries

- **Existing rules.** The action reuses the existing helper calls, `SMAppService.mainApp`, and the `MACOS-014` grant. It adds no new privilege, and its grant still covers only the person's own Start and Resume. Automatic starts wait for `SCHEDULE-002` after the `SCHEDULE-001` review.
- **Start gate.** Only blocking readiness gates the manual Start. A missing login item or grant leaves Start available; Session shows the one-time offer, and This Mac shows **Set up Posato**.
- **During a session.** Nothing that needs a password runs while a session is active, starting, or changing enforcement. The action is disabled, with a caption.
- **Never unprompted.** No setup step starts without the person pressing the action.
- **This Mac.** It keeps advanced status, the two switches for revocation, and **Remove from this Mac**.
- **Non-goals:** schedule readiness, notification settings (`NOTIFY-001`), and iOS.

## Acceptance

- `AC-01` — On a fresh Mac, onboarding's **Set up Posato** leads through background approval and the administrator prompt to **This Mac is ready**. Afterwards a pause starts with no password and blocks.
- `AC-02` — An interrupted setup resumes only the missing step. Declined approval or a cancelled password leaves an honest state and a **Try again** action.
- `AC-03` — On an existing install whose helper is ready but not the rest, one offer appears once, and dismissing it is remembered. **Set up Posato** from the offer completes the missing steps.
- `AC-04` — During a session the action is disabled.
- `AC-05` — Isolated tests, written failing first, cover:
  - the step order and skipping;
  - the approval wait and its bound;
  - the session guard;
  - verified completion;
  - offer visibility and its persistence rule.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac. -->

- **E2E in fresh Tart clones:**
  - onboarding setup;
  - resume after interruption;
  - upgrade offer and dismissal;
  - start with no password after setup;
  - screenshots and a recording.
- `./gradlew quality` after the last correction, an independent plan review, and a completed-change review.
