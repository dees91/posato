# `SYNC-006`: Access the synchronizable workspace key on macOS

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-003`
- **Stable concurrency constraints:** macOS Keychain and native-bridge
  ownership are isolated from other macOS Apple-adapter work. The
  cross-platform item contract is frozen by `SYNC-003` and is not owned by
  this task.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The macOS application reads and writes the synchronizable workspace key through the accepted semantic native boundary with correct confidentiality, delay, account, and cleanup outcomes.

## Context

The Kotlin/JVM macOS application needs the app-owned native Keychain access
boundary accepted by `SYNC-003`, compatible with the same semantic bootstrap
contract as iOS and separate from the enforcement helper.

## In scope

- Provide accepted create, read, update, missing, delayed, denied, corrupt, account-changed, and unavailable outcomes.
- Protect key confidentiality across the native bridge, diagnostics, fixtures, and errors.
- Implement the accepted cross-platform item identity, attributes, mutation,
  deletion, versioning, synchronizable, and account-isolation contract on
  macOS.
- Verify restart and cleanup behavior on the physical Mac.

## Out of scope

- Bootstrap orchestration, CloudKit access, or onboarding UI.
- Giving the privileged enforcement helper access to synchronization keys.
- Portable workspace export, recovery codes, or total-key-loss recovery.

## Acceptance criteria

- `AC-01` — On the physical Mac, the adapter can create and later retrieve the same synchronizable workspace key under the accepted eligibility rules.
- `AC-02` — Missing, delayed, denied, corrupt, account-changed, unavailable, and restart outcomes map to the accepted semantic contract without fabricating a second key.
- `AC-03` — Workspace key material is absent from logs, diagnostics, tracked fixtures, UI, helper IPC, crash evidence, and CloudKit payloads.
- `AC-04` — Synchronization, update, deletion, versioning, and
  account-isolation behavior conforms to the frozen cross-platform item
  contract and passes the controlled physical matrix.
- `AC-05` — The native boundary remains replaceable and does not leak Keychain types or secret access into shared logic.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Semantic adapter and native-boundary contract tests.
- Physical Mac Keychain create/read/delay/restart/account/error/cleanup matrix.
- Secret, privacy, native-boundary, and memory-exposure review.
- Aggregate quality gate and independent security review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates missing, delayed, corrupt, incompatible, denied, unavailable, and account-changed Keychain items and native outcomes. |
| Authentication or authorization | yes | Enforces the intended Apple account and synchronizable-item boundary through semantic outcomes. |
| Secrets, signing, or credentials | yes | Handles the workspace key and prohibits it from UI, logs, diagnostics, fixtures, helper IPC, crash evidence, and CloudKit. |
| Personal data or diagnostics | yes | Keeps account, app-owned native-access, and Keychain error details bounded and redacted. |
| Storage or migration | yes | Implements macOS item creation, read, update, restart, deletion, and account-isolation behavior against the cross-platform contract frozen by `SYNC-003`; it does not redefine that contract. |
| Cryptography | yes | Handles a cryptographic root secret even though operation encryption remains in `SYNC-002`; focused secret-lifecycle review applies. |
| Native IPC or entitlements | yes | Implements the app-owned macOS Keychain access boundary accepted by `SYNC-003`, never the enforcement helper. |
| External services | yes | Synchronizable Keychain delay, account, authorization, restart, and cleanup require physical Mac evidence. |
| Dependencies or licenses | no | The macOS Keychain adapter outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | PoC Keychain evidence is bounded and needs fresh production ownership and physical validation. |

## Decision gates

- The app-owned macOS native-access boundary selected by `SYNC-003` and the
  required synchronizable Keychain behavior must be available.
- The cross-platform item identity, attributes, lifecycle, and compatibility
  rules accepted by `SYNC-003` must remain frozen. Any required contract change
  returns to that decision task or serializes the affected adapter work.
- This task cannot create or reuse a synchronization process, target,
  identifier, or capability that is absent from the accepted architecture and
  Apple resource record.
- Physical delayed-delivery and account-state evidence may block completion when Apple-managed behavior cannot be observed.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
