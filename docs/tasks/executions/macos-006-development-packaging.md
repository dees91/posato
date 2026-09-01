# Execution: `MACOS-006`

- **Brief:** [`../specifications/macos-006-development-packaging.md`](../specifications/macos-006-development-packaging.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** `Codex`
- **Reviewer:** independent Codex reviewer
- **Branch:** `feature/macos-006-development-packaging`
- **Updated:** `2026-09-01`

## Plan

1. Replace the partial outer/helper signing chain with one minimal inside-out development-packaging task while preserving Compose's credential-free ad-hoc output.
2. Replace the permissive package check with strict structure, identity, nested-code, and entitlement verification wired into package, launch, and aggregate-quality tasks.
3. Prove the old artifact fails the new invariant, then run both signing modes, exact packaged launch, aggregate quality, and independent completed-change review.
4. Record only the reusable observed packaging correction and open the focused GitHub pull request.

## High-risk plan review

- **Verdict:** `approved after required corrections`
- **Critical or Required findings:** distinguish Apple Development authority from another team-bearing identity; enforce the complete entitlement surface in both signing modes and reject helper or daemon entitlements.
- **Resolution:** the verifier will require the Apple Development authority and exact identifiers, and will compare every application, helper, and daemon entitlement set with its closed expected set.

## Result

- The Gradle pipeline now signs the complete current arm64 package surface inside-out in both credential-free and Apple Development modes, including the SQLite JDBC native library inside its JAR.
- One verifier cryptographically verifies each enumerated code object before reading its metadata, then enforces exact identifiers, entitlement sets, and signing relationships for the runtime, Skiko, SQLite, helper, and daemon. Apple Development additionally requires one shared Team ID and the Apple Development authority.
- The verified application is staged as an immutable DMG input. The planned JPackage installer path was replaced with Compose's native DMG task after controlled verification showed that JPackage rewrote nested executables and invalidated the helper resource seal.
- The replacement DMG task remains part of Compose's standard `packageDistributionForCurrentOS` and `package` lifecycle instead of requiring callers to know its implementation task name.
- Both final DMGs passed strict verification and launched directly from mounted images with isolated normal SQLite stores. No manual copy or re-signing was used.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** the initial ad-hoc verifier did not enumerate SQLite-in-JAR and every other nested signature, recreating the original `--deep` blind spot. Hosted reviews later found that the replacement DMG task was disconnected from Compose's aggregate packaging lifecycle and that enumerated signatures were displayed without individual cryptographic verification.
- **Resolution:** both signing modes and the shared verifier enumerate the same inside-out package surface, every metadata read first performs strict verification, and the replacement DMG task is a dependency of `packageDistributionForCurrentOS`. Focused re-review found no remaining Critical or Required defect.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| High-risk plan review | `pass` | Approved after the two Required verifier corrections were incorporated. |
| Baseline regression probe | `pass` | The new verifier rejected the former Apple Development package at its first ad-hoc nested signature. |
| Nested-signature corruption probe | `pass` | Outer deep verification accepted the resealed application while the verifier rejected its corrupted SQLite-in-JAR code. |
| Credential-free package and launch | `pass` | Full nested verification, configuration-cache reuse, strict mounted-DMG verification, and isolated packaged launch passed. |
| Apple Development package and launch | `pass` | Authority, shared-team, identifier, entitlement, strict mounted-DMG, and isolated packaged launch checks passed. |
| Compose aggregate packaging lifecycle | `pass` | Both aggregate task graphs include the replacement DMG task; `packageDistributionForCurrentOS` built a strictly verified mounted DMG and reused the configuration cache. |
| `./gradlew quality --rerun-tasks` | `pass` | All 115 tasks passed after the final individual-signature correction. |
| Independent completed-change review | `pass` | Focused re-reviews approved the enumeration, aggregate-lifecycle, and cryptographic-verification corrections with no remaining finding. |

## Blockers and accepted risks

- A local Apple Development identity remains runtime-only input for its physical package gate. Developer ID, notarization, x86_64, and public distribution remain outside this task.
- The result is bounded to the supported Apple silicon Mac and current JDK 21 and Compose toolchain.

## Final

- **Status:** `done`
- **Outcome:** met
