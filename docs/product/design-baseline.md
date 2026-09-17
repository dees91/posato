# Product and Design Baseline

## Status

- **Status:** Accepted
- **Accepted:** 2026-08-25
- **Provenance:** `user-confirmed`
- **Gate 3:** Complete

The canonical, tool-neutral design contract is [DESIGN.md](../../DESIGN.md).
It defines the accepted brand foundation, product language, visual tokens,
reusable Compose components, current MVP screens, platform navigation, and
accessibility constraints.

The maintained low-fidelity flow diagrams and proposal history remain in the
[brand and product design synthesis](../wiki/topics/brand-and-design-baseline.md).
Current authoritative Apple design evidence is summarized in the
[source digest](../wiki/sources/apple-design-guidance.md).

## Native design adoption

`user-confirmed` (2026-09-07): adopt the accepted native prototype presentation
and complete reusable design system in the real MVP in one pull request. Keep
the visual result practically 1:1 while adapting Compose code to the existing
ViewModels, persistence, and native application-selection boundaries. The
frozen reference is revision `27da7bdd6c831213a65b71db59a57cc5df70f174`.

This supersedes the previous deferral of exact tokens, component visuals,
and navigation. The root `DESIGN.md` now owns those specifications; the
prototype remains reference evidence, not a runtime dependency. Mock session,
sync, enforcement, onboarding, and overlay behavior is not adopted. Final
store artwork, a custom typeface, and unimplemented product flows remain
outside this adoption. Verification tooling and its maintained skill are
part of the same change.

## Historical prototype documentation amendment

`user-confirmed` (2026-09-06): the maintainer requested an accurate, complete
description of the current native interaction prototype. The root `DESIGN.md`
retains the accepted production contract and routes to the separately scoped
[prototype design reference](../../prototypes/mvp-interaction-flow/DESIGN.md).
Its concrete tokens, navigation, copy variants, and component states describe
the mock; they are evidence for the existing design-system consolidation
checkpoint, not acceptance of those choices for production. The production
deferrals remained in force until the explicit adoption above (`superseded`).
