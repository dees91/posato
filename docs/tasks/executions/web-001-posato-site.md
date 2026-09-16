# Execution: `WEB-001`

- **Brief:** [web-001-posato-site.md](../specifications/web-001-posato-site.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Claude Code agent
- **Reviewer:** separate Claude Code agent
- **Branch:** `feature/web-001-posato-site`
- **Updated:** 2026-09-16

## Plan

1. Mark `PRIVACY.md` as effective from 2026-09-16; no other text changes.
2. Build `website/` with Astro: product, limits, privacy, support, and
   not-found pages; the policy, limits, screenshots, and favicon come from their
   repository sources; `_headers` sets the security headers and `noindex` on
   `pages.dev` hosts.
3. Add the `support@posato.app` routing rule and turn off zone features that
   inject scripts or cookies.
4. Create the Pages project with watch paths and review a pull-request preview.
5. After the preview is accepted, attach `posato.app` and redirect `www`.

## Result

- `PRIVACY.md` is effective from September 16, 2026; the rest of its text is
  unchanged.
- `website/` uses Astro 7.3.2 with no integrations and no client scripts. It
  builds `/`, `/limits/`, `/privacy/`, `/support/`, and `404.html`:
  - `/privacy/` renders `PRIVACY.md` with typographic substitution disabled.
  - `/limits/` renders the Limits section of
    `docs/product/limits-and-platforms.md`, and the build fails if that section
    is missing.
  - Captures come from `video/public/`, the favicon from the Forest icon
    source, and stylesheets and the favicon are emitted as files, so the CSP
    needs no inline or `data:` allowances.
- Captures sit in generic CSS device frames after the maintainer compared two
  local variants and chose a display bezel with a sage wallpaper, without a
  laptop base.
- A separate clarity-skill agent reviewed all copy. The maintainer accepted
  every site edit and a softer privacy request on the support page, and page
  titles use a hyphen instead of an em dash. The same review corrected the
  subject of one limits sentence: restrictions clear, not sessions. Its
  suggestions for `PRIVACY.md` are deferred and do not change the policy here.
- The footer says "Licensed under the Apache License 2.0." instead of "open
  source" while the repository is private.
- Cloudflare:
  - The Pages project `posato` builds `website/` from GitHub, with watch paths
    `website/*`, `PRIVACY.md`, `video/public/*`,
    `docs/design/app-icon/forest.svg`, and
    `docs/product/limits-and-platforms.md`.
  - `posato.app` is an active custom domain behind a proxied CNAME.
  - `www.posato.app` redirects to the apex with a 301, keeping the path and
    query.
  - E-mail obfuscation is off, the minimum TLS version is 1.2, and a
    `support@posato.app` forwarding rule exists.
- Pages Git integration needed the maintainer to install the legacy
  **Cloudflare Pages** GitHub app, not **Cloudflare Workers and Pages**, on the
  personal account, starting from the Cloudflare dashboard, before the API could
  create the project.
- Deviations from the brief:
  - Astro, the direct image and icon sources, and the `/limits/` page extend
    the watch paths; the brief records the change to `AC-03`.
  - The support page leaves out the security reporting route while the
    repository is private.

## Completed-change review

- **Verdict:** approved.
- **Critical or Required findings:** the first pass found two Required issues:
  the site shortened the limits with no route to the full list, and the footer
  claimed public source.
- **Resolution:** added `/limits/` built from the limits document and changed
  the footer to a licensing statement. Focused re-reviews approved that
  correction, the device frames, and the copy changes, with no new findings.
- **Advisory findings:** accepted the Optional contrast-token and
  `Permissions-Policy` corrections and added `/limits/` to `AC-02`.
- **Hosted review (first pass, `9d40543`):** one P1, that the contact addresses
  did not deliver. It was resolved by the separate mail-routing work and the
  maintainer's delivery confirmation; the only repository change is this
  record.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `npm ci` and `npm run build` | pass | Five pages; the lockfile resolves only from the npm registry |
| Built output scan | pass | No scripts, inline styles, `data:` URLs, external assets, GitHub links, or em and en dashes |
| `AC-01` policy text | pass | Rendered `/privacy/` words match `PRIVACY.md` after stripping Markdown and tags |
| `AC-02` local browser | pass | Chrome at 390 and 1280 CSS pixels in light and dark, no horizontal overflow; screenshots under ignored `build/verification/web-001/` |
| `AC-02` preview | pass | Pull-request preview at build `201730a`: 200 for every page, 404 for an unknown path, security headers, `X-Robots-Tag: noindex`, no cookies, no CSP console errors |
| `AC-03` hosting | partial | Project, watch paths, active custom domain, `http` to `https`, and `www` 301 with path and query verified; the apex serves 404 until the first production build from `main` |
| `AC-04` mail | pass | After separate mail-routing work, the maintainer confirmed on 2026-09-16 that test messages to `support@` and `privacy@` reached the maintainer's mailbox |

## Blockers and accepted risks

- `AC-03` production content needs the merge to `main`. After the merge, check
  with `curl` that the apex serves the pages, headers, and 404 without cookies.
- The security reporting route is missing from the support page while the
  repository is private; `RELEASE-002` adds it with the GitHub Issues link.
- When the repository is public, `RELEASE-002` adds a repository link with an
  inline GitHub logo to the site header (`user-confirmed`, 2026-09-16), not the
  footer, and may restore the "open source" wording.
- `README.md` still omits "only" in its under-15-minute iPhone summary, and the
  store listing says "open source". Both are outside this task and are
  recorded for `RELEASE-002`.

## Final

- **Status:** `done`
- **Outcome:** `AC-01`, `AC-02`, and `AC-04` met; `AC-03` met except production
  content, which follows the merge. The maintainer accepted the change for merge
  on 2026-09-16.
