# `SYNC-012`: Converge bounded sessions across linked Apple devices

- **Review tier:** `high-risk`
- **Tier reason:** Remote session transitions will apply or clear real restrictions; stale work or lost terminal state can restrict the wrong session or revive an expired one.
- **Dependencies:** merged `SYNC-011`, `SESSION-002`, and follow-ups `SESSION-003` and `SYNC-015`; historical physical-check limits remain explicit in the execution plan.
- **Integration group:** `PR-SESSION-SYNC`, roadmap wave P4/W4.4.
- **Authority:** [MVP roadmap](../mvp-roadmap.md), [MVP scope](../../product/mvp-scope.md), [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md), [ADR 0007](../../decisions/0007-apple-workspace-bootstrap-and-native-sync-boundary.md), [SESSION-003](session-003-frozen-start-set.md), [DESIGN.md](../../../DESIGN.md), and [threat model A-02/A-03](../../security/apple-mvp-threat-model.md).
- **Planning status:** Draft for maintainer acceptance of D1–D3 below; preparing this brief does not authorize application changes.

## Outcome

After a successful exchange, a linked Mac and iPhone derive the same eligible session intent, apply it using their own authorized selections, and converge deliberate early end; each independently enforces the mandatory end without a delivery or wake-up promise.

## Boundaries

- Reuse format-1 `session-start`/`session-end`, the existing reducer, writer, mailbox, and account/workspace gates. Expiry remains a local terminal fact; no new wire kind, key contract, scheduler, or transport.
- Preserve responsive local start/end while offline, durable retry, exact session identity, and workspace isolation. Restart or uncertain commit must never author a second distinct start for one identifier.
- Reconcile through common session state and existing enforcement ports on launch, foreground, exchange completion, local commands, and local time evaluation; no dependence on the Session screen remaining subscribed.
- Commit end/expiry before clearing restrictions. Reconcile replacements by identity and reject stale completions; never extend the signed end or fall back to an older ended/expired candidate.
- Keep application selections and frozen summary data local. Preserve SESSION-003's distinction between the local frozen summary and current-policy reapply; a remote start carries no policy snapshot.
- Scope: shared session/sync integration, required SQLDelight migration and DI/host lifecycle wiring, focused platform adaptation if necessary, and affected design/verification documentation. Preserve SYNC-015 removal behavior and continuation invalidation.
- Non-goals: session history UI, policy redesign, guaranteed background delivery, silent authorization, new enforcement mechanisms, and MVP/release-readiness claims.

## Acceptance

- `AC-01` — Linked local start and deliberate early end commit recoverable intent without waiting for CloudKit; offline/restart/uncertain-commit retries preserve the exact start once and target the confirmed session when ending. A failed durable write is not reported as success.
- `AC-02` — Two replicas converge under reversed/duplicate delivery, concurrent starts, end-before-start, future starts, and conflicting starts according to ADR 0006; terminal candidates never revive older sessions, and pending local commands are not overwritten before authoring.
- `AC-03` — First observed expiry is durably terminal before inactivity/clear, survives restart, wall-clock rollback, and session replacement, and remains retained while referenced operations remain. Late expired delivery never applies restrictions.
- `AC-04` — Remote start, replacement, early end, and expiry reconcile real local enforcement by identity, including off-screen operation and relaunch; apply/clear failure and missing authorization stay truthful and retryable through the existing user flow. Removal/re-link cannot replay work from another workspace.
- `AC-05` — Signed Mac/iPhone driver runs prove start in both directions, end from the receiving peer, normal expiry, and offline/reconnect without resurrection, with actual restriction/cleanup evidence and device-local selections preserved; narrower unattended evidence is labelled separately.

## Verification

- Deterministic common/SQL integration tests for AC-01–AC-04, including commit fault injection, fake clocks, suspended apply/clear, workspace replacement, and migration preservation; both platform adapter suites where touched.
- Final `./gradlew quality`; affected native builds; [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) session/sync recipes on signed Mac and physical iPhone. Keep run directories under ignored `build/verification/` and obtain independent plan and completed-change reviews.

## Decisions for maintainer acceptance

- `D1` — **Proposed:** explicit linking publishes only a still-active local session, once, with its original identifier and end; no ended-session backfill. It then competes with remote starts by ADR 0006 order. Update consent copy to disclose session sharing; linking may replace the local candidate.
- `D2` — **Proposed:** successful workspace removal preserves the current bounded session locally, while discarding old-workspace pending sync intent and association. Local early end/expiry still works; a later explicit link follows D1. Failed removal preserves retry state.
- `D3` — **Proposed:** a remote start on an already authorized iPhone applies through the existing port; on Mac, use the existing explicit Resume restrictions flow when application requires an administrator prompt. A received timer is never presented as proof of enforcement, and no remote operation grants permission.
