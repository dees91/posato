# `SYNC-005`: Access the synchronizable workspace key on iOS

- **Specification status:** `accepted`
- **Specification revision:** `1`
- **Accepted on:** 2026-08-25
- **Accepted by:** Project maintainer
- **Acceptance provenance:** `user-confirmed`
- **Direct blocking dependencies:** `SYNC-003`
- **Stable concurrency constraints:** iOS Keychain, access-group, entitlement,
  signing, and Xcode project ownership are serialized against other iOS
  Apple-adapter work. The cross-platform item contract is frozen by
  `SYNC-003` and is not owned by this task.
- **Roadmap reference:** [Gate 6 task map](../mvp-roadmap.md#task-and-integration-group-map)
- **Risk:** high

> Keep this specification outcome-focused. Exact files, APIs, dependency
> versions, algorithms, and implementation steps belong in the execution
> record after the task is authorized.

## Outcome

The iOS application reads and writes the synchronizable workspace key through the accepted semantic adapter with correct confidentiality, delay, account, and cleanup outcomes.

## Context

The Apple bootstrap contract needs an iOS-native Keychain adapter whose results remain semantic and whose secret material never crosses diagnostics or repository evidence.

## In scope

- Provide accepted create, read, update, missing, delayed, denied, corrupt, account-changed, and unavailable outcomes.
- Protect key confidentiality across storage, memory exposure, diagnostics, fixtures, and errors.
- Implement the accepted cross-platform item identity, attributes, mutation,
  deletion, versioning, synchronizable, and account-isolation contract on iOS.
- Verify restart and cleanup behavior on a physical iPhone.

## Out of scope

- Bootstrap orchestration, CloudKit access, or onboarding UI.
- Portable workspace export, recovery codes, or total-key-loss recovery.
- Logging, synchronizing, or exposing plaintext workspace key material.

## Acceptance criteria

- `AC-01` — On a physical iPhone, the adapter can create and later retrieve the same synchronizable workspace key under the accepted eligibility rules.
- `AC-02` — Missing, delayed, denied, corrupt, account-changed, unavailable, and restart outcomes map to the accepted semantic contract without fabricating a second key.
- `AC-03` — Workspace key material is absent from logs, diagnostics, tracked fixtures, UI, crash evidence, and CloudKit payloads.
- `AC-04` — Access-group, synchronization, update, deletion, versioning, and
  account-isolation behavior conforms to the frozen cross-platform item
  contract and passes the controlled physical matrix.
- `AC-05` — The adapter remains replaceable and does not leak Keychain types into shared bootstrap logic.

Results and evidence for these immutable criteria belong only in the execution
record.

## Required evidence categories

- Semantic adapter contract tests with fakes.
- Physical iPhone Keychain create/read/delay/restart/account/error/cleanup matrix.
- Secret, entitlement, privacy, and memory-exposure review.
- Aggregate quality gate and independent security review.

Every category not applicable to this task must receive a reasoned `N/A` in
the execution record. The standing Definition of Done in the engineering
quality contract applies in full.

## Review applicability

| Area | Applies (`yes`, `no`, or `unknown`) | Reason and required focused review or authority update |
| --- | --- | --- |
| Untrusted input | yes | Validates missing, delayed, corrupt, incompatible, denied, unavailable, and account-changed Keychain items and outcomes. |
| Authentication or authorization | yes | Enforces the intended Apple account and access-group boundary through semantic outcomes. |
| Secrets, signing, or credentials | yes | Handles the workspace key and prohibits it from UI, logs, diagnostics, fixtures, crash evidence, and CloudKit. |
| Personal data or diagnostics | yes | Keeps account and Keychain error details bounded and redacted. |
| Storage or migration | yes | Implements iOS item creation, read, update, restart, deletion, and account-isolation behavior against the cross-platform contract frozen by `SYNC-003`; it does not redefine that contract. |
| Cryptography | yes | Handles a cryptographic root secret even though operation encryption remains in `SYNC-002`; focused secret-lifecycle review applies. |
| Native IPC or entitlements | yes | Implements the iOS Keychain, access-group, entitlement, and lifecycle boundary. |
| External services | yes | Synchronizable Keychain delay, account, authorization, restart, and cleanup require physical iPhone evidence. |
| Dependencies or licenses | no | The iOS Keychain adapter outcome does not add or select a production dependency or licensed tool. |
| PoC reuse or external provenance | yes | PoC Keychain evidence is bounded and needs fresh production ownership and physical validation. |

## Decision gates

- The required access group, signing, and synchronizable Keychain behavior must be available.
- The cross-platform item identity, attributes, lifecycle, and compatibility
  rules accepted by `SYNC-003` must remain frozen. Any required contract change
  returns to that decision task or serializes the affected adapter work.
- Physical delayed-delivery and account-state evidence may block completion when Apple-managed behavior cannot be observed.

## References

- [MVP roadmap](../mvp-roadmap.md)
- [Engineering quality contract](../../docs/development/engineering-quality-contract.md)
- [MVP scope](../../docs/product/mvp-scope.md)
- [MVP architecture baseline](../../docs/decisions/0003-mvp-application-architecture-baseline.md)
- [Synchronization trust boundary](../../docs/decisions/0002-synchronization-trust-and-workspace-modes.md)
