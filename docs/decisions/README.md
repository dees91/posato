# Architecture Decisions

This directory contains accepted product architecture decision records.

The synchronization PoC used a scope-specific ADR, but that experiment decision
does not automatically become a production decision. Relevant evidence and
constraints are synthesized in the wiki. A new ADR belongs here only after the
maintainer explicitly accepts it for the MVP.

Numbering continues the feasibility decision history without copying the
feasibility ADR into this repository. ADR 0001 remains historical source
evidence at the revision recorded in the wiki. Accepted product ADRs are:

- [ADR 0002: Separate Synchronization Trust and Workspace Modes](0002-synchronization-trust-and-workspace-modes.md)
  — accepted Apple and portable transport, encryption, key-delivery, device
  admission, and migration boundaries.
- [ADR 0003: Establish the MVP Application Architecture Baseline](0003-mvp-application-architecture-baseline.md)
  — accepted module, source-set, Metro, Apple target, native-process,
  deployment, generator-import, and toolchain-selection boundaries.
- [ADR 0004: Define macOS Helper Ownership and Lifecycle](0004-macos-helper-ownership-and-lifecycle.md)
  — accepted macOS process, privilege, IPC, authorization, proxy ownership,
  recovery, update, and removal boundaries.
- [ADR 0005: Bound macOS Browser Enforcement and Coexistence](0005-macos-browser-enforcement-and-coexistence.md)
  — accepted Safari and Chrome support, exact-domain denial, fixed browser
  presentation, proxy coexistence, privacy, failure, and recovery boundaries.
- [ADR 0006: Apple MVP Encrypted Operation and Convergence Contract](0006-apple-mvp-encrypted-operation-and-convergence.md)
  — accepted encrypted bundle, canonical encoding, cryptographic provider,
  automatic author-registration, validation, and convergence boundaries.
- [ADR 0007: Apple Workspace Bootstrap and macOS Native Sync Boundary](0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
  — accepted CloudKit mailbox, synchronizable-Keychain item, deterministic
  one-workspace bootstrap, and dedicated macOS synchronization companion.
