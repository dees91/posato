# Execution: `SYNC-006`

- **Brief:** [Implement the macOS synchronizable-Keychain companion boundary](../specifications/sync-006-macos-keychain.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Grok
- **Reviewer:** pending completed-change review
- **Branch:** `feature/sync-006-macos-keychain`
- **Worktree:** `~/Projects/Polyglot/posato-sync-006`
- **Updated:** 2026-09-04

## Plan

1. Record the approved plan (module in `shared/src/jvmMain`, separate one-shot
   IPC, companion-side binding, entitlement guard, constructor-supplied app
   root) and the shared preflight table with `SYNC-005`.
2. Create `:macosSyncCompanion` with encoding, exact Keychain queries, binding
   resolution, parent verification, entitlement guard, one-shot framing, and
   Swift tests; add its `check` to the aggregate quality gate.
3. Implement the JVM verifier, client, and frozen-port adapters with
   deadlines, cancellation, timeout reconciliation, request-identity matching,
   buffer clearing, and tests over an in-process fake and a scripted process.
4. Embed, sign, and provision the companion in both packaging modes and
   extend the strict verifier, including wrong-identifier and
   wrong-entitlement rejection.
5. Stop for the maintainer Keychain Sharing and development-profile gate,
   then run the physical Mac checklist, `./gradlew quality`, completed-change
   review, wiki closeout, and the GitHub pull request.

## High-risk plan review

- **Verdict:** `approved after required corrections`
- **Critical or Required findings:** (1) guard entitlements with
  `SecTaskCopyValueForEntitlement` before any CloudKit or `SecItem` call so
  ad-hoc signing cannot throw; (2) read the access group from that same
  entitlement value, never construct a Team ID; (3) map preflight unavailable
  or restricted key operations to `Retryable`, a different account to
  `AccountChanged`, and postflight mismatch, timeout, kill, or malformed
  identity to `UnknownOutcome`; (4) echo magic, major, operation, and
  request identity on every response.
- **Resolution:** the approved plan incorporates all four, plus constructor
  path injection, Keychain Sharing on the companion App ID, keeping `HOME`
  in a cleared environment, a per-frame 65,536-byte payload limit, and
  launching the scripted fake with `java.home` and `java.class.path`.

## Preflight mapping (aligned with `SYNC-005`)

| Native condition | Provider access | Account port | Key port |
| --- | --- | --- | --- |
| Missing iCloud or Keychain Sharing entitlements | none | `Unavailable` | `Retryable` |
| Binding unavailable, restricted, or undetermined | none | matching `BindingResolution` | `Retryable` |
| Current binding ≠ expected | none | n/a | `AccountChanged` |
| Postflight mismatch, timeout, kill, malformed or wrong-identity response | discard bytes; do not confirm delete | `Undetermined` | `UnknownOutcome` |
| Identical existing item | no replace | n/a | `AlreadyExists` |
| Different existing item | no replace | n/a | `IntegrityFailure` |

## Result

- Added `:macosSyncCompanion` with exact Keychain selectors, CRC-32 item
  codec, entitlement guard, parent verification, and one-shot IPC.
- JVM adapters live in `shared/src/jvmMain` over a constructor-supplied
  companion path; they are not wired into Metro.
- Ad-hoc packaging embeds and signs `PosatoMacOSSync.app`. Apple Development
  Keychain round-trip is blocked until the maintainer enables Keychain Sharing
  on `app.posato.macos.sync` and supplies the untracked profile.

## Completed-change review

- **Verdict:** `approved after required corrections`
- **Critical or Required findings:** (1) ad-hoc parent verification required a Team ID;
  (2) the JVM verifier was unused; (3) packaging probes did not run the
  verifier contract; (4) no `CKAccountChanged` observer during `SecItem`.
- **Resolution:** ad-hoc peers match on identifier and nested path only;
  `MacOsSyncCompanionClient.verified` is the production factory; probes
  require `acceptAdHocCompanion` to fail; Keychain ops observe
  `.CKAccountChanged`. Affected Swift/JVM/packaging checks were rerun.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Swift tests, format, lint | pass | `:macosSyncCompanion:check`, 27 tests |
| JVM protocol, adapter, fake-process tests | pass | `:shared:jvmTest` |
| Ad-hoc package, nested companion, negative probes | pass | `:desktopApp:verifyMacOsDevelopmentPackaging`, `:desktopApp:probeMacOsSyncCompanionPackaging` |
| Desktop smoke launch | pass | posato-control `wait` for "Add website"; run `20260904-100928-fa99` |
| `./gradlew quality` | pass | 139 tasks after the completed-change corrections |
| Apple Development Keychain round-trip | blocked | Needs Keychain Sharing on `app.posato.macos.sync` and an untracked development profile |

## Blockers and accepted risks

- A development provisioning profile for `app.posato.macos.sync` with iCloud
  and Keychain Sharing must exist on the maintainer's Mac before the Apple
  Development package gate; it is never tracked.
- Delayed propagation and byte-identical selectors against the iOS adapter
  are `SYNC-009` evidence, not claimed here.

## Final

- **Status:** `blocked`
- **Outcome:** code increment met; AC-04 Apple Development round-trip waits on the maintainer profile gate
