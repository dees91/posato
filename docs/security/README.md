# Security and Privacy Baseline

This directory is the home for accepted security and privacy authorities,
including the threat model, cryptographic design, data inventory, retention
and deletion policy, diagnostic policy, and security reporting process.

[`apple-mvp-threat-model.md`](apple-mvp-threat-model.md) is the accepted
`SECURITY-001` authority for the Apple MVP assets, trust boundaries, threats,
required controls, owners, and residual risks.

The current feasibility synthesis is in
[`docs/wiki/topics/privacy-and-trust-model.md`](../wiki/topics/privacy-and-trust-model.md).
It records bounded evidence and open questions rather than a production
security claim.

The accepted synchronization trust split is recorded in
[ADR 0002](../decisions/0002-synchronization-trust-and-workspace-modes.md).
It selects Apple Account and iCloud Keychain trust for Apple-workspace
admission while retaining application-layer E2EE and explicit membership for
the later portable workspace. The accepted threat model does not select the
still-open production cryptographic design.
