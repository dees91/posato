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

- `video/` is rebuilt around `src/storyboard.ts` (scenes, copy, targets in
  capture pixels, captures) with `src/storyboard.test.ts` enforcing six-frame
  overlaps, a callout around every action, no quiet stretch over 90 frames,
  and tracked captures. Components: `DeviceFrame` (site bezel ratios,
  crossfade stack), `Pointer`, `TapRing`, `SceneLayer`, `Typography`,
  `Wordmark`; scenes `ActionScene` and `TitleScene`; compositions `Hero`
  (660 frames), `Walkthrough` (1260), stills `StepWebsites`, `StepDuration`,
  `SocialPreview`. The recording-based end scene and `@remotion/media` and
  `@remotion/transitions` are gone.
- Captures: 17 tracked PNGs (the websites screen twice: with the added-count hint for the video, at rest for the stills and the site) from `9b40a2f`, taken through `capture/mac-captures.sh`
  (Mac, picker driven by keyboard, administrator prompt confirmed by the
  maintainer; the window is activated before every screenshot because a
  driver tap does not keep Posato frontmost) and `capture/iphone-captures.sh`
  (fresh install on an iPhone 13 mini, first-install skip scenario, the
  maintainer granted Screen Time and chose one built-in app). The Simulator
  store screenshot `iphone-duration.png` and the unused `mac-active.png`,
  `mac-review.png`, `mac-end.mp4` are removed.
- Copy reviewed with the `clarity` skill before rendering; five callouts and
  the support line changed (see the brief's decisions).
- Export: `scripts/render-gif.sh` (ladder landed at 12 fps, 160 colors),
  `render-web.sh` (site MP4 plus JPEG poster), `render-attachment.sh`,
  `verify-output.sh` (dimensions, durations, loop extension, loop seam,
  faststart, budgets, capture widths); `npm run media` chains everything.
- README: hero GIF alt text, both step image pairs replaced by composed stills
  linked to their full size. `video/README.md`, `STORYBOARD.md`, and
  `AGENTS.md` rewritten.
- Site: the hero is one column with the demo as a silent looping MP4 in a
  plain outlined panel, `<source media="(min-width: 601px)">` so phones skip
  the download, a poster under Reduce Motion and in compact layouts hidden
  with the panel; CSP gains `media-src 'self'`; the duration step uses the
  45-minute captures. `DESIGN.md` website paragraph amended.
- The walkthrough is 42 seconds; the maintainer uploaded
  `out/walkthrough-attachment.mp4` as a GitHub attachment on 2026-09-16 and the
  README links to it.

## Completed-change review

- **Verdict:** `changes-required` on `fa1b664` (2026-09-16), corrected in the
  next commit; the corrected render passed `npm run check` and `npm run verify`.
- **Critical or Required findings:** (1) capture crossfades were hard cuts
  except for the last swap in a scene; (2) crossfades stacked both captures at
  partial opacity so the window brightened mid-fade; (3) the closing card
  overrode the scene layer's opacity and cut in hard over the outgoing scene;
  (4) the site poster alt described devices while the poster frame was the
  title card.
- **Resolution:** swaps that have not started are skipped and only the
  incoming capture fades in over an opaque stack; the closing card fades an
  inner layer and drops the wash so the loop still lands on the canvas; the
  poster comes from the paired active-session frame (19.3 s) and the alt
  describes it. Recommended items taken: roadmap "Last amended" date, the start
  scene's pointer begins where the duration scene left it. Optional items
  taken: the duplicate ended-session capture is dropped, the verify script
  asserts capture sizes, the README narrows the cursor claim.
- **Advisory findings:** `<source media>` on video is honoured by Chrome and
  Safari; other mobile browsers may fetch the hidden video (bandwidth only).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `npm run check` (eslint, tsc, 10 storyboard tests) | pass | clean run before `media` |
| `npm run media` and `npm run verify` | pass | hero 1600x1000x30/660, walkthrough 1260 frames; GIF 960x600, 12 fps, 8,909,899 bytes, infinite loop, seam YAVG 35.5 both ends; web MP4 1,281,500 bytes faststart; poster 67,998 bytes; attachment 2,181,040 bytes; stills 531,460 and 572,437 bytes; social preview 91,335 bytes |
| Capture runs (posato-control) | pass | `build/verification/runs/20260916-2052*` to `-2210*` (Mac items, three attended session runs, iPhone install, first-install skip, apps, session); every tracked PNG checked for synthetic data |
| Probe frames at every click and tap | pass | `video/out/probe/` reviewed: pointer tip and tap ring on the control in each scene, callouts readable, loop seam frames identical |
| Website `npm run build` and output scan | pass | five pages; no scripts, inline styles, external assets, or em/en dashes; video and single `<source media>` present |
| Lighthouse before (mobile / desktop) | baseline | `build/verification/docs-002/lighthouse/baseline-*`: 100/100/100/100 both; LCP 1.2 s / 0.3 s; CLS 0; 65 KiB / 54 KiB |
| Lighthouse after (mobile / desktop) | pass, no regression | `build/verification/docs-002/lighthouse/after-*` on the first render and `final-*` on the corrected one: 100/100/100/100 both; final LCP 1.6 s / 0.4 s, 115 KiB / 1,367 KiB; CLS 0; final mobile fetches the poster only, never `hero.mp4` |
| Browser check 390 and 1280 CSS px, light and dark | pass | `build/verification/docs-002/browser/preview-*.png` on the Pages preview (agent-browser): scroll width equals viewport at 390, hero panel `display: none` there; at 1280 the video plays (`paused: false`, `currentSrc` hero.mp4); with Reduce Motion the video is `display: none` and the poster `block` |
| Pages preview headers | pass | `curl -I` on the preview: CSP with `media-src 'self'`, `X-Robots-Tag: noindex`, no cookies; `/media/hero.mp4` is `video/mp4`, the poster `image/jpeg` |
| Independent completed-change review | pass after corrections | separate agent on `fa1b664`; four Required findings resolved, verification rerun on the corrected render |

## Blockers and accepted risks

- Attended captures done on 2026-09-16 (administrator prompt confirmed three
  times because the first two active frames lost window focus; iPhone picker
  and Screen Time consent). The walkthrough attachment
  was uploaded on 2026-09-16. Remaining hand-offs: review the Pages preview;
  accept the clarity copy edits and the `DESIGN.md` amendment already applied.
  While the repository is private the attachment answers 404 to signed-out
  visitors; `RELEASE-002` rechecks it at publication.
- Accepted as real state in the Mac captures: "The session ended early." on the
  inactive Session screen and the iCloud row reading "Sync needs attention".
- Mac captures show a 12-hour clock and iPhone captures a 24-hour clock; both
  are the devices' real settings.

## Final

- **Status:** `active`
- **Outcome:** pending
