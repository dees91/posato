# `MVP-001`: Pass the complete Apple MVP flow without manual state repair

- **Review tier:** `high-risk`
- **Tier reason:** Physical first/second-installation acceptance touches account-level workspace state and real device restrictions; fixture preparation and recovery need independent plan review before execution.
- **Dependencies:** completed `SYNC-012`, including its merged policy, onboarding, mapping, enforcement, and workspace-lifecycle dependencies.
- **Integration group:** `PR-MVP-ACCEPTANCE`, roadmap wave P5/W5.1.
- **Authority:** [MVP roadmap](../mvp-roadmap.md), [accepted MVP scope](../../product/mvp-scope.md), [DESIGN.md](../../../DESIGN.md), and [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md).

## Outcome

One supported physical Mac and iPhone complete the accepted product flow from local setup and explicit iCloud linking through shared policy, real local enforcement, early termination, and normal expiry, without manual state repair.

## Boundaries

- Exercise existing product paths and accepted platform differences: exact domains, shared semantic application policy, device-local application selections, and bounded session intent. macOS Resume and system authorization remain legitimate user steps.
- Normal permission dialogs, native pickers, and Sync now are allowed; database edits, injected product state, or Keychain/helper surgery cannot repair a failing acceptance run. Agree on disposable fixtures and any destructive setup before execution; preserve unrelated user data.
- Prior [SYNC-012 evidence](../executions/sync-012-session-convergence.md) supports session convergence but does not replace this integrated run: it used an already linked workspace and no selected applications. Earlier onboarding evidence retains its stated limits.
- No new features, transport, authorization bypass, delivery/wake guarantee, post-MVP improvements, or public-release verdict. Defects found here require scoped correction and applicable regression verification before the affected acceptance path is repeated; `RELEASE-001` owns release readiness.

## Acceptance

- `AC-01` — First and second installations complete local setup and explicitly choose Sync with iCloud under the same Apple Account, joining one workspace without Posato QR, invitation, or peer approval. Delayed key delivery shows truthful waiting without an empty/parallel workspace; immediate joining alone does not prove this case.
- `AC-02` — Exact domain policy and semantic application policy created on one device arrive unchanged on the other. Each device assigns its own application selection through the native route; opaque selections remain local and are not represented as portable matches.
- `AC-03` — Start a bounded session in each direction. After exchange and any required local Resume/authorization, both devices actually block a selected domain and their locally mapped application, while an unselected control remains usable. A received timer alone is insufficient evidence.
- `AC-04` — Deliberate early termination from either device converges and removes both website and application restrictions on both devices. Normal expiry also removes both kinds of restriction independently of terminal-operation delivery, including the accepted iPhone suspended-expiry path.
- `AC-05` — Relaunch during an active session preserves its identity/end and exposes truthful enforcement recovery; reopening after expiry and reconnecting a peer that missed the session never revive it. Final cleanup removes only owned fixtures and confirms no remaining session restrictions; the acceptance record identifies every pass, failure, or unobserved case on the tested revision.

## Verification

- Before execution, write the short execution plan with fixture ownership, safe setup/cleanup, and an AC-to-evidence mapping; obtain independent High-risk plan review. Use [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) and its onboarding, policy, application, session, and sync recipes through `tools/posato-control` on signed builds of the same revision.
- Capture driver runs and permitted read-only state evidence under ignored `build/verification/`. Record attended browser/application blocking and unblocking observations separately from UI/driver assertions; the maintainer handles system authorization and any native interaction the driver cannot perform.
- Verify the supported macOS browser contract (Safari and Chrome) and iPhone enforcement using owned synthetic website fixtures and agreed local test applications. Confirm actual selected duration, including the native scheduler minimum for suspended expiry.
- Run final `./gradlew quality` and applicable regression suites after any code correction; reuse unchanged unit/contract evidence only with its revision and limits. Complete independent change review and the execution record before claiming MVP acceptance.

## Decisions or blockers

- Execution requires an attended, unlocked Mac/iPhone pair on the same Apple Account, native permissions, and an agreed first/second-installation fixture. Preparing this brief does not authorize resetting the maintainer's current workspace or device data.
- [ONBOARDING-002](../executions/onboarding-002-second-install.md) observed immediate joining in both directions, not physical key waiting. If a safe run cannot observe delayed delivery, leave that case unverified and obtain a specific maintainer acceptance decision; do not manufacture a wait by tampering with private state or infer a full pass from unit tests.
