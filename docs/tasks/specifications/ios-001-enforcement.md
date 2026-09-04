# `IOS-001`: Apply and clear only Posato-owned iOS restrictions

- **Review tier:** `high-risk`
- **Tier reason:** The adapter writes Managed Settings restrictions on a
  person's phone under the Family Controls entitlement and moves the opaque
  selection store into the App Group; a wrong apply restricts the wrong site
  or app, and a wrong clear leaves the phone restricted or removes another
  source's settings.
- **Dependencies:** completed `SESSION-001`, `TARGETS-004`, `TARGETS-005`,
  and `APPLE-001` (App Group `group.app.posato.ios.session` registered and
  assigned to `app.posato.ios` and `app.posato.ios.activitymonitor`, Family
  Controls development capability on `app.posato.ios`)
- **Integration group:** `PR-IOS-ENFORCEMENT`
- **Authority:** `IOS-001` in MVP roadmap revision 8,
  [ADR 0003](../../decisions/0003-mvp-application-architecture-baseline.md)
  (iOS enforcement boundary, target graph), `DESIGN.md` blocked presentation
  and unavailability copy, the diagnostics policy (no domains, tokens, or
  bundle identifiers), the threat model (`A-03`, `A-07`, `TB-03`, `T-01`,
  `T-10`, `T-11`, `T-14`), and the `TARGETS-004` decision that `IOS-001`
  owns the App Group migration

## Outcome

On a development-signed iPhone the application applies Posato-owned web
domain and application restrictions for the saved exact domains and the
opaque local selection through one named Managed Settings store, the system
shows its own blocked presentation for a paused site in Safari and for a
shielded application, and an exact, repeatable clear restores access without
touching any restriction Posato did not create.

## Boundaries

- Add a Swift provider in `iosApp` that owns one named `ManagedSettingsStore`
  reserved for Posato, translates canonical exact domains into `WebDomain`
  values and local mapping identifiers into the stored `ApplicationToken`
  values, applies both as one validated set, verifies the store reflects the
  set, and clears only that store. The set is atomic by ordering, not by the
  platform: every validation (canonical domain restore, every mapping-id
  token resolution, the empty-set check) completes before the first write,
  so a failure refuses before any write. On any mid-apply throw or verify
  mismatch the provider clears the owned store (rollback) and returns
  platform-failure. Family Controls, Managed Settings, and
  token types stay in Swift.
- Add a Kotlin `iosMain` adapter behind a public Swift-facing interface,
  mirroring `IosKeychainProvider`: apply takes canonical domain strings and
  `LocalApplicationMappingId` values from the existing `TargetPolicy` and
  `LocalApplicationMappings` types, clear takes nothing, and both return
  platform-neutral outcomes (applied, cleared, nothing-to-enforce,
  authorization-required, authorization-denied, restricted, unavailable,
  selection-missing, platform-failure). Explicit mapping: authorization not
  determined returns authorization-required, denied returns
  authorization-denied, restricted returns restricted, Simulator and Release
  builds without the capability return unavailable. A selection with no
  domains applies applications only, mirroring the decided websites-only
  rule; nothing-to-enforce is returned only when both sets are empty. No
  `expect`/`actual`.
- Migrate the `TARGETS-004` selection store from the app-private container
  with complete protection to the App Group container
  (`group.app.posato.ios.session`) with protection available after first
  unlock, keeping the token bytes and identifiers unchanged, so `IOS-002`
  can read them from the Device Activity extension. The migration runs
  inside the live store factory, so the `TARGETS-004` and `TARGETS-005`
  flows keep their paths: copy the source file, verify read-back equality,
  then delete the app-private file; re-running is safe (a verified group
  copy is kept, a missing source with a present group copy means done). A
  corrupt source is never copied: migration reports corruption, the old
  file stays for the `TARGETS-005` clear-selection path, and apply writes
  nothing. Any other migration failure surfaces as a storage failure and
  apply writes nothing. The group copy keeps backup exclusion. Add only
  the App Group entitlement; no extension target, no expiry
  callback, no custom shield target.
- Authorization loss: apply with authorization not granted applies nothing
  and reports the distinct state; clear always runs and is idempotent, so a
  revoked authorization never leaves restrictions behind. If a post-revoke
  clear throws, the provider reports platform-failure as an action-required
  state rather than cleared; where the product controls the ordering, clear
  runs before revoking. The `platform-failure` payload carries only a
  redacted category, never Apple error text.
- No domain, token, mapping identifier, bundle identifier, or raw Apple error
  reaches logs, diagnostics, `toString()`, or test artifacts.
- Non-goals: session wiring, DI wiring, UI, and start/end/expiry recovery
  (`SESSION-002`); clearing after expiry while suspended and the extension
  (`IOS-002`); custom shield presentation; synchronization; macOS.
- `.research/blocker` `iosApp/BlockerPoc/IosEnforcementSpike.swift` is
  read-only behavioral evidence (named store, apply-then-verify,
  `clearAllSettings` on the owned store); re-derive the shape here. The
  path is absent from agent worktrees, so this three-line summary plus the
  decisions below is the complete contract for this task.
- Exclusive write surface while `MACOS-004` and `SYNC-008` run in parallel:
  `iosApp/**` (Swift provider, App Group entitlement, Xcode project,
  `iosAppTests/**`), new `shared/src/iosMain/**/feature/enforcement/**` and
  `shared/src/iosTest/**/feature/enforcement/**`, and
  `docs/wiki/topics/ios-enforcement.md`. Shared with the other tasks and
  resolved by rebase: `docs/wiki/log.md`. Do not touch
  `shared/src/commonMain/**` (`MACOS-004` owns any common enforcement
  contract this wave), `feature/session/**`, `feature/sync/**`,
  `feature/targets/**`, `app/posato/di/**`, `MainViewController.kt`,
  `PosatoApplication.kt`, `desktopApp/**`, `macosHelper/**`,
  `macosSyncCompanion/**`, Gradle files, or `SKILL.md`. No verify-posato
  feature file or fixture: there is no user path until `SESSION-002` wires
  the session start; the skill's "Out of scope" rule applies.

## Acceptance

- `AC-01` — On the device, applying one saved exact domain and the current
  selection makes Safari show the system blocked presentation for that
  domain and the selected application show the system shield, while an
  unselected domain and an unselected application stay usable; the named
  store reports exactly the applied set.
- `AC-02` — Clear removes every Posato restriction, is safe to repeat, and
  leaves a restriction created in a differently named store untouched;
  after clear the paused domain and application are usable again.
- `AC-03` — With authorization not determined, denied, or restricted, apply
  writes nothing and returns the matching state; with an empty domain set
  and an empty selection it returns nothing-to-enforce and writes nothing;
  a selection identifier without a stored token fails before any write.
- `AC-04` — The selection saved before the migration is readable after it
  from the App Group container with the after-first-unlock protection class,
  the `TARGETS-004` and `TARGETS-005` device flows still pass, and the
  Simulator and Release builds report unavailable without opening anything.
- `AC-05` — No domain, token, identifier, or Apple error text appears in any
  `toString()`, log, diagnostic, or test artifact; `./gradlew quality` and
  the Kotlin `iosTest` suite pass on the Simulator.

## Verification

- Swift tests in `iosAppTests` over an injectable store and authorization
  seam: set translation, apply-then-verify, empty-set refusal, unknown
  identifier refusal, clear idempotence, a two-store test proving clear on
  `app.posato.session` leaves a differently named store intact, an
  injected mid-apply failure proving the owned store ends empty, and the
  store-migration read-back including a corrupt-source case that copies
  nothing.
- Kotlin `iosTest` contract tests over a fake provider for every outcome
  mapping plus the enumerated redaction test for the new carriers.
- Physical iPhone: `iosAppTests` device run for the apply and clear cycle
  with a synthetic domain and the real selection, then a manual checklist
  recorded pass or blocked per step (Safari blocked presentation, shielded
  application, unselected controls usable, clear restores both, revoke then
  clear leaves nothing), evidence under `build/verification/` without
  screenshots or identifiers in tracked files.
- `./gradlew quality`, `git diff --check`, suppression and private-data
  scans, an independent plan review before implementation, and an
  independent completed-change review with evidence.

## Decisions or blockers

Every decision below is accepted (`user-confirmed`, 2026-09-04) unless it is
marked open.

- Decided: one Posato-only named store (`app.posato.session`); clear is
  `clearAllSettings()` on that store only, never on the default store.
- Decided: domain matching stays the canonical exact string as a
  `WebDomain`; whether iOS also pauses subdomains is recorded as an observed
  platform fact on the device, not promised in product copy.
- Decided: apply with domains and no selection pauses websites only,
  matching the `SESSION-001` review rule; nothing-to-enforce is refused.
- Decided on plan-review authority (maintainer confirms at merge): apply
  with a selection and no domains pauses applications only, mirroring the
  websites-only rule; nothing-to-enforce is returned only when both sets
  are empty.
- Decided: the App Group migration happens here with the token bytes
  unchanged, per the `TARGETS-004` decision; the shared record for `IOS-002`
  is limited to the store name and a version.
- Resolved with `MACOS-004`, which owns common `feature/enforcement` this
  wave: no shared `commonMain` port is introduced now. Each platform keeps
  its own adapter, `IOS-001` builds against the existing types, and
  `SESSION-002` defines the common contract shaped like
  `apply(domains, mappingIds)`, `clear()`, and `status()` when it wires both
  hosts.
- Physical gates: development-signed iPhone with the maintainer's team,
  Screen Time authorization granted through the real picker with at least
  one application selected (manual, see the mappings feature file). Family
  Controls distribution availability stays a later blocker.
- Open: restriction state after uninstall and reinstall and after a restore
  from backup is recorded from the device, not assumed.
