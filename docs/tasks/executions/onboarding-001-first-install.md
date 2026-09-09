# Execution: `ONBOARDING-001`

- **Brief:** [First-install setup](../specifications/onboarding-001-first-install.md)
- **Status:** `done`
- **Review tier:** original task `high-risk`; accepted UI correction `standard`
- **Branch:** `feature/onboarding-001-first-install` (PR #44)
- **Updated:** 2026-09-09

## Plan

1. Implement the maintainer-accepted D1–D6 decisions after independent plan review.
2. Add the local completion store, migration seed, and tri-state launch read.
3. Add narrow helper and Screen Time authorization ports over existing services.
4. Host six first-install steps outside navigation, reuse synchronization and
   website entry, and read summary values from returned service state.
5. Verify migrations, service behavior, native setup, and upgrade routing;
   complete documentation and independent review.
6. Integrate the subsequently accepted UI proposal into PR #44, record its
   presentation in DESIGN.md, update driver selectors, and repeat quality,
   native UI verification, and independent completed-change review.

## Original high-risk plan review

- **Verdict:** `changes-required`, resolved before implementation (2026-09-09).
- Seven Required findings covered missing upgrade seeding, helper signing and
  threading, unsupported cross-device promises, undefined completion, missing
  iOS provenance, unobservable consent criteria, and threat-model closeout.
- Resolution added the product-state migration seed; signing pre-check and
  `Dispatchers.IO`; application-owned System Settings and Check again;
  truthful linking language; service-read summary; explicit consent operations;
  provenance and security closeout. The brief records accepted advisory items.

## Result

- A fresh database opens the six-step flow. Existing product state seeds local
  completion on upgrade, and later completed launches open Session directly.
- One `OnboardingDependencies` provider per graph preserves narrow ownership;
  no new quality-tool exceptions were introduced.
- The maintainer accepted the UI proposal on 2026-09-09. It uses the interval
  artwork, compact progress, icon-led privacy rows, contextual permission
  explanations, reachable compact actions, and state-aware continuation.
- DESIGN.md and the brand topic record that acceptance. The UI correction
  changes no consent operation, database schema, synchronization implementation,
  website validation, or six-step sequencing.
- Fixtures follow the accepted labels and wait for distinct step content when
  consecutive screens share Not now.

## Completed-change reviews

- Original full implementation: independent `approve`, no Critical/Required
  findings; checked upgrade seeding, tri-state hosting, provider ownership,
  helper threading/settings, truthful sync copy, and the skip prelude.
- Accepted UI proposal and final integration: independent `approve`, no
  Critical/Required findings. Reviewed responsive layout, wording against
  returned service states, callback preservation, preview cases, fixtures,
  and DESIGN/brief/wiki consistency. Final application files match the
  reviewed proposal.

## Verification

All evidence remains in ignored `build/verification/`; this record is categorical.

| Scope | Check | Result |
| --- | --- | --- |
| Final integrated UI | `./gradlew quality` | Pass; 200 tasks, including lint, shared/desktop tests, native tests and packaging |
| Final integrated UI | Driver desktop build; Simulator app and driver build | Pass |
| Final integrated UI | Mac full setup, enable helper → Continue, add website, summary, Session and relaunch | Pass; one website, completion row, zero bootstrap rows; local DB restored byte-identical |
| Final integrated UI | Simulator full/skip, keyboard and completion persistence | Pass; full and skip paths, visible software keyboard, Session after relaunch, expected DB rows; data restored |
| Original service implementation | Simulator full/skip and degraded permission outcome | Pass |
| Original service implementation | Mac fresh setup, helper enable and upgrade seed | Pass; local DB restored byte-identical |
| Original service implementation | Physical iPhone deferred permission and attended Screen Time grant | Pass; not repeated for the UI correction |
| Final integration review | `git diff HEAD --check` | Pass |

## Limits

- The maintainer Mac already approves the helper; the approval-required branch
  has unit evidence only.
- The UI correction does not establish new physical-device consent, cross-device
  delivery, or attended VoiceOver coverage.
- An existing database without any domain, policy, bootstrap row, or session
  shows setup once; this is the accepted upgrade-seed boundary.

## Outcome

Implementation, native integration verification, and independent review are
complete. PR #44 remains open for maintainer review.
