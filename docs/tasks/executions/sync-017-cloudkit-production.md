# Execution: `SYNC-017`

- **Brief:** [`sync-017-cloudkit-production.md`](../specifications/sync-017-cloudkit-production.md)
- **Status:** `done`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code (agent)
- **Reviewer:** independent agent
- **Branch:** `feature/sync-017-cloudkit-production`
- **Updated:** 2026-09-16

## Plan

`observed`: only the companion and iOS `makeAnchor`/`makeBundle` write records:
`PosatoWorkspaceV1` (three identifiers) and `PosatoEncryptedBundleV1`
(`payload`), all `BYTES`, in zone `PosatoSyncV1`; reads never query, and
`quotaExceeded` is retryable. Release and development Mac apps share one local
database, and key items are named per workspace. In Production an extra type,
field, or index is permanent; a missing one only fails saves until a deploy.

1–4. **Audit (done).** Management token in the login Keychain, team id from
   `local.properties`, neither recorded. `cktool export-schema` for both
   environments under ignored `build/verification/`; the expected schema is
   Production's system types plus the two Posato types with four fields and no
   index. An index-free import without reset removed the extras (D2), the
   builds' sync paths were checked (D4), and a re-export showed an empty diff.
5–11. **Production run (done).** The maintainer deployed in the Console after
   the preview matched the exported schema, then release builds ran: Developer
   ID build 6 on the Mac and TestFlight 1.0.0 (1) on the iPhone. Evidence is
   Mac-side aggregates, the Console zone list and static `codesign`, because
   `posato-control` must not drive a notarized or TestFlight build; the
   maintainer pressed every device step, and snapshots around each step gave
   the `AC-04` deltas. Dropped by the 2026-09-16 maintainer decision: the
   second timed removal, the post-run zone check, and the closing removal.

## Decisions

`user-confirmed` (2026-09-15): **D1** audit with `cktool` export and diff.
**D2** extras are never deployed (brief boundary); index-only extras are
removed by import without reset, falling back to reset and import.
**D3** no tracked schema file; ADR 0007 stays the authority.
**D4** reuse the release builds when sync paths match `main`. **D5**
(2026-09-16) disclosure only: no product change and no follow-up row.
The brief's removal boundary carries a dated amendment: devices stay linked.

## High-risk plan review

- **Verdict:** approved after two changes-required passes (2026-09-15)
- **Required:** R1 `Users` defaults versus a no-index rule; R2 import without a
  development round; R3 deploying extras; R4 weak zone and key evidence; R5 no
  step column; N1 fresh link after removal has no records.
- **Resolution:** folded into the steps above, D2, and the recommended items
  (artifact check first, no database reset, zone stop rule, fixture cleanup).

## Result

- Steps 3–4 `observed`: Production had only the default `Users` type, while
  Development had the two types and four `BYTES` fields plus just-in-time
  `QUERYABLE SORTABLE` indexes; an import without reset made its export equal
  the index-free schema. `validate-schema` rejects Production.
- The development round established on the Mac (21 bundles from local policy),
  joined from the iPhone with no key wait, and removed the workspace; the peer
  reported action required and the Mac ended local-only. Its re-export still
  equalled the expected schema. It proves the Mac writers only: the iPhone
  authored nothing, because a joining device registers with its first authored
  change, and no session bundles were produced. The iOS writers use the same
  types and fields, established by code reading.
- `AC-01` met: the Console preview matched the expected schema beforehand, and
  after the deploy the Production export equals Development and that schema.
  That export predates the release writes, which cannot change it: Production
  has no just-in-time schema creation.
- `AC-02` met. `observed`: the nested companion is Developer ID signed with a
  hardened runtime, a secure timestamp and `icloud-container-environment`
  Production (`companion-codesign.txt`, also covered by `MACOS-008`), and one
  Mac press left `sync_bootstrap_state` 1. `user-confirmed`: Production then
  held exactly one `PosatoSyncV1` zone, and the TestFlight iPhone read the
  Mac-authored websites, so the synchronizable key works across signing
  environments, replacing the `source-claim`. The log query stayed empty as in
  `MACOS-008`, so the planned fallback carried the claim.
- `AC-03` met: websites converged both ways after linking, a session started on
  the iPhone ended early from the Mac, and both devices removed the workspace
  and linked fresh once. Device-side facts are `user-confirmed`.
- `AC-04` partly measured. A record carries about 350 B of payload (change 347,
  registration 316, session 349, largest 357 against the 65,536 cap); CloudKit
  metadata on top stays a `source-claim`. A deletion costs a record like an
  addition (no compaction), so a heavy year of about 6,600 records is about
  2.3 MB, roughly 0.05 percent of the 5 GB tier (`inferred`). The first removal
  cleared 9 records in under 73 s, but the press instant was missed, so the
  duration is not measured; `user-confirmed` (2026-09-16) accepts that bound
  rather than repeating runs covered by development automation and `SYNC-015`.

## Completed-change review

- **Verdict:** changes-required, corrections folded; re-check pending
- **Required:** stale header; brief and plan deviations unreconciled; token
  state unevidenced; readiness row stale; wiki overclaimed the round; no
  retained companion signature. **Resolution:** folded above, with the brief
  amendment, readiness paragraph, wiki wording and `companion-codesign.txt`.

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Release build sources vs `main`, sync paths | pass | `observed`: no `shared/`, adapter, or companion difference for Developer ID build 6 or TestFlight build 1 |
| Development export after the development round | pass | byte-identical to the index-free expected schema |
| Production export after the deploy (`AC-01`) | pass | equals Development and the expected schema; two types, four `BYTES` fields, no index |
| Companion signature and Production zone (`AC-02`) | pass | `companion-codesign.txt`; one zone after the Mac press; `sync_bootstrap_state` 1 |
| Convergence, session, removal, re-link (`AC-03`) | pass | Mac aggregates per step; maintainer confirmation on the iPhone |
| Sizes, storage and full-account behavior (`AC-04`) | partly | payload sizes measured; removal duration accepted as unmeasured |

## Blockers and accepted risks

- Open maintainer action: revoke the management token; not claimed done here.
- Accepted risk: removal duration at scale stays unmeasured by hand; the
  deletion path keeps its automated development coverage.
- Production record types and fields are now permanent; the devices stay linked.

## Final

- **Status:** `done`
- **Outcome:** met, with the `AC-04` removal duration accepted as unmeasured
