# Execution: `DOCS-002`

- **Brief:** [docs-002-showcase-media.md](../specifications/docs-002-showcase-media.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude Code agent
- **Reviewer:** pending until assigned
- **Branch:** `feature/docs-002-showcase-media`
- **Updated:** 2026-09-16

## Plan

1. Rebuild `video/` around a data-first storyboard with a test, device-frame,
   pointer, tap-ring, scene, and typography components ported from the
   maintainer's `skill-manager` promo and recolored with `DESIGN.md` tokens;
   remove the recording-based end scene and its media dependency.
2. Capture the missing Mac and iPhone states through posato-control at one
   product revision, reduce them, and check them for synthetic data only.
3. Review the storyboard, README, and site copy with the `clarity` skill,
   then calibrate pointer targets in Remotion Studio.
4. Add the export scripts (GIF ladder, site MP4 and poster, attachment,
   stills, verify) and render everything with `npm run media`.
5. Update the README, `video/README.md`, `STORYBOARD.md`, the site hero,
   the CSP, `DESIGN.md`, and take Lighthouse baselines before and after.

## Result

- Pending.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | pending | pending |

## Blockers and accepted risks

- Maintainer hand-offs listed in the brief: administrator prompt, physical
  iPhone captures, attachment upload, `DESIGN.md` amendment and preview review.

## Final

- **Status:** `active`
- **Outcome:** pending
