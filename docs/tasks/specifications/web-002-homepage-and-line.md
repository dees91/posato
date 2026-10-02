# `WEB-002`: A more inviting homepage and product line

- **Review tier:** Standard
- **Tier reason:** Public site, copy, and media only; no application code,
  privacy boundary, or release operation changes.
- **Dependencies:** None (release 1.3, wave 4). `DOCS-004` waits for this row.
- **Integration group:** PR-WEB-HOMEPAGE
- **Authority:** [Release roadmap](../release-roadmap.md) revision 16, row
  `WEB-002`; idea 24; maintainer named the row on 2026-10-02.

## Outcome

The first screen of `posato.app` shows at a glance what Posato does, also on
a phone, and a stronger product line replaces **Pause. Then choose.**
everywhere it appears.

## Boundaries

- Continue from the experiment already on this branch: a bolder layout, a
  24-second animated hero with a static poster on phones and for reduced
  motion, a contrasting privacy section, and the provisional line "A little
  space. For what matters."
- The maintainer chooses the final line and layout from proposals; the line
  then changes in `DESIGN.md`, the site title and hero, and the README. The
  App Store subtitle changes with the 1.3 store record in `DOCS-004`.
- Public copy passes the clarity review, uses no em dash, and stays within
  `PRIVACY.md` and the availability page; the hero shows only behavior the
  captures prove.
- Keep the site's script-free, cookie-free page and its security headers,
  unless the hero video needs a reviewed exception.
- Non-goals: the 1.3 feature text, store screenshots, and showcase media
  (`DOCS-004`), and the Polish site (`I18N-001`).

## Acceptance

- `AC-01` — The maintainer has chosen the line and layout.
- `AC-02` — On a phone-width viewport the first screen names what Posato does
  and shows the product, without scrolling.
- `AC-03` — On a wide screen the hero plays, and the poster appears with
  reduced motion or before playback; no empty frame shows.
- `AC-04` — The chosen line is the only product line in `DESIGN.md`, the
  site, and the README.

## Verification

<!-- Unattended by default (AGENTS.md): macOS in a Tart VM with --vm, never on the host Mac; iOS on the test iPhone. -->

- The Astro production build, and the built site checked at phone and wide
  widths with reduced motion on and off.

## Decisions or blockers

- Merging deploys `posato.app`, because Cloudflare Pages builds `website/`
  from `main`; the maintainer decides whether it merges before or with
  `RELEASE-005`.
- The four experiment entries in `docs/wiki/log.md` fold into one entry at
  closeout.
