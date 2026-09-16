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
5. **Deploy (maintainer).** Console → Deploy Schema Changes with an empty
   diff, comparing the preview with the recorded diff and never adding the
   `recordName` index. Re-export Production and diff it (`AC-01`).
6. **Baseline.** Development builds removed once (step 4 or now); no
   database reset, since step 9 uses differences. Install the release builds.
   An existing `PosatoSyncV1` zone in Production stops the run for a
   maintainer decision; the app never deletes zones.
7. **Mac first (`AC-02`).** Static `codesign -dvv --entitlements :-` on the
   nested companion in `/Applications/Posato.app` (Developer ID, Production;
   team id not recorded). The maintainer presses **Sync with iCloud**. Proof:
   Mac `sync_bootstrap_state` = 1 and exactly one Production zone before the
   iPhone acts, plus the process-only log query; if it is empty, the verifier
   grants CloudKit only to the companion, so the zone still proves it.
8. **Join and converge (`AC-02`, `AC-03`).** The TestFlight iPhone joins. A
   Mac-authored synthetic domain in the iPhone's Paused items proves the
   shared synchronizable key across signing environments (replaces the
   `source-claim`). Converge the reverse direction, start a session on one
   device and end it early from the other, Remove workspace on both, link
   fresh once. The maintainer presses Mac steps; posato-control provides
   `db query`, the run directory, and iPhone snapshots by bundle id.
9. **Measure (`AC-04`).** Before and after each step, snapshot on the Mac
   `count(*)`, `sum(length(bundle_bytes))`, and `max(length(bundle_bytes))`
   of `sync_accepted_bundle`, plus `count(*)` of `sync_pending_bundle`, which
   must be 0. Length aggregates select no bytes. Record differences per step
   and the first Remove workspace duration at its record count. Per-record and
   heavy-use extrapolations are `inferred`; storage is a share of the quota
   the person shares with other iCloud data.
10. **Quota and retention (D5).** A full account keeps local saves committed
    and reports generic retryable; the maintainer picks disclosure or a row.
11. **Closeout.** Remove fixture domains and the workspace in Production on
    both devices; restore development builds; `cktool remove-token` and
    revoke the token; shorten this plan; update the sync topic question,
    readiness row, and wiki log; `./gradlew quality` only if code changed.

## Decisions

`user-confirmed` (2026-09-15): **D1** audit with `cktool` export and diff.
**D2** extras are never deployed (brief boundary); index-only extras are
removed by import without reset, falling back to reset and import. **D3** no tracked schema file; ADR 0007 stays the authority.
**D4** reuse the release builds when sync paths match `main`. **D5** open
until step 9.

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

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Release build sources vs `main`, sync paths | pass | `observed`: identical for the installed Developer ID build 6 (built from the `MACOS-009` branch) and TestFlight build 1; only unrelated iOS, desktop helper, and version-token files differ |
| Development export after the development round | pass | byte-identical to the index-free expected schema |
| Production export after the deploy (`AC-01`) | pass | equals Development and the expected schema; two types, four `BYTES` fields, no index |

## Blockers and accepted risks

- Maintainer: Console deployment, attended physical run (token done).

## Final

- **Status:** pending
- **Outcome:** pending
