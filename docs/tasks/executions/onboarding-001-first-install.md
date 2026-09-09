# Execution: `ONBOARDING-001`

- **Brief:** [First-install setup](../specifications/onboarding-001-first-install.md)
- **Status:** `done`
- **Review tier:** original task `high-risk`; accepted UI correction `standard`
- **Branch:** `feature/onboarding-001-first-install` (PR #44)
- **Updated:** 2026-09-09

## Plan

1. Implement accepted D1–D6 after plan review, including completion and migration.
2. Add narrow helper and Screen Time authorization ports over existing services.
3. Host six first-install steps outside navigation, reuse synchronization and
   website entry, and read summary values from returned service state.
4. Verify migrations, service behavior, native setup, and upgrade routing;
   complete documentation and independent review.
5. Integrate the subsequently accepted UI proposal into PR #44, record its
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
- DESIGN.md and the brand topic record acceptance; consent, schema, sync,
  website validation, and six-step sequencing remain unchanged.
- Fixtures follow the accepted labels and wait for distinct step content when
  consecutive screens share Not now.

## Completed-change reviews

- Original full implementation: independent `approve`; checked upgrade seeding,
  tri-state hosting, ownership, helper threading/settings, sync copy and skip.
- Accepted UI proposal and final integration: independent `approve`, no
  Critical/Required findings. Reviewed responsive layout, wording against
  returned service states, callback preservation, preview cases, fixtures,
  and DESIGN/brief/wiki consistency. Final application files match the
  reviewed proposal.

## Verification

Evidence remains in ignored `build/verification/`; this record is categorical.

| Scope | Check | Result |
| --- | --- | --- |
| Final integrated UI | `./gradlew quality` | Pass; 200 tasks, including lint, shared/desktop tests, native tests and packaging |
| Final integrated UI | Driver desktop build; Simulator app and driver build | Pass |
| Final integrated UI | Mac full setup, enable helper → Continue, add website, summary, Session and relaunch | Pass; one website, completion row, zero bootstrap rows; local DB restored byte-identical |
| Final integrated UI | Simulator full/skip, keyboard and completion persistence | Pass; full and skip paths, visible software keyboard, Session after relaunch, expected DB rows; data restored |
| Original service implementation | Simulator full/skip and degraded permission outcome | Pass |
| Original service implementation | Mac fresh setup, helper enable and upgrade seed | Pass; local DB restored byte-identical |
| Original service implementation | Physical iPhone deferred permission and attended Screen Time grant | Pass; not repeated for the UI correction |
| Review correction | `./gradlew quality`; settings-launch regression | Pass; 197 tasks; regression red before fix, green after |
| Review correction | Mac welcome via Tab/Space; pgrep at welcome and before permission action | Pass; keyboard advances; helper absent before consent; DB restored |
| Review correction | Simulator unavailable request removed; iCloud without account | Pass; full fixture, actual retryable status, zero bootstrap rows; DB restored |
| Review correction | Simulator largest Dynamic Type privacy page | Pass; OS content-size launch override, scoped content scroll and Continue |
| Review correction | `git diff --check`; independent code and documentation review | Pass; no new Critical/Required defects |

## Review follow-up

The authorized Standard feedback correction required no new brief. Independent
review of the five implementation/test/fixture files found no Critical/Required defects.

| Finding class | Count | Decision |
| --- | --- | --- |
| Required verification/closeout evidence | 1 | Available evidence completed; remaining AC-06 pre-merge evidence waived by maintainer below |
| Advisory Mac recovery/administrator copy | 1 | Correct the real route and websites-only authorization; reject the suggested universal prompt/retry claims |
| Advisory unavailable request action | 1 | Hide the ineffective action, retain Not now |
| Advisory settings-launch failure | 1 | Catch IOException; regression test failed before the fix and passes after it |
| Advisory unused resources | 1 | Remove both unreferenced aliases |

## Threat-model closeout

ONBOARDING-001 remains within the accepted threat model. `local_setup_state`
stores only a completion fact, without timestamp, identity, policy, authorization,
key material, or event history. It gates setup display, granting no capability.
Storage and validated reads remain within `TB-02`; app-private modification
remains the accepted `R-02` limit. There is no new sensitive asset class,
external service, diagnostic collection, or trust boundary.

No Posato account follows the MVP scope and `TB-07`/`R-01`: Apple trust remains
membership, without independent Posato revocation. No browsing records, activity
feed, usage scores, telemetry, or crash uploads follows the excluded-data
boundary, `A-08`, `T-09`, `T-12`, and diagnostics policy. Local app choices follow
`A-03`/`T-10`. Encryption before optional upload follows `TB-06`/`T-06`; `R-03`
still permits metadata visibility and delay. Connected to iCloud means linking,
not delivery. No remote-wipe, total-key-loss recovery, anonymity, or guaranteed
erasure claim is made, preserving `R-04`/`R-05`.

## Limits

- `user-confirmed` (2026-09-09): after the remaining AC-06 gaps were listed,
  the maintainer instructed merging PR #44. This waives attended VoiceOver on
  both platforms, iPhone keyboard navigation, and native Mac larger-text proof
  as pre-merge evidence for this PR only. They remain unverified; no broader
  accessibility claim or standing design-requirement waiver is implied.
- The maintainer Mac already approves the helper; the approval-required branch
  has unit evidence only.
- An existing database without any domain, policy, bootstrap row, or session
  shows setup once; this is the accepted upgrade-seed boundary.

## Outcome
The reviewed code and local checks pass at `fda6af1`. The maintainer authorized
merge with the bounded AC-06 waiver above; this closeout changes only records.
