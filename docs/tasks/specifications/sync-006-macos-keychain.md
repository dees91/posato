# `SYNC-006`: Implement the macOS synchronizable-Keychain companion boundary

- **Review tier:** `high-risk`
- **Tier reason:** The task adds a second signed and provisioned native
  process that handles workspace-key bytes, introduces the JVM-to-companion
  IPC trust boundary (`TB-09`), grants iCloud and Keychain Sharing
  entitlements, and gates every secure-item operation on the local account
  binding; a defect can leak or lose the workspace key or create a second
  workspace.
- **Dependencies:** completed `SYNC-003`; `SYNC-004` supplies the frozen
  Kotlin ports and item encoding; `MACOS-006` supplies the signed packaging
  and strict verification the companion must join
- **Integration group:** `PR-MAC-KEYCHAIN`
- **Authority:** `SYNC-006` in MVP roadmap revision 7,
  [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md),
  ADR 0003, ADR 0004 (`SYNC-003` amendment), and the threat model (`A-05`,
  `TB-06`, `TB-09`, `T-04`, `T-07`, `R-04`, `R-05`)

## Outcome

The packaged macOS application launches its embedded, verified
`app.posato.macos.sync` companion over private pipes and, through it, performs
the ADR 0007 exact synchronizable-Keychain read, create-if-absent, and
delete-and-verify-absent operations with binding preflight and postflight,
returning only the `SYNC-004` port outcomes and never a raw Apple value,
error text, or unguarded key byte.

## Boundaries

- Add a new `macosSyncCompanion` Gradle module holding one Swift package: a
  short-lived executable that reads one framed request from stdin, verifies
  the signed parent and embedded-package relationship before decoding it,
  performs one allowlisted operation, writes one framed response, clears its
  key buffers, and exits. It has no enforcement, root, listener, shell,
  background, or general command responsibility and does not depend on the
  `macosHelper` package.
- Implement the ADR 0007 item contract exactly: generic-password class,
  service `app.posato.sync.workspace-key.v1`, canonical UUID account, the
  signed access group with public suffix `app.posato.sync`, synchronizable,
  after-first-unlock, and `kSecUseDataProtectionKeychain` on every query;
  84-byte value with CRC-32 validation; `created`, `identical`, `conflict`,
  `missing`, `retryable`, `account-changed`, `unknown-outcome`, and
  `integrity-failure` outcomes; no `SecItemUpdate`, persistent reference, or
  broad deletion.
- Resolve the local account binding in the companion from the current
  CloudKit user record ID exactly as ADR 0007 defines, and run the preflight
  and postflight around every Keychain operation; an unavailable or changed
  postflight discards read bytes and returns `unknown-outcome`.
- Define the companion IPC as one bounded major version with allowlisted
  operations, frame and field limits, request identity, deadline, cancel, and
  structured outcomes, leaving unused operation codes and a capability field
  so `SYNC-008` adds CloudKit zone, anchor, and mailbox operations without a
  protocol break. Secrets never enter arguments, environment, logs, error
  text, or diagnostics.
- Implement in `shared/src/jvmMain` the JVM launcher (private inherited
  pipes, embedded-companion signature verification before use, deadline,
  cancellation, timeout reconciliation to `UnknownOutcome`, buffer clearing)
  and the JVM `BootstrapKeyPort` and `BootstrapAccountPort` over it. Ports
  and `BootstrapEncoding` are frozen; an insufficiency is a recorded blocker.
- Embed the companion at one fixed application-owned path, sign it with its
  own identifier and entitlements in both credential-free and Apple
  Development modes, and extend the strict inside-out verification task so a
  wrong identifier, Team ID, or entitlement set fails packaging. Ad-hoc
  packaging keeps launching; the companion then reports `unavailable` or
  `restricted` truthfully instead of crashing.
- No DI wiring, UI, **Sync with iCloud** action, CloudKit zone or mailbox
  operation, explicit workspace removal, cross-device bootstrap, or
  distribution claim; those belong to `SYNC-008` and `SYNC-009`.
- Exclusive write surface while `SESSION-001` and `SYNC-005` run in parallel:
  new `macosSyncCompanion/**`; `settings.gradle.kts` (include the module);
  the root `build.gradle.kts` only to add the module's `check` to the
  aggregate quality gate; `desktopApp/build.gradle.kts` and
  `desktopApp/Config/**` (embed, sign, provision, extend verification);
  `shared/src/jvmMain/**/feature/sync/**` and
  `shared/src/jvmTest/**/feature/sync/**`; `tools/posato-control/**` only if
  the package build needs a flag. Shared with `SYNC-005` and resolved by
  rebase: `docs/wiki/topics/cross-device-synchronization.md` and
  `docs/wiki/log.md`. Do not touch `shared/src/commonMain/**`,
  `shared/src/jvmMain/kotlin/app/posato/di/**`, `desktopApp/src/**`,
  `macosHelper/**`, `iosApp/**`, `feature/targets/**`, `feature/session/**`,
  `shared/build.gradle.kts`, or `gradle/libs.versions.toml`.

## Acceptance

- `AC-01` — Read, create-if-absent, and delete-and-verify-absent use the
  exact ADR 0007 selector and return the exact outcome for absent, identical,
  different, malformed-length, bad-checksum, locked, unavailable, and
  restricted items; a different existing value is never replaced and no
  operation issues `SecItemUpdate` or a selector-less delete.
- `AC-02` — A binding mismatch before an operation returns `account-changed`
  without any `SecItem` query; a mismatch or unavailable value after it
  returns `unknown-outcome`, exposes no read bytes, and confirms no deletion
  or absence.
- `AC-03` — The JVM refuses an unverified, substituted, or wrong-identifier
  companion, and the companion refuses an unsigned, wrong-identifier,
  wrong-team, or non-embedding parent, a malformed, oversized, unknown-version,
  or unknown-operation frame, and a second request; a deadline, cancel, or
  lost response yields `UnknownOutcome` and leaves no lingering process.
- `AC-04` — Both signing modes produce a package whose companion carries the
  expected identifier, Team ID rule, and exact entitlement set, passes the
  strict verifier, and launches on the supported Mac; the Apple Development
  package performs one real create, identical re-create, read, and
  delete-and-verify-absent round trip in the maintainer's iCloud Keychain and
  leaves no item behind.
- `AC-05` — No workspace-key, binding, account, or Apple error value appears
  in arguments, environment, logs, `toString()`, diagnostics, or test
  fixtures; `./gradlew quality` passes, including the companion's format,
  lint, and Swift tests.

## Verification

- Swift tests for framing limits, operation allowlist, item encoding and
  checksum validation, outcome mapping, and parent-verification failure
  paths; JVM tests over an in-process fake companion and a scripted fake
  executable for every port outcome, timeout, cancellation, malformed
  response, and buffer clearing; the enumerated redaction test for new
  carriers.
- Packaging verification in credential-free and Apple Development modes,
  including a deliberate wrong-entitlement and wrong-identifier probe that the
  extended verifier must reject; `./gradlew quality`; `git diff --check`;
  suppression and private-data scans.
- Physical Mac checklist with a synthetic workspace id: entitlement and
  signed-access proof, the `AC-04` round trip, a locked-keychain read, and
  an account sign-out during a read observed as `unknown-outcome`, recorded
  under `build/verification/` without item bytes. Delayed propagation and
  byte-identical selectors against iOS wait for `SYNC-009`.
- Independent plan review before implementation and independent
  completed-change review with evidence.

## Decisions or blockers

- Manual gate: the companion needs `com.apple.developer.icloud-services`,
  `com.apple.developer.icloud-container-identifiers` for
  `iCloud.app.posato.sync`, and `keychain-access-groups` with the
  `app.posato.sync` suffix, so the maintainer must create or let Xcode
  generate a development provisioning profile for `app.posato.macos.sync`
  and place it outside Git; the parent `app.posato.macos` package needs no
  new entitlement (recommended, to be confirmed by the physical gate).
- Recommended: a separate Swift package without a dependency on
  `PosatoMacOSServiceCore`; re-derive the signing-requirement and big-endian
  framing pattern rather than importing enforcement types.
- Recommended: the IPC uses the helper's framing style (versioned length-
  prefixed frames, request identity, capability bits) but a separate
  operation table and major version, since the peers, entitlements, and
  lifecycle differ.
- Recommended: the account binding is resolved in the companion for every
  request, mirroring `SYNC-005`; the JVM never sees the record ID.
- Open: how account change is simulated on one Mac. Recommended: an iCloud
  sign-out between preflight and postflight driven by the maintainer during
  the physical checklist, plus a fake-provider test for the ordered outcome.
- Open: the companion launcher lives in `shared/src/jvmMain` because the
  `SYNC-004` ports are `internal` to `:shared`, unlike the helper client in
  `desktopApp/src`; the plan review confirms this placement.
