# Execution: `DOCS-004`

- **Brief:** [Prepare the public packaging for Posato 1.3](../specifications/docs-004-release-1-3-media.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude
- **Branch:** `docs/docs-004-release-1-3-media`

## Plan

1. Text without devices: README, `posato.app`, the App Store subtitle,
   description, and What's New for 1.3, the `PRIVACY.md` pause set edits, and
   the GitHub release notes.
2. Recapture the showcase states on the 1.3 applications with two synthetic
   pause sets, `Focus` (the default) and `Evening`: the Mac in a Tart VM, the
   iPhone on the test iPhone, both in Dark Mode, without the maintainer.
3. Storyboard revision 4 with the **Pause sets** destination and the set
   choice, `npm run media`, and a contact sheet review.
4. Recapture the App Store screenshots on the Simulators.
5. Independent completed-change review, then closeout.

## Decisions

- **Subtitle** (`user-confirmed`, 2026-10-02): "Space for what matters.",
  because the product line has 33 characters and the subtitle allows 30.
