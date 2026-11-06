# Execution: `ANDROID-003`

- **Brief:** [Posato for Android on Google Play](../specifications/android-003-google-play.md)
- **Status:** `active`
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Reviewer:** independent agent (plan and completed change)
- **Branch:** `android-003-google-play`
- **Updated:** 2026-11-06

## Plan

1. Roadmap revision 42, brief, record, draft pull request, mirror item In
   Progress.
2. Failing first: on the phone with all-files access revoked, show that the
   folder flow refuses or loses the folder (the defect Play forces).
3. Folder access on Android (`shared/src/androidMain`):
   - `TreeFolderAccess`: the token is the tree URI; `accept` takes the
     persistable read and write grant, `resolve` maps it to a virtual root
     path, and `display` shows the folder's name.
   - `TreeFolderFileSystem` serves paths under that root through
     `DocumentsContract`; a routing file system sends every other path (the
     app's own directory) to `NioFolderFileSystem`.
   - `writeExclusive` looks the name up and creates it with that exact
     name; a provider that renames a clash (`name (1)`) yields `EXISTS` and
     the copy is deleted. `replace` writes a temporary sibling, deletes the
     target, and renames; Android's shared storage already had no atomic
     exclusive create, so the folder protocol's tolerance of a short gap is
     unchanged (to be confirmed against `FolderMailbox` and
     `FolderPairing`).
   - `acceptsTypedPath` becomes false on Android. A stored path token (1.6.0
     install) resolves to nothing; the folder section then offers "Choose the
     folder again", which accepts only a tree holding the same workspace.
4. Grants (`androidApp`): remove `MANAGE_EXTERNAL_STORAGE`; a localized
   disclosure dialog before usage access and before the overlay; measure the
   battery exemption under `dumpsys deviceidle force-idle` (a schedule start
   and a folder exchange) and drop the permission unless they fail; record
   Play's exact-alarm and target API rules with links.
5. Build: `release` build type with R8 and keep rules found by running the
   release build; `versionCode = major * 10000 + minor * 100 + patch`
   (1.6.1 is 10601); the AAB signed by a new upload key
   (`~/Library/Developer/Posato/posato-play-upload.p12`, password in the
   login Keychain, paths in `local.properties`); the APK keeps the release
   key. PEPK exports the release key for Play App Signing; both keys are
   recorded in `~/machine-wiki`.
6. `posato-provisioning play`: service-account JWT from a JSON key outside
   Git; `upload` (AAB to a track with release notes per language),
   `listing` (text, icon, feature graphic, phone screenshots per language),
   `data-safety` (CSV), and `status`; documented beside the App Store
   commands.
7. `posato-control`: grants without all-files access, the folder chosen
   through the DocumentsUI picker; `verify-posato` skill, feature map, and
   scenarios updated.
8. Store content: listing text in the shipped languages from the App Store
   text, Android screenshots, the 512 px icon and the 1024 x 500 feature
   graphic, the Data safety answers (no data collected or shared; DNS
   lookups forwarded), the privacy policy URL.
9. Verify `AC-01` to `AC-03` on the Galaxy and the Posato AVD with a Tart
   peer; completed-change review; quality gate.
10. Maintainer checklist in Play Console, then upload to the internal
    track, install from Play on the Galaxy, `AC-04`.
11. Pull request ready and handed to `babysit-pr`; after it merges and the
    maintainer's go, promote to production; a closeout pull request updates
    the limits page, README, `PRIVACY.md`, and `posato.app` in every shipped
    language once 1.6.1 is live (`AC-05`).

## High-risk plan review

- **Verdict:** pending
- **Critical or Required findings:** pending
- **Resolution:** pending

## Result

- Pending.

## Completed-change review

- **Verdict:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| pending | pending | pending |

## Blockers and accepted risks

- Maintainer-only Play Console steps (brief).

## Final

- **Status:** `active`
- **Outcome:** pending
