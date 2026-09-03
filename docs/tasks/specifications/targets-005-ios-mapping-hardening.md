# `TARGETS-005`: Recover iOS application mappings from stale, refused, and corrupted states

- **Review tier:** `high-risk`
- **Tier reason:** The change alters the Family Controls authorization
  continuation and the Swift-to-Kotlin selection boundary that holds the
  shared adapter mutex, and it makes a destructive overwrite of the protected
  opaque-token store reachable from inside the app.
- **Dependencies:** completed `TARGETS-004`
- **Integration group:** `PR-IOS-MAPPING-HARDENING`
- **Authority:** `TARGETS-005` in MVP roadmap revision 7; the deferred items
  recorded under "Deferred hardening after `TARGETS-004`" on the iOS
  enforcement wiki page; ADR 0003 (iOS boundary); the threat model (`TB-01`,
  `TB-03`, `T-10`); `DESIGN.md` for copy

## Outcome

On a physical iPhone a stale authorization request can no longer drive a later
picker, a refused picker presentation fails the pending selection and releases
the adapter for later loads, removals, and clears, a corrupted selection store
can be cleared from inside the app, and a build without a selection producer
says so in product terms.

## Boundaries

- Fix only the four deferred items. The `TARGETS-004` authorization, storage,
  identifier, redaction, capacity, and Simulator-unavailable contracts stay
  unchanged: no entitlement, App Group, store format, or schema change.
- Stale request: every `choose` carries its own request generation; a
  continuation from an earlier request presents and completes nothing.
- Refused presentation: check for an already presented controller before
  presenting and confirm through the presentation completion; a refused or
  failed presentation completes the pending selection with the picker-failure
  outcome so no completion or mutex is stranded.
- Corrupted store: choose and remove stay blocked on corruption; the
  corruption notice exposes `Clear selection`, which overwrites the store
  with an empty valid file. The desktop `clear` already removes all rows the
  same way, so the shared-UI change is platform-neutral. Clearing touches only
  the unreadable selection, never the application group or domains.
- Unavailability copy: replace the build-terms sentence with the
  maintainer-accepted product-terms sentence in `DESIGN.md`, the string
  resource, the previews, and the verification feature map.
- Non-goals: enforcement (`IOS-001`), expiry (`IOS-002`), the App Group
  migration, a Simulator picker, display names for tokens, and any session
  work.
- Exclusive write surface while `SYNC-004` runs in parallel: `iosApp/**`,
  `shared/src/{iosMain,iosTest,commonMain,commonTest}/**/feature/targets/**`,
  `shared/src/commonMain/composeResources/**`, the `DESIGN.md` copy,
  `.agents/skills/verify-posato/features/ios-application-mappings.md`, and
  `docs/wiki/topics/ios-enforcement.md`. Touch `IosApplicationGraph.kt` and
  `MainViewController.kt` only if a constructor signature changes. Do not
  touch `feature/sync/**` or `SyncReplica.sq`.

## Acceptance

- `AC-01` — A selection cancelled or completed while its authorization request
  is still pending never presents or completes a later selection; the later
  request behaves as if it were the only one.
- `AC-02` — When the presenter already presents a controller or the
  presentation fails, `chooseApplications()` returns the picker failure, the
  prior snapshot is retained, and a following `load`, `remove`, and `clear`
  complete without waiting on the failed operation.
- `AC-03` — With a corrupted store file the Targets screen shows the corruption
  notice with `Clear selection`; clearing writes an empty valid store, the
  notice disappears, and choosing becomes available again, while retry alone
  still reports corruption.
- `AC-04` — Simulator and Release builds show the accepted product-terms
  sentence; `DESIGN.md`, the string resource, the previews, and the
  verification map agree, and no other copy changes.
- `AC-05` — The `TARGETS-004` physical flow still passes on a development-signed
  iPhone: authorize, pick, cancel, clear, restart.

## Verification

- Keep the generation and presentation guards in FamilyControls-free Swift
  types so Simulator XCTest covers `AC-01` and `AC-02` with fakes, plus the
  real-store corruption clear for `AC-03`; the device path compiles in the
  Debug device build.
- Shared ViewModel tests on JVM and iOS Simulator for the corrupted-state clear
  transition; `./gradlew quality`; the credential-free Debug Simulator, Debug
  device, and Release Simulator builds.
- Physical iPhone run through the `verify-posato` iOS mappings recipe for
  `AC-05`; the refused-presentation and corrupted-store cases are covered by
  automated tests because the device store is not reachable from outside.
- `git diff --check`, a private-data scan, an independent plan review before
  implementation, and an independent completed-change review with evidence.

## Decisions or blockers

- `open`: the product-terms unavailability sentence. Recommended: "Choosing
  apps is not available in this version of Posato." It names the product, not
  the build, and stays true for Simulator and Release builds. Maintainer
  acceptance is needed before the `DESIGN.md` edit; it can arrive with the plan
  review.
- `open`: whether clearing a corrupted store needs a confirmation.
  Recommended: no; the notice already states that the selection cannot be
  verified, the action label is explicit, and `DESIGN.md` reserves
  confirmation for ending a session early.
- Blocker for `AC-05`: a physical iPhone with the maintainer's development team
  supplied only through the ignored `local.properties`, as for `TARGETS-004`.
