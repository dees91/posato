# Execution: `SYNC-003`

- **Brief:** [Apple bootstrap contract](../specifications/sync-003-apple-bootstrap-contract.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** Codex agent `/root/sync003_plan_review`
- **Branch:** `feature/sync-003-apple-bootstrap-contract`
- **Updated:** `2026-08-28`

## Plan

1. Obtain an independent review of the brief and this plan.
2. Record one immutable, create-only CloudKit anchor whose conditional creation
   selects the sole workspace during concurrent first runs; an indeterminate
   result is reconciled by exact read rather than a new candidate.
3. Freeze one versioned synchronizable-Keychain item with exact create, read,
   and delete selectors. A visible anchor with a delayed key waits without
   replacement, while a losing candidate deletes only its own exact item.
4. Keep CloudKit and Keychain behind a dedicated, short-lived, unprivileged
   macOS sync companion over bounded authenticated IPC. It owns no enforcement,
   product policy, root privilege, listener, background agent, or general
   command surface.
5. Freeze only the minimum opaque mailbox record and semantic outcomes needed
   by downstream adapters, with no target, dependency, deployed schema, retry
   timing, or PoC runtime in this task.
6. Guide the maintainer through registering the one new App ID and verify its
   association with the existing CloudKit container.
7. Reconcile affected architecture, security, roadmap, resource, and wiki
   authorities, then run focused documentation checks.
8. Obtain an independent completed-change review, resolve blocking findings,
   rerun affected checks, and record the final result.

## High-risk plan review

- **Verdict:** `approved`
- **Critical or Required findings:** The first review required the plan to name
  the bootstrap, exact-Keychain, delayed-key, and companion-boundary invariants
  and to remove a premature `blocked` final status.
- **Resolution:** The plan now states those invariants, keeps the task active,
  and passed focused re-review with no remaining blocking finding.

## Result

- ADR 0007 now freezes the one-workspace bootstrap, mailbox, Keychain, and
  dedicated macOS synchronization-companion boundaries, including a durable
  local-only account binding that gates bootstrap and ongoing mailbox access
  and exact-zone establishment before anchor absence.
- `user-confirmed` (2026-08-28): `app.posato.macos.sync` exists with
  iCloud/CloudKit enabled and is associated with the existing
  `iCloud.app.posato.sync` container. No private account or signing value was
  recorded.
- Affected architecture, security, roadmap, resource, and wiki authorities now
  route to the accepted decision without rewriting the historical `APPLE-001`
  result.

## Completed-change review

- **Verdict:** `approved`
- **Critical or Required findings:** Candidate IDs were persisted before the
  only workspace-key copy reached Keychain, leaving a crash window that could
  not resume; the item named CRC-32 without exact interoperable parameters.
- **Resolution:** The item is now created and read-confirmed before candidate-ID
  persistence, anchor creation waits for that persistence, and the checksum is
  fixed as CRC-32/ISO-HDLC with complete parameters and byte order. Focused
  re-review found no remaining Critical or Required issue.

## Hosted review correction

- **Finding:** The hosted PR review identified a `Required` race: a persisted
  candidate had no durable binding to its originating Apple account, so retry
  after an account switch could treat another private database as empty and
  create a parallel anchor.
- **Resolution:** ADR 0007 now defines one native-derived 32-byte opaque account
  binding, persists it with candidate and established state, and requires the
  native edge to match it before and after every bootstrap provider access.
  Mismatch or an in-flight account change fails closed without altering the
  CloudKit or Keychain formats.

## Focused correction review

- **Verdict:** `approved`
- **Critical or Required findings:** The first pass found that a preflight-only
  binding check could accept a provider result before a delayed account-change
  event arrived.
- **Resolution:** The native edge now also requires an exact postflight match
  before returning any definitive provider result. Unavailable, mismatched, or
  changed in-flight state becomes `unknown-outcome`; focused re-review found no
  remaining Critical, Required, or advisory issue.

## Ongoing-mailbox review correction

- **Finding:** A second hosted `Required` finding identified that the durable
  binding guarded bootstrap only, leaving later CloudKit bundle reads and
  writes dependent on a potentially delayed account-change event.
- **Resolution:** The established binding now guards every mailbox operation
  before and after provider access. Failed checks expose no fetched bundle,
  advance no cursor or accepted engine state, acknowledge no publication, and
  preserve pending work without changing provider formats. Each sync-engine
  instance is binding-scoped; failure invalidates it, and recovery creates a
  fresh instance from the last accepted serialization and pending work.

## Ongoing-mailbox focused review

- **Verdict:** `approved`
- **Critical or Required findings:** The first pass required invalidating the
  live sync-engine instance after an account failure and found three stale
  authority routes that ended implementation ownership at `SYNC-009`.
- **Resolution:** Automatic events now share the account gate; failure cancels
  and discards the binding-scoped engine before recreation from accepted state.
  All implementation and proof routes now extend through `SYNC-010`; focused
  re-review found no remaining Critical, Required, or advisory issue.

## Zone-establishment review correction

- **Finding:** A third hosted `Required` finding identified that bootstrap read
  the fixed anchor without first creating or confirming its custom zone, so a
  literal implementation could not complete first opt-in in a fresh private
  database.
- **Resolution:** ADR 0007 now separates zone absence from anchor absence and
  requires a binding-checked fetch, save-if-absent, and exact read confirmation
  of the fixed `PosatoSyncV1` zone before bootstrap proceeds. Crash, concurrent
  creation, timeout, and unknown outcomes reuse that fixed identity and the
  existing provider outcomes without another local token or manager. Zone loss
  after establishment remains action-required and preserves local and pending
  work.

## Accepted advisory correction

- **Finding:** The maintainer accepted the hosted advisory that ADR 0002 still
  ended implementation and physical evidence ownership at `SYNC-009` after the
  ongoing mailbox correction assigned account isolation to `SYNC-010`.
- **Resolution:** Current decision, task, security, and routing authorities now
  distinguish bootstrap evidence through `SYNC-009` from ongoing mailbox and
  account-isolation evidence through `SYNC-010`. The historical wiki-log entry
  remains unchanged.

## Zone-establishment focused review

- **Verdict:** `approved`
- **Critical or Required findings:** None. The reviewer found the fixed-zone
  fetch, save, and read-confirm sequence implementable, account-bound, and
  minimal, with established zone loss preserving local and pending work.
- **Recommended finding:** Record this verdict and rerun the affected
  documentation checks after the final record-only edit. Both actions were
  completed; no Optional finding remained.

## Physical-gate advisory correction

- **Finding:** The maintainer accepted a hosted advisory that the roadmap's
  physical CloudKit and Keychain gate still ended at `SYNC-009`, although
  ongoing mailbox account-postflight evidence belongs to `SYNC-010`.
- **Resolution:** The gate now extends through `SYNC-010` without changing its
  completion rule, the accepted provider contract, or unrelated bootstrap,
  Keychain, key-loss, and local-access ownership ranges.

## Physical-gate focused review

- **Verdict:** `approved`
- **Critical or Required findings:** None. The reviewer confirmed that
  `SYNC-010` owns the ongoing mailbox account-postflight evidence while the
  unchanged local-access and key-loss ranges retain their narrower ownership.
- **Recommended or Optional findings:** None.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Dependency and authority inspection | pass | `SYNC-001` and `APPLE-001` are complete; roadmap and accepted ADR dependencies are present. |
| Apple resource inspection | pass | Maintainer confirmed the explicit App ID, iCloud/CloudKit capability, and association with the existing container without recording private values. |
| Contract and authority consistency | pass | ADR, architecture, threat, roadmap, resource, and wiki surfaces use the same companion, zone, record, Keychain, and downstream-owner contracts. |
| Markdown links and whitespace | pass | All changed relative links resolve; tracked and new-file diff checks pass after the final review correction. |
| Scoped sensitive-data scan | pass | No personal path, private key marker, or common credential pattern appears in the changed security-sensitive records after the final review correction. |
| Account-isolation correction | pass | The contract checks the expected binding before and after provider access, preserves indeterminate attempts across delayed account-change notification, resumes only under the original binding, and leaves the anchor and Keychain formats unchanged. |
| Correction documentation hygiene | pass | All repository-local Markdown links resolve, `git diff --check` passes, the scoped sensitive-data scan is clean, and the correction log entry is parseable at EOF. |
| Ongoing-mailbox isolation | pass | The established binding gates fetch/send batches, automatic engine events, and their results; mismatch invalidates the engine while preserving pending work and accepted transport progress for a fresh instance under the original binding. |
| Exact-zone establishment | pass | Bootstrap confirms the fixed binding-scoped zone before anchor absence; crash, concurrent creation, and unknown save outcomes reuse that identity, while established zone loss preserves local and pending work. |

## Blockers and accepted risks

- No blocker. Target creation, entitlements, provisioning, signing, production
  CloudKit schema deployment, and physical behavior remain with `SYNC-004`
  through `SYNC-010` and do not gain an implementation-readiness claim here.

## Final

- **Status:** `done`
- **Outcome:** met
