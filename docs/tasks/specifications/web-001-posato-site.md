# `WEB-001`: Publish the posato.app site with the privacy policy and support

- **Review tier:** `standard`
- **Tier reason:** A static site with accepted copy changes no product behavior; publishing the policy as in effect and the public claims still get an independent completed-change review before the custom domain serves them.
- **Dependencies:** completed `DESIGN-002` (icon) and `DOCS-001` (demo, merged as `33495be`). Unblocks the privacy policy URL for `PRIVACY-001`.
- **Integration group:** `PR-WEBSITE`, roadmap wave Release/R2.
- **Authority:** [MVP roadmap](../mvp-roadmap.md) (revision 17), [privacy policy](../../../PRIVACY.md), [limits and platforms](../../product/limits-and-platforms.md), [store listing](../../store/en-US/listing.md), [DESIGN.md](../../../DESIGN.md) (voice, palette, icon), [security policy](../../../SECURITY.md).

## Outcome

`https://posato.app` serves a small static site: a product page, the privacy policy built from `PRIVACY.md` at `/privacy/`, and a support page at `/support/` with working contact addresses, usable as the App Store privacy policy and support URLs.

## Boundaries

- Site source lives in `website/` and builds the policy from the repository's `PRIVACY.md`, never from a copy. Pages hosting on Cloudflare builds from GitHub only when `website/` or `PRIVACY.md` changes.
- Copy reuses accepted text (README, store listing, limits page) and keeps every limit it summarizes accurate; nothing claims a download, store availability, or public source that does not exist yet. No links to the private GitHub repository until `RELEASE-002`.
- No cookies, analytics, third-party scripts, fonts, or network assets; strict security headers.
- Email: `privacy@posato.app` (exists) and a new `support@posato.app`, both forwarding to the maintainer's mailbox. Security reporting stays as `SECURITY.md` defines it.
- Non-goals: blog, localization, download or store badges, GitHub Issues links (added by `RELEASE-002` when the repository is public), changes to the privacy policy text beyond its status line.

## Acceptance

- `AC-01` — `PRIVACY.md` states it is in effect from 2026-09-16 instead of a draft, and `/privacy/` renders exactly that text.
- `AC-02` — `/`, `/privacy/`, `/support/`, and a 404 page render in light and dark appearance at phone and desktop widths, with only first-party assets and the security headers.
- `AC-03` — The Cloudflare Pages project builds from `main` with watch paths limited to `website/` and `PRIVACY.md`, and serves `posato.app` over HTTPS with `www.posato.app` redirecting to it.
- `AC-04` — Mail to `support@posato.app` and `privacy@posato.app` reaches the maintainer.

## Verification

- Local build and browser check of every page in both appearances and widths.
- Pull-request preview deployment reviewed by the maintainer before the custom domain is attached.
- `curl` checks of status codes, redirects, headers, and absence of cookies on the production domain; a test message to each address.
- Independent completed-change review against `PRIVACY.md`, the limits page, and the store listing.

## Decisions (`user-confirmed`, 2026-09-16)

- The policy is published as in effect from 2026-09-16, text otherwise unchanged.
- Support contact is `support@posato.app` now; GitHub Issues is linked at `RELEASE-002`.
- Cloudflare Pages connected to GitHub with build watch paths, rather than local deploys.
- Hosting on Cloudflare Pages, source in this repository (decided before `DOCS-001`).
