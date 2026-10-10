# Execution: `DOCS-005`

- **Brief:** [Prepare the public packaging for Posato 1.4](../specifications/docs-005-release-1-4-media.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Branch:** `task/docs-005-release-1-4-media`

## Plan

1. Text without devices: README, `posato.app`, the limits page Availability,
   the App Store description and What's New for 1.4, and the GitHub release
   notes.
2. Recapture the showcase states on the 1.4 tree with the `DOCS-004` fixture
   (`Focus` and `Evening`, `Deep work` and `Evening reading`): the Mac in a
   Tart VM, the iPhone on the test iPhone, both in Dark Mode, and adapt the
   capture scripts to the `DESIGN-004` interface.
3. Recalibrate the storyboard targets, `npm run media`, `npm run
   compare:hero`, and a contact sheet review.
4. Recapture the App Store screenshots on the Simulators.
5. Independent completed-change review, then closeout.

## Decisions

Decided by the agent under the maintainer's delegation of 2026-10-09 unless
marked otherwise.

- The `MACOS-026` one-time limit goes into the GitHub release notes, which
  Mac users read, and not into the App Store What's New: the iPhone app has
  no quit question.
- What's New does not repeat the advice to update every device: 1.4 keeps the
  1.3 sync format (ADR 0006 unchanged since `v1.3.0`), so 1.3 and 1.4 devices
  keep syncing.
- The `Evening` fixture set holds Chess from the start, so the schedule list
  shows no missing-apps notice.
