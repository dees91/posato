# Execution: `ANDROID-003`

- **Brief:** [Posato for Android on Google Play](../specifications/android-003-google-play.md)
- **Status:** `active` (the production release follows the merge)
- **Review tier:** `high-risk`
- **Implementer:** Claude, with parallel agents in isolated worktrees
- **Reviewer:** independent agents (plan, completed change)
- **Branch:** `android-003-google-play`
- **Updated:** 2026-11-06

## Plan

1. Revision 42, brief, record, draft pull request #228, mirror In Progress.
2. Failing first: with all-files access denied, the folder is refused.
3. Folder through the Storage Access Framework (`TreeFolderAccess`,
   `TreeFolderFileSystem`, `RoutingFolderFileSystem`): the tree URI is the
   token, a primary tree resolves to the 1.6.0 path so the binding stays,
   an exclusive write renames a complete `.tmp-` file and reads a renamed
   clash as existing, `replace` is refused on the shared folder; a linked
   1.6.0 install chooses the same folder again.
4. Grants: no all-files access, a disclosure before usage access and the
   overlay, the battery exemption measured and dropped, `PRIVACY.md` in
   every shipped language.
5. R8 release APK and AAB, `versionCode = major * 1000000 + minor * 10000 +
   patch * 100 + build`, an upload key outside Git, the release key enrolled
   as the Play app signing key through PEPK before the first upload.
6. `posato-provisioning play status|upload|promote|listing`.
7. `posato-control` grants and the DocumentsUI folder picker.
8. Listing text, icon, feature graphic, and phone screenshots per language;
   the Play Console declarations with their reasons.
9. Verification on the Galaxy and the Posato AVD with a Tart Linux peer.
10. Maintainer steps in Play Console, the first upload by hand, the internal
    track installed from Play on the Galaxy.
11. After the merge: a bundle of the merged commit through `play upload`,
    the smoke test from Play, and the first production rollout in Play
    Console (the API cannot publish an unpublished app); a closeout pull
    request names Google Play on the limits page, README, and `posato.app`.

## High-risk plan review

- **Verdict:** approved on the second pass.
- **Required findings:** R1 the re-picked folder's binding must equal the
  1.6.0 path; R2 no partial files; R3 enrollment gates the first upload;
  R4 the first upload is manual; R5 a build digit; R6 production ships the
  merged commit. All folded into the plan before implementation.

## Result

- Android 1.6.1 opens the sync folder through the system picker and asks
  for neither all-files access nor the battery exemption; usage access and
  the overlay are explained first. Play App Signing holds the existing
  release key (`9D:9C:67:F8...D5:B1`), the upload key is separate, and
  build 1 (`1060101`, commit `75ebac1d`) is on the internal track.
- Found and fixed: a paused app was never covered on the Galaxy, also on
  1.6.0, because Samsung publishes usage events about six seconds late
  (maintainer decision to fix it in this row); after the 1.6.0 upgrade the
  folder section also claimed the workspace was missing; the disclosure
  dialog was dark in a light app.
- Deviations: the routing file system sends only Posato's private
  directory to `java.nio` and fails closed for every other path; R8 is
  pinned to 9.5.29 because AGP 9.0.1's R8 could not read Kotlin 2.4
  metadata; the Play listing lives in `docs/store/<locale>/play/` because
  translated copy is allowed only there; the maintainer uploaded build 1
  before the API was linked.
- Decisions (`agent-delegated`): drop the battery exemption on the Doze
  evidence; declare forwarded name lookups as ephemeral web browsing data
  in Data safety; the Play title "Posato: pause apps and sites".
- Production: build 3 (`1060103`) of the merged main commit `5dae191f` went
  to the internal track through `play upload`, passed the smoke test
  installed from Play on the Galaxy (`a003-prod-candidate`), and was sent to
  production review on 2026-11-06. The closeout pull request names Google
  Play on `posato.app`, the README, and the limits page, and merges once
  1.6.1 is live.

## Completed-change review

- **Verdict:** approved for code; one Required process item (P1: cite the
  verification evidence) resolved by this record and `pr-evidence`.
- **Folded:** a folder Posato cannot create files in is refused; an
  unlinked 1.6.0 choice is offered again; the usage disclosure says what is
  kept; the dialog closes when its wait is cancelled.
- **Accepted risk:** the provider's rename is not atomic against another
  writer in the same instant.
- **Declined:** registering the access token with the redaction (Google
  never echoes it); the SD-card tree path (no 1.6.0 SD-card installs).

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| Red: all-files denied, `flow folder link` on main | fail as expected | `a003-red-emu` |
| Disclosures before usage access and overlay, R8 build, Galaxy | pass | `a003-r8-onboard` |
| Schedule start and end in forced deep Doze, no exemption | pass | `a003-doze` |
| Fresh link and two-way sync with a Tart Linux peer | pass | `ac01-r1-*` |
| 1.6.0 to 1.6.1 update with the release key, choose again, sync | pass | `ac01-r2-*` |
| Unlinked 1.6.0 choice after the update, picker link on the Galaxy | pass | `a003-unlinked-lost`, `a003-phone-link` |
| Paused app covered on the Galaxy and the emulator | pass | `a003-samsung-fix`, `a003-emu-fix` |
| Installed from Play's internal track: signature, website pause, schedule with Posato closed | pass | `a003-play-ac03` |
| `qualityLint`, `:shared:jvmTest`, `:posato-control:test`, `:posato-provisioning:test` | pass | local |

## Blockers and accepted risks

- Maintainer: Data safety, content rating, the VpnService and foreground
  service declarations with their videos, and the production go.

## Final

- **Status:** `active`
- **Outcome:** pending the production release and its closeout.
