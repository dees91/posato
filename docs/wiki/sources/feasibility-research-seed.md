# Feasibility Research Seed

## Provenance

- **Ingest date:** 2026-08-24
- **Source class:** synchronization PoC and enforcement spike repository
- **Source revision:** `bcdc8ce9b91ecb7569c2d98b568d5fd64c25455c`
- **Destination:** maintained MVP knowledge wiki
- **Production code imported:** none

The research repository combined product discovery, maintained synthesis, two
controlled Apple feasibility efforts, application-like PoC entry points, test
harnesses, and experimental implementation. This seed captures the durable
product and engineering conclusions needed for MVP work.

## Reviewed source classes

The ingest reviewed:

- the maintained product, synchronization, enforcement, selective website
  blocking, and privacy topic synthesis;
- the accepted Apple-first KMP/Compose synchronization ADR within its PoC scope;
- final synchronization and enforcement reports and their
  machine-readable classifications;
- the shared Kotlin operation model, merge rules, encrypted envelope,
  persistence, synchronization, lifecycle, enrollment, recovery, membership,
  deletion, and platform-enforcer boundaries;
- desktop JVM helper contracts, authenticated local process protocol, native
  gateway, and platform-enforcer integration;
- native Apple CloudKit, Keychain, proxy, system-settings, application-control,
  browser-presentation, Family Controls, and Managed Settings adapters;
- common, JVM, native, Swift, signed-runtime, physical-matrix, failure, privacy,
  and cleanup tests;
- the PoC build manifest and dependency versions as reproducibility
  evidence rather than MVP version requirements.

## Evidence promoted into synthesis

`observed`: the synchronization PoC reached a final PASS only after an audit
withdrew an earlier insufficient result and the complete runtime was routed
through the normal desktop and iOS application entry points. The corrected
matrix used one physical Mac and one physical iPhone, repeated the complete
exchange, preserved valid state across required failures, passed a clean-source
configuration check, and performed exact cleanup.

`observed`: the enforcement spike reached a final PASS for its named macOS and
iOS rows. It proved selected mechanisms with synthetic targets and development
authorization, then removed all project-owned restrictions and temporary
state. It did not establish production persistence, bypass resistance,
distribution eligibility, or broad browser and device coverage.

`user-confirmed`: the product direction is Kotlin Multiplatform plus Compose
Multiplatform, Kotlin-first ownership, semantic platform interfaces, and Swift
only where direct Kotlin/Native integration is impractical. The exact
production boundary is intentionally deferred to small reviewed MVP slices,
not a separate pre-MVP architecture spike.

## Synthesis approach

- Knowledge is organized by current product and engineering questions rather
  than experiment chronology.
- Every feasibility result retains its tested topology and production limits.
- PoC ownership is recorded as evidence and reuse guidance, not as a production
  module prescription.
- Synthetic test details appear only when they define an evidence boundary.
- Tool versions remain reproducibility evidence; the MVP baseline will select
  and verify current compatible versions separately.

## Seed scope

The knowledge seed covers durable conclusions, constraints, failure semantics,
reuse guidance, and open decisions. The feasibility repository remains the
home of:

- experimental application code and build configuration;
- one-off runners, local setup, raw result bundles, captures, and logs;
- detailed execution chronology and superseded experiment plans;
- machine-specific signing, device, and environment configuration.

## Completeness and limits

The seed is intended to preserve all knowledge currently useful for MVP
planning and implementation. It deliberately compresses failed attempts and
execution detail into current conclusions and reusable lessons. Maintainer
environments may expose the ignored `.research/blocker` path for exact PoC or
spike verification, but the wiki remains coherent without it.

Mutable platform availability, entitlement policy, library versions, browser
behavior, and distribution rules must be checked again against current primary
documentation before they become MVP requirements.
