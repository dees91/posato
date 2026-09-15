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

1. **Token (maintainer).** Management token saved with `cktool save-token`
   in the login Keychain; team id passed from `local.properties` in a shell
   variable. Neither enters Git, evidence, or chat.
2. **Export.** `cktool export-schema` for Production and Development under
   ignored `build/verification/`. The expected schema is the Production
   default system types copied as exported, plus the two Posato types with
   exactly the four fields and no index on any of their fields, system
   fields included.
3. **Diff Development.** Record the categorical diff (types, fields,
   indexes, grants). Artifacts: sync paths of the release builds are checked
   against `main` here, before any deploy (D4).
4. **Correct Development (D2), only if the diff is not empty.** On the
   development builds complete **Remove workspace** on both devices (this
   deliberately deletes the Development workspace and its key; Mac
   `sync_bootstrap_state` = 0, iPhone local-only). Notify the `MACOS-009` and
   `DESIGN-003` worktrees, run `cktool reset-schema`, import the expected
   schema with `--validate`, then run one development round (link, website
   change, session start and early end, Remove workspace) so any field the
   code writes but the import lacks reappears. Re-export; the diff must be
   empty.
5. **Deploy (maintainer).** Console → Deploy Schema Changes, only with an
   empty diff; the maintainer compares the Console preview with the recorded
   diff. Never add the `recordName` index the Records browser may request.
   Re-export Production and diff it against Development (`AC-01`).
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
    and reports generic retryable with nothing storage-specific. Propose
    disclosure only or a follow-up row; the maintainer decides.
11. **Closeout.** Remove fixture domains and the workspace in Production on
    both devices; restore development builds; `cktool remove-token` and
    revoke the token; shorten this plan; update the sync topic question,
    readiness row, and wiki log; `./gradlew quality` only if code changed.

## Decisions

`user-confirmed` (2026-09-15): **D1** audit with `cktool` export and diff.
**D2** extras are removed by reset and import, never deployed (brief
boundary). **D3** no tracked schema file; ADR 0007 stays the authority.
**D4** reuse the release builds when sync paths match `main`. **D5** open
until step 9.

## High-risk plan review

- **Verdict:** changes-required twice, corrections folded; re-check pending
- **Required:** R1 system `Users` defaults cannot meet a no-index rule; R2
  import without a development round cannot prove the code's schema; R3
  deploying extras breaks the brief; R4 one-shot companion and weak zone or
  key evidence; R5 no step column; N1 fresh link after removal has no records.
- **Resolution:** steps 2, 4, 6–9, 11 and D2. Folded Recommended: artifact
  check before deploy, no database reset, single removal, Console index
  warning, token removal, team-id redaction, zone stop rule, fixture cleanup.

## Result

- Pending. Step 3 artifact check passed (Verification).

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Release build sources vs `main`, sync paths | pass | `observed`: `shared/`, adapters, companion identical for build 4 and TestFlight build 1; only unrelated iOS files differ |

## Blockers and accepted risks

- Maintainer: token, Console deployment, attended physical run.

## Final

- **Status:** pending
- **Outcome:** pending
