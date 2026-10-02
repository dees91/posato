# WEB-002 execution

- **Status:** done; ready for review

## Plan

1. Start from the homepage experiment in PR #126, rebased on `main`.
2. Have the maintainer choose the line and the layout (`AC-01`), then fix
   the phone's first screen (`AC-02`) and replace the line everywhere
   (`AC-04`).
3. Run the clarity review, an independent completed-change review, and the
   checks below.

## Result

- `AC-01`: on 2026-10-02 the maintainer chose "A little space. For what
  matters." and the experiment's layout.
- `AC-02`: at 375 x 667 the poster ended below the fold (top at 587 px). On
  screens up to 700 px it now follows the lead, ending at 598 px; at
  390 x 844 the download badges also fit.
- `AC-03`: on a wide screen the 24-second hero plays; with reduced motion,
  and on phones, the poster shows instead.
- `AC-04`: the line is the only product line in `DESIGN.md`, the site, the
  README and its GIF, and About Posato. It remains in the store listing
  (`DOCS-004`), in retained history, and in `prototypes/`.
- Deviations decided by the maintainer:
  - About Posato changes here.
  - The old `Hero` composition is gone; the new hero also feeds the README
    GIF (now 24 seconds and 1.5 MB instead of 9.6 MB).
  - The homepage styles joined `site.css` without a page prefix.
  - The pull request merges right after review rather than with
    `RELEASE-005`, so merging deploys `posato.app`.

## Review

- Clarity review: the homepage title now leads with Posato. The hero video
  label was shortened.
- Independent completed-change review: no Critical or Required findings.
  The maintainer accepted all three Recommended and all four Optional
  findings:
  - the video label overstated what syncs;
  - the old hero media still shipped;
  - the brief did not cover About Posato;
  - experiment names, dead `.eyebrow` CSS, invisible selection in the
    privacy section, and stylesheet order.
  All are fixed in `8ab4518` and `9f43120`. The maintainer chose not to
  review this correction again.

## Checks

- `npm run check` in `video/` (lint and 6 storyboard tests).
- `npm run render:hero`, `render:walkthrough`, `export:gif`,
  `export:web`, and `verify`: the hero is 720 frames; the GIF is
  960 x 600 at 15 fps, 24 seconds, with matching first and last frames
  (YAVG 40.53); the site MP4 is 506,442 bytes with faststart.
- Astro production build with no inline styles. The built site was checked
  at 375 x 667, 390 x 844, and 1440 x 900 with reduced motion on and off.
  Support keeps its styles from `main`.
- About Posato through `posato-control`:
  - Tart VM: the `licenses` scenario passed.
  - Test iPhone: About showed the new line in three runs. Each run then
    stopped at a different later step (36, 24, 12) while scrolling a long
    license document. That is device-driver flakiness, not an app defect;
    the maintainer chose to leave it unrecorded.
