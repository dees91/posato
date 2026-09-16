# `DOCS-002`: Rebuild the showcase media with a visible cursor, device frames, and a hero video

- **Review tier:** `standard`
- **Tier reason:** Public-facing media, README, and site changes alter no product behavior; an independent check still covers claim accuracy, synthetic captures, reproducible renders, size budgets, and the site's performance baseline.
- **Dependencies:** completed `DOCS-001` (`33495be`) and `WEB-001` (`1a113f4`). `RELEASE-002` rechecks the walkthrough attachment link at publication.
- **Integration group:** `PR-SHOWCASE-MEDIA`, roadmap wave Release/R2.
- **Authority:** [MVP roadmap](../mvp-roadmap.md) (revision 19 maintainer request), [DESIGN.md](../../../DESIGN.md) (voice, palette, website), [README](../../../README.md), [showcase media](../../../video/README.md), [WEB-001 record](../executions/web-001-posato-site.md).

## Outcome

A newcomer to the README or `posato.app` sees, within the first screen, a Posato demo in which every step is a visible click or tap on a framed Mac or iPhone capture, with no dead pauses; the README step screenshots show both devices side by side in the same frames as the site, and the site's hero plays the same demo as a silent loop instead of a still that repeats the frame below it.

## Boundaries

- Model the motion system on the maintainer's `skill-manager` promo: product design tokens, a springy cursor with a click ring and a tap ring on the iPhone, before/after capture crossfades, callout pills naming each action, gated zoom, six-frame scene overlaps, and a closing card that fades to the canvas for a seamless loop. Keep the storyboard as data with a test that forbids static holds longer than 90 frames.
- Device frames in the video, the composed README stills, and the site share the website's bezel geometry and the moss-to-sage wallpaper; the site's hero shows the promo in a plain outlined panel because the promo already draws bezels.
- Captures come from the real apps through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) with synthetic websites, one built-in app per device, and no account, notification, or personal data; the Simulator is not a source. Only resolution is reduced; nothing is retouched.
- Copy on the demo, the walkthrough, the README changes, and the site's new alt text is reviewed with the `clarity` skill before rendering; no em or en dashes; no sync, availability, or enforcement claim beyond verified behavior.
- Budgets: tracked GIF at or below 10 MiB, site hero MP4 at or below 3 MiB, composed stills at or below 1 MiB each, walkthrough attachment below 10 MiB. The GIF ladder may reduce frame rate, palette, or size, never the story.
- The site keeps no scripts, cookies, analytics, web fonts, or third-party assets; the video is self-hosted, skipped in compact layouts, and replaced by its poster under Reduce Motion. The CSP gains only `media-src 'self'`.
- Non-goals: store metadata, product or copy changes inside the apps, a public download or repository link (`RELEASE-002`), WebM output, a new wiki topic page.

## Acceptance

- `AC-01` — Every action in the demo and the walkthrough is visible as a click or tap with a callout naming it, and no scene holds a static frame longer than three seconds; the storyboard test enforces the frame limit.
- `AC-02` — The README step screenshots, the demo, and the site show the Mac and iPhone captures side by side in matching device frames, and the site's hero no longer repeats the iPhone frame of the step below it.
- `AC-03` — `npm run media` in `video/` reproduces the GIF, the site MP4 and poster, the attachment, and the stills from tracked sources within the documented budgets, and `npm run verify` passes.
- `AC-04` — Captures and renders show only synthetic data at one recorded product revision, and every README and site path resolves.
- `AC-05` — Local Lighthouse runs of the product page (mobile and desktop) after the change show no category regression against the baseline taken before it, and the mobile run does not fetch the hero video.

## Verification

- Clean `video/` install, `npm run check`, `npm run media`, `npm run verify`; contact sheet and GIF first/last-frame review.
- posato-control capture runs cited by their ignored run directories; privacy check of every tracked PNG.
- `website/` build, built-output scan, browser check at phone and desktop widths in both appearances, Lighthouse before and after, pull-request preview reviewed by the maintainer.
- Independent completed-change review against `DESIGN.md`, `video/README.md`, and the README claims.

## Decisions or blockers

- **Decisions (`user-confirmed`, 2026-09-16):** Remotion with a synthetic cursor instead of screen recording; GIF budget raised to 10 MiB and tracked; the site hero embeds the promo as a muted looping MP4; one roadmap row for the README and the site because both draw on `video/public/`.
- **Blockers (maintainer):** confirming the macOS administrator prompt during the attended active-session capture; driving the physical iPhone through the Screen Time picker and the active session; uploading the new walkthrough as a GitHub attachment; accepting the `DESIGN.md` website amendment and the Pages preview.
