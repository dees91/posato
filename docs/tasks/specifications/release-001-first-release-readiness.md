# `RELEASE-001`: Establish an evidence-based first-release readiness verdict

- **Review tier:** `high-risk`
- **Tier reason:** Public repository exposure, signing, distribution, privacy disclosures, and release artifacts cross security and irreversible publication boundaries.
- **Dependencies:** `MVP-001` gates a ready verdict; this change prepares the brief in parallel. Preliminary audit may precede MVP completion under a separately scoped execution plan.
- **Integration group:** `PR-RELEASE-READINESS`, roadmap wave Release/R1.
- **Authority:** [MVP roadmap](../mvp-roadmap.md), [AGENTS.md](../../../AGENTS.md), [quality contract](../../development/engineering-quality-contract.md#release-readiness-boundary), [MVP scope](../../product/mvp-scope.md), [architecture baseline](../../decisions/0003-mvp-application-architecture-baseline.md), [threat model](../../security/apple-mvp-threat-model.md), and [diagnostics policy](../../security/diagnostics-and-support-data.md).

## Outcome

Every first-release obligation has verified evidence or an explicit blocker, yielding a ready or blocked verdict for a named source revision, artifact set, distribution channel, and supported platform matrix. Completing the audit with blockers does not authorize release.

## Boundaries

- Preliminary inspection, evidence inventory, and decision preparation may run alongside MVP-001 in an isolated worktree. Coordinate build, signing, packaging, and product corrections with its acceptance run; revalidate any affected evidence on the final candidate.
- Evaluate existing Apple MVP behavior and accepted limits. Post-MVP features, stronger bypass resistance, new recovery promises, and broader platform coverage are outside scope; development signing and a passing device pair do not establish distribution eligibility or the release support matrix.
- No public release, repository visibility change, history rewrite, credential revocation, Apple submission, or production CloudKit change follows from this brief. Prepare concrete results before any separately authorized external action; keep secrets, personal identifiers, and raw audit/device evidence out of tracked files.
- GitHub CI is currently disabled by explicit decision. Assess the release verification mechanism and record any needed decision; do not silently enable CI or treat absent hosted checks as passing evidence.

## Acceptance

- `AC-01` — Audit the repository content and Git history intended for publication for credentials, private data, machine-specific material, and improper PoC reuse. Inventory source, dependency, asset, and third-party licenses/notices; identify repository-license and naming-clearance decisions. Unresolved exposure or permission gaps block the affected publication.
- `AC-02` — A clean checkout builds and passes applicable documented local gates without the optional research checkout or undeclared maintainer state. Document credential-free checks separately from signed-build prerequisites; verify the actual CI/local merge process and reproducible release inputs against the chosen revision.
- `AC-03` — User-facing privacy disclosures, security reporting, contribution/support routes, and release limitations agree with implemented data handling and accepted authorities. Cover collection, synchronization, retention, deletion, diagnostics/export, and recovery limits without claiming browsing-history collection, remote wipe, total-key-loss recovery, or guarantees absent from the product.
- `AC-04` — For each chosen distribution channel, verify required Apple permissions and production configuration for the app, helper/daemon, and extension, including Family Controls, CloudKit/Keychain environments, signing/provisioning, and applicable notarization or store requirements. Identify versioned artifacts and verify installation, launch, enforcement, cleanup/removal, and applicable update behavior across the declared supported platforms; unresolved channel or coverage requirements are blockers.
- `AC-05` — Reconcile completed MVP-001 evidence with the final release candidate, rerun checks affected by subsequent changes, and produce a reviewed pass/blocked obligation table with evidence, residual limits, and an owner/next action for each blocker. A ready verdict requires all required obligations satisfied and no unresolved Critical/Required findings; actual publication remains a separate maintainer action.

## Verification

- Before consequential work, create the short execution plan and obtain independent High-risk plan review. Keep the audit tied to actual files, reachable history, resolved dependencies, artifact contents, and commands; record sanitized conclusions rather than copying raw findings into versioned documents.
- Run clean-checkout builds and final `./gradlew quality` where applicable; inspect the resulting bundles and their signatures/entitlements. Recheck current platform/distribution requirements against official Apple sources during execution and date the findings; do not infer approval from a development profile.
- Drive candidate applications through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) for installation, enforcement, and lifecycle evidence. Retain captures under ignored `build/verification/`; record OS/browser versions, artifact identity, and attended permission steps. Reuse MVP-001 evidence only where its revision, configuration, and coverage remain applicable.
- Complete independent change/readiness review and a concise execution record containing the actual obligation table, decisions, checks, and verdict. Apply scoped corrections and rerun affected verification before closeout.

## Decisions or blockers

- MVP-001 is still pending; no ready verdict can precede it. Choose the macOS/iOS distribution channels, repository license, public reporting/support routes, and release verification policy through explicit maintainer decisions where not already accepted.
- Apple account permissions, distribution entitlements, signing material, production configuration, supported test targets, and human system approvals must be confirmed during execution. Missing access or untested coverage stays blocked; neither Simulator success nor the existing development-device pass substitutes for it.
