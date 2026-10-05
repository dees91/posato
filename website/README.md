# posato.app

The public site: the product page, privacy policy, limits, and support. It
is an Astro project built to static pages. Cloudflare Pages builds this
directory from `main` and serves a preview for each pull request.

## Commands

Use the Node version in `.node-version`.

```shell
cd website
npm ci
npm run build     # static pages into dist/
npm run preview   # serves dist/ locally
```

The hero and walkthrough videos and the poster under `public/media/` are
rendered from `video/` (see [`video/README.md`](../video/README.md)); edit
them there, never here.

## Boundaries

- Pages stay script-free and cookie-free with no inline styles. The
  security headers, including the Content Security Policy, live in
  `public/_headers`; a new asset type needs a reviewed change there.
- Every claim stays within [`PRIVACY.md`](../PRIVACY.md) and the
  [limits page](../docs/product/limits-and-platforms.md), and the hero shows only behavior that
  verified captures prove.

## Public copy

- Use "-" or rewrite the sentence; the built pages contain no em or en dash,
  titles included.
- Run the `clarity` skill's review on changed copy before the
  completed-change review.

## Verification

Check the built site, not the dev server, in a headless browser such as
`agent-browser`:

- widths 375 x 667, 390 x 844, and 1440 x 900, in light and dark, with
  reduced motion on and off;
- no horizontal overflow, the first screen names what Posato does, and the
  poster replaces the hero video on phones and with reduced motion;
- no console errors, CSP violations included;
- `grep -rlE '—|–' dist/` prints nothing.

On the pull-request preview, check that every page answers 200, an unknown
path 404, and the security headers are present. Keep screenshots under the
ignored `build/verification/`.
