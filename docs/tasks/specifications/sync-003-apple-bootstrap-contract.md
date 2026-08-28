# `SYNC-003`: Accept the Apple one-workspace bootstrap contract

- **Review tier:** `high-risk`
- **Tier reason:** The task freezes a secret-storage and cloud trust boundary and adds an irreversible Apple App ID.
- **Dependencies:** `SYNC-001`, `APPLE-001`
- **Integration group:** `PR-APPLE-SYNC-CONTRACT`
- **Authority:** [MVP roadmap](../mvp-roadmap.md)

## Outcome

An accepted decision defines one interoperable CloudKit mailbox, one
synchronizable-Keychain workspace-key item, deterministic one-workspace
bootstrap, and a dedicated app-owned macOS native synchronization boundary.

## Boundaries

- Freeze only the minimum provider-visible schema, secure-item selectors,
  semantic outcomes, conflict behavior, account isolation, and native process
  ownership required by `SYNC-004` through `SYNC-009`.
- Register and verify `app.posato.macos.sync` with iCloud/CloudKit access to the
  existing `iCloud.app.posato.sync` container, without recording private Apple
  account, signing, provisioning, or team values.
- Keep `.research/blocker` read-only feasibility evidence. Do not copy its
  combined helper, runtime, identifiers, dependency choices, or prototype
  schema into product authority.
- Do not add production code, a native target, dependencies, CloudKit schema
  deployment, entitlements, provisioning, UI, retry timing, or physical
  synchronization claims.

## Acceptance

- `AC-01` — An accepted ADR freezes the minimal Keychain item, CloudKit zone,
  anchor, mailbox record, bootstrap arbitration, and truthful outcome contracts.
- `AC-02` — The macOS synchronization companion is distinct from the enforcement
  helper and has a bounded, unprivileged, app-owned native boundary.
- `AC-03` — Concurrent first runs converge on one CloudKit anchor; delay,
  restart, account change, corruption, and conflict never create a replacement
  key or parallel workspace.
- `AC-04` — The new explicit App ID and existing CloudKit-container association
  have a truthful manual pass or a recorded blocker and clearing condition.
- `AC-05` — Architecture, security, roadmap, resource, and wiki authorities are
  consistent without rewriting the historical `APPLE-001` result.

## Verification

- Independent high-risk plan and completed-change reviews.
- Manual Apple Developer resource and container-association inspection.
- Contract scenario traceability, documentation consistency, link checking,
  `git diff --check`, and a scoped sensitive-data scan.

## Decisions or blockers

- `user-confirmed` (2026-08-28): use a dedicated macOS synchronization
  companion, register its App ID in `SYNC-003`, and arbitrate concurrent first
  runs automatically through one create-only CloudKit anchor.
- Human Apple Developer access is required after plan approval; a failed or
  unavailable identifier blocks completion rather than authorizing a fallback.
