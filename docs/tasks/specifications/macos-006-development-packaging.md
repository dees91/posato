# `MACOS-006`: Launchable development-signed macOS package

- **Review tier:** `high-risk`
- **Tier reason:** The task changes the signing and entitlement boundary of the packaged macOS application and nested helper.
- **Dependencies:** `MACOS-003`
- **Integration group:** `PR-MAC-DEV-PACKAGING`
- **Authority:** `docs/tasks/mvp-roadmap.md`

## Outcome

The unchanged Gradle-produced application passes strict nested-signature verification and launches on the supported physical Mac without manual re-signing.

## Boundaries

- Keep the existing Apple Development identity property as runtime-only input and preserve credential-free ad-hoc packaging.
- Sign only the current arm64 JVM, SQLite JDBC, Skiko, helper, and daemon package surface with the minimum required development entitlement.
- Keep Developer ID signing, notarization, public distribution, x86_64 packaging, and helper lifecycle re-verification outside this task.

## Acceptance

- `AC-01` — Apple Development packaging gives the exact application, runtime Mach-O files, arm64 SQLite JDBC library, Skiko, helper, and daemon one nonempty Team ID and the expected identifiers.
- `AC-02` — The Apple Development authority is verified; the application carries exactly the JIT entitlement, while the helper and daemon carry none.
- `AC-03` — The exact Gradle-produced application launches on the supported physical Mac and initializes its normal SQLite-backed runtime without manual repair.
- `AC-04` — Credential-free ad-hoc packaging remains strictly verifiable and launchable through the documented Gradle path.

## Verification

- Prove the current package fails the new relationship check before correcting the signing order.
- Run focused ad-hoc and Apple Development package verification, configuration-cache reuse, packaged launch smoke, and the fresh aggregate quality gate.

## Decisions or blockers

- Current-repository experiments establish that the Apple Development package needs only `com.apple.security.cs.allow-jit`; any broader entitlement requires maintainer approval.
- Credential-free packaging retains exactly the three current Compose JVM entitlements and no helper or daemon entitlements.
