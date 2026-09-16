# Execution: `WEB-001`

- **Brief:** [web-001-posato-site.md](../specifications/web-001-posato-site.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** Claude Code agent
- **Reviewer:** pending
- **Branch:** `feature/web-001-posato-site`
- **Updated:** 2026-09-16

## Plan

1. Mark `PRIVACY.md` as effective from 2026-09-16; no other text changes.
2. Build `website/` with Astro: product, privacy, support, and not-found pages;
   the policy, screenshots, and favicon come from their repository sources;
   `_headers` sets the security headers and `noindex` on `pages.dev` hosts.
3. Add the `support@posato.app` routing rule and turn off zone features that
   inject scripts or cookies (e-mail obfuscation).
4. After the maintainer gives the Cloudflare GitHub app access to the
   repository, create the Pages project with watch paths and review a
   pull-request preview.
5. After the preview is accepted, attach `posato.app`, redirect `www`, and
   verify production after the merge.

## Result

- Pending.

## Completed-change review

- **Verdict:** pending
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Pending | | |

## Blockers and accepted risks

- Pages Git integration needs the maintainer to give the Cloudflare GitHub app
  access to the private repository.
- Security reporting stays as `SECURITY.md` defines it (GitHub private
  vulnerability reporting). The support page does not repeat that route while
  the repository is private; `RELEASE-002` adds it with the GitHub Issues link.

## Final

- **Status:** pending
- **Outcome:** pending
