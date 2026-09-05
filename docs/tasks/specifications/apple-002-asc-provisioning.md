# `APPLE-002`: Provision development profiles and certificates through the App Store Connect API

- **Review tier:** `standard`
- **Tier reason:** Maintainer tooling that talks to the Apple account with an
  Admin team key; it changes no product code, but a leaked key or a
  misaddressed request affects every Apple target, so the credential boundary
  and request bounds need one independent review.
- **Dependencies:** completed `APPLE-001` (five App IDs, App Group, CloudKit
  container registered), `SYNC-006` (the macOS companion profile created by
  hand, which this task must be able to recreate)
- **Integration group:** `PR-ASC-PROVISIONING`
- **Authority:** `APPLE-002` in MVP roadmap revision 9, the repository safety
  rules (no credentials, team identifiers, profiles, or device identifiers in
  Git), `docs/tasks/README.md` "Human and one-off work" (the roadmap row is
  the maintainer's explicit request for the tool; `IOS-002`, `SYNC-007`, and
  `RELEASE-001` are its named repeated consumers)

## Outcome

From a provisioned checkout, one command lists the Posato App IDs, registers
the connected supported iPhone and this Mac when missing, ensures one valid
Apple Development certificate for the maintainer's team, and creates,
downloads, and installs a development profile for any of the five Posato App
IDs, so `IOS-002`, `SYNC-007`, and later release work never wait on a portal
step; every secret stays outside Git.

## Boundaries

- Add one small Gradle tool module under `tools/` (JDK-only: ES256 JWT with
  `java.security`, `java.net.http`, no new third-party dependency) with a
  `provision` command surface: `doctor` (key, issuer, team, certificate, and
  device state), `devices register`, `certificates ensure`, and
  `profiles ensure <app-id> [--platform ios|macos]`. Idempotent: existing
  valid resources are reused, expired or invalid profiles are replaced, and
  nothing is deleted without an explicit `--replace`.
- Configuration follows the existing `local.properties` convention with
  three new keys (`posato.asc.keyId`, `posato.asc.issuerId`,
  `posato.asc.privateKeyPath`); the `.p8` lives under
  `~/Library/Developer/Posato/` like the `SYNC-006` profile. Downloaded
  profiles are installed to the same untracked directory and, for iOS, to the
  Xcode provisioning-profiles directory; output names follow the accepted
  convention (product, target, profile type).
- Certificate creation generates the key pair in the login keychain and sends
  only the CSR; the private key never leaves the Mac. Device registration
  reads identifiers from the local tools and never prints or stores them in
  tracked files.
- Requests are bounded: fixed hosts, pinned API version, 60 s deadline,
  response size cap, exponential retry only on 429 and 5xx, and redacted
  error categories (no response bodies in output by default).
- Non-goals: distribution certificates, App Store submission, TestFlight,
  notarization, capability changes on App IDs, deleting or renaming
  resources, CI use, and any change to `tools/posato-control/**` (a separate
  lightweight change is in flight there).
- Exclusive write surface while `MACOS-005` and `IOS-002` run in parallel: the
  new `tools/<module>/**`, `settings.gradle.kts` (one `include`),
  `docs/development/apple-provisioning.md` (new), and the roadmap only if a
  row needs correction. Shared by rebase: `docs/wiki/log.md`. Do not touch
  `desktopApp/**`, `iosApp/**`, `macosHelper/**`, `shared/**`, or
  `tools/posato-control/**`.

## Acceptance

- `AC-01` — `doctor` reports each provisioning condition as OK, MISSING, or
  UNKNOWN with a next action, without printing the key, issuer, team, device
  identifiers, or profile contents.
- `AC-02` — `profiles ensure` for `app.posato.ios.activitymonitor` produces an
  installed development profile that Xcode accepts for the `IOS-002` extension
  target, and for `app.posato.macos.sync` a profile equivalent to the
  hand-made `SYNC-006` one (`:desktopApp:verifyMacOsDevelopmentPackaging`
  passes with it); repeating the command changes nothing.
- `AC-03` — With no key configured, an expired token, or a revoked key, every
  command fails closed with a categorical message and makes no request that
  mutates the account.
- `AC-04` — No credential, team identifier, device identifier, or profile
  byte appears in tracked files, test fixtures, or logs; `./gradlew quality`
  passes with the new module included.

## Verification

- Unit tests: ES256 JWT header, claims, expiry, and signature over a synthetic
  key; request building against a recorded synthetic API shape (App IDs,
  devices, certificates, profiles); idempotence decisions; redaction of
  error output; configuration key parsing.
- Manual run on the maintainer's Mac against the real account: `doctor`, the
  two `profiles ensure` cases in `AC-02`, and the repeat run; results recorded
  categorically in the execution record without identifiers.
- `./gradlew quality`, `git diff --check`, private-data scan; one independent
  completed-change review.

## Decisions or blockers

- Blocker (maintainer action): create one App Store Connect API team key with
  the Admin role under Users and Access, download the `.p8` once, store it
  under `~/Library/Developer/Posato/`, and add the three keys to
  `local.properties` in the main checkout (then copy the whole file into the
  worktree). Implementation up to the manual run proceeds without it.
- Open (maintainer decision): module name and command shape. Recommended:
  `tools/posato-provisioning` with the `posato-provisioning` launcher, kept
  separate from the verification driver so the two tools never share a
  change surface.
- Decided by authority: profiles and keys stay untracked; the tool reads
  `posato.apple.developmentTeam` from the same `local.properties` rather than
  a second team configuration.
