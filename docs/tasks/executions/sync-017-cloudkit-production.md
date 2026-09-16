# Execution: `SYNC-017`

- **Brief:** [`sync-017-cloudkit-production.md`](../specifications/sync-017-cloudkit-production.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude Code (agent)
- **Reviewer:** independent agent
- **Branch:** `feature/sync-017-cloudkit-production`
- **Updated:** 2026-09-15

## Plan

`observed`: only the companion and iOS `makeAnchor`/`makeBundle` write records:
`PosatoWorkspaceV1` (`workspaceId`, `transportEpochId`, `keyEpochId`) and
`PosatoEncryptedBundleV1` (`payload`), all `BYTES`, in zone `PosatoSyncV1`.
Reads use record-ID fetches and zone changes; no query, subscription, or
`CKSyncEngine` exists. `CKError.quotaExceeded` is retryable in both adapters.
Release and development Mac apps share one local database. Key items are named
per workspace, so Development and Production keys cannot collide. In
Production an extra type, field, or index is permanent; a missing one only
fails saves until a later deploy adds it.

1–4. **Audit (done).** Management token in the login Keychain via
   `cktool save-token`, team id from `local.properties`, never recorded.
   `cktool export-schema` for both environments under ignored
   `build/verification/`; the expected schema is Production's default system
   types plus the two Posato types with four fields and no index. An import
   with `--validate` and no reset removed the index-only extras (D2); the
   release builds' sync paths were checked against `main` (D4); one
   development round and a re-export confirmed an empty diff.
5–11. **Production run (done).** The maintainer deployed in the Console after
   the preview matched the exported schema, then release builds ran on both
   devices: Developer ID build 6 on the Mac and TestFlight 1.0.0 (1) on the
   iPhone. Evidence is Mac-side aggregates, the Console zone list and static
   `codesign` on the nested companion, because `posato-control` must not drive
   a notarized or TestFlight build; the maintainer pressed every device step.
   Snapshots before and after each step gave the `AC-04` deltas. The synthetic
   fixtures were removed, and by maintainer decision the devices stay linked
   and the management token is revoked at closeout.

## Decisions

`user-confirmed` (2026-09-15): **D1** audit with `cktool` export and diff.
**D2** extras are never deployed (brief boundary); index-only extras are
removed by import without reset, falling back to reset and import. **D3** no tracked schema file; ADR 0007 stays the authority.
**D4** reuse the release builds when sync paths match `main`. **D5**
(2026-09-16) disclosure only: no product change and no follow-up row.

## High-risk plan review

- **Verdict:** approved after two changes-required passes (2026-09-15)
- **Required:** R1 system `Users` defaults cannot meet a no-index rule; R2
  import without a development round cannot prove the code's schema; R3
  deploying extras breaks the brief; R4 one-shot companion and weak zone or
  key evidence; R5 no step column; N1 fresh link after removal has no records.
- **Resolution:** steps 2, 4, 6–9, 11 and D2. Folded Recommended: artifact
  check before deploy, no database reset, single removal, Console index
  warning, token removal, team-id redaction, zone stop rule, fixture cleanup.

## Result

- Steps 3–4 `observed`: Production has only the default `Users` type.
  Development had the two types and four `BYTES` fields plus just-in-time
  `QUERYABLE SORTABLE` indexes; an import without reset made its export equal
  the index-free schema. `validate-schema` rejects Production.
- The development round established on the Mac (21 bundles from existing local
  policy), joined from the iPhone with no key wait, and removed the workspace;
  the peer then reported action required, and the Mac ended local-only. Its
  re-export still equalled the expected schema. Deviations: no session bundles
  and no iPhone-authored website, because both use the same single bundle
  writer and `payload` field, and the iPhone would have needed an attended
  Screen Time grant; the iOS anchor writer stays covered by code reading.

- `AC-01` met: the Console preview matched the expected schema beforehand, and
  after the deploy the Production export equals Development and that schema.
- `AC-02` met: the nested companion is Developer ID signed with a hardened
  runtime, a secure timestamp and `icloud-container-environment` Production.
  One Mac press produced exactly one `PosatoSyncV1` zone in the Production
  private database with `sync_bootstrap_state` 1, and the TestFlight iPhone
  then read the Mac-authored websites, so the synchronizable key works across
  signing environments (`observed`, replacing the `source-claim`). The
  process-only log query stayed empty as in `MACOS-008`, so the planned
  fallback carried the claim.
- `AC-03` met: websites converged both ways after linking, a session started on
  the iPhone ended early from the Mac, and both devices removed the workspace
  and linked fresh once.
- `AC-04` partly measured. A record costs about 350 B (website change 347,
  registration 316, session operation 349, largest 357 against the 65,536 cap),
  and a deletion costs a record like an addition because format 1 has no
  compaction, so a heavy year of about 6,600 records is about 2.3 MB, roughly
  0.05 percent of the 5 GB tier (`inferred`). The first removal cleared 9
  records in under 73 s, but the press instant was missed, so the duration is
  not measured; `user-confirmed` (2026-09-16) accepts that bound rather than
  repeating runs already covered by development automation and `SYNC-015`.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Release build sources vs `main`, sync paths | pass | `observed`: identical for the installed Developer ID build 6 (built from the `MACOS-009` branch) and TestFlight build 1; only unrelated iOS, desktop helper, and version-token files differ |
| Development export after the development round | pass | byte-identical to the index-free expected schema |
| Production export after the deploy (`AC-01`) | pass | equals Development and the expected schema; two types, four `BYTES` fields, no index |
| Companion signature and Production zone (`AC-02`) | pass | static `codesign`; one zone after the Mac press; `sync_bootstrap_state` 1 |
| Convergence, session, removal, re-link (`AC-03`) | pass | Mac aggregates per step; maintainer confirmation on the iPhone |
| Sizes, storage and full-account behaviour (`AC-04`) | partly | sizes measured; removal duration accepted as unmeasured |

## Blockers and accepted risks

- Accepted risk: removal duration at scale stays unmeasured by hand; the
  deletion path keeps its automated development coverage.
- Production record types and fields are now permanent; the devices stay linked.

## Final

- **Status:** `done`
- **Outcome:** met, with the `AC-04` removal duration accepted as unmeasured
