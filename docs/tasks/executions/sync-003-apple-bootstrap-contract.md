# Execution: `SYNC-003`

- **Brief:** [Apple bootstrap contract](../specifications/sync-003-apple-bootstrap-contract.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Codex
- **Reviewer:** Codex agent `/root/sync003_plan_review`
- **Branch:** `main`
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
  local-only account binding that gates bootstrap provider access.
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

## Blockers and accepted risks

- No blocker. Target creation, entitlements, provisioning, signing, production
  CloudKit schema deployment, and physical behavior remain with `SYNC-004`
  through `SYNC-009` and do not gain an implementation-readiness claim here.

## Final

- **Status:** `done`
- **Outcome:** met
