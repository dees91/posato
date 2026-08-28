# Security and Privacy Baseline

This directory is the home for accepted security and privacy authorities,
including the threat model, cryptographic design, data inventory, retention
and deletion policy, diagnostic policy, and security reporting process.

[`apple-mvp-threat-model.md`](apple-mvp-threat-model.md) is the accepted
`SECURITY-001` authority for the Apple MVP assets, trust boundaries, threats,
required controls, owners, and residual risks.

[`diagnostics-and-support-data.md`](diagnostics-and-support-data.md) is the
accepted `DIAGNOSTICS-001` authority for diagnostic purpose, fields,
prohibited data, consent, local expiry and cleanup, support export, producer
verification, and the absence of automatic remote collection.

The current feasibility synthesis is in
[`docs/wiki/topics/privacy-and-trust-model.md`](../wiki/topics/privacy-and-trust-model.md).
It records bounded evidence and open questions rather than a production
security claim.

The accepted synchronization trust split is recorded in
[ADR 0002](../decisions/0002-synchronization-trust-and-workspace-modes.md).
It selects Apple Account and iCloud Keychain trust for Apple-workspace
admission while retaining application-layer E2EE and explicit membership for
the later portable workspace. ADR 0006 defines the Apple MVP cryptographic
format and provider boundary; their implementation evidence remains downstream.

[ADR 0007](../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md)
defines the exact Apple mailbox, synchronizable-Keychain item, deterministic
bootstrap, and dedicated macOS native synchronization boundary. Their controls
remain unverified until `SYNC-004` through `SYNC-010` provide implementation and
physical evidence.
