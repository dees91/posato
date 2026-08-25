# Wiki Log

## [2026-08-24] ingest | Seed MVP knowledge from feasibility work

- Created the embedded wiki and mandatory repository routing.
- Synthesized product, architecture, synchronization, enforcement, privacy,
  reuse, and open-decision knowledge from the recorded research revision.
- Kept experiment code, execution artifacts, and machine-local configuration
  with the feasibility work instead of treating them as an MVP baseline.
- Preserved every feasibility result as bounded evidence rather than a
  production or MVP claim.

## [2026-08-24] correction | Focus the repository narrative on product evidence

- Reframed the repository around a fresh MVP implementation informed by the
  completed synchronization PoC and enforcement spike.
- Retained experiment provenance, production limits, and the optional read-only
  feasibility reference without making research history part of the product
  introduction.

## [2026-08-24] decision | Set the route to the first MVP code pull request

- Defined the next milestone as the first pull request containing production
  application code rather than another PoC or architecture spike.
- Accepted seven preceding gates: MVP scope, product identity, product and
  design baseline, architecture baseline, quality contract, vertical PR
  decomposition, and manual Apple resource setup.
- Fixed PR #1 to a fresh KMP application skeleton with accepted identifiers, a
  minimal shared Compose screen on macOS and iOS, semantic platform contracts
  with fakes, baseline tests, and CI.
- Excluded blocking and synchronization implementation from PR #1 and routed
  current work to MVP scope and minimum product identity.

## [2026-08-25] decision | Accept the first MVP scope

- Included website and application blocking, bounded manual sessions, Apple
  synchronization, and first- and second-device onboarding on macOS and iOS.
- Required exact domain synchronization and semantic application-policy
  synchronization with device-local platform selections.
- Allowed intentional early session termination while deferring stronger UX
  friction, schedules, and total-key-loss recovery.
- Accepted a current-plus-previous-major OS support policy and a physical Mac
  and iPhone end-to-end product-flow measure.
- Excluded product accounts and relays, behavioral tracking, Android and Linux
  implementation, cloud-delivery guarantees, and administrator-resistant
  claims.

## [2026-08-25] decision | Separate synchronization trust by workspace mode

- Accepted CloudKit Private Database transport, synchronizable-Keychain
  workspace-key delivery, and Apple Account/iCloud Keychain membership for the
  Apple MVP.
- Replaced Blocker QR and cross-device approval in Apple onboarding with one
  **Sync with iCloud** action per installation and a required missing-key
  waiting state.
- Retained one common application-encrypted, authenticated, and signed
  operation format for CloudKit and portable-folder transports.
- Retained explicit device identity, membership, QR approval, per-device
  wrapping, key epochs, revocation, and optional recovery for later portable
  mode.
- Required a new portable workspace and key epoch for migration, with no live
  bridge, dual-write, or parallel CloudKit-folder authority.
- Recorded the production decision in ADR 0002 while preserving feasibility
  ADR 0001 and PoC results as historical evidence.
