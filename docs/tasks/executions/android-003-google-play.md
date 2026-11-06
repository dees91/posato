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
     persistable read and write grant and releases the previous one.
     `resolve` maps a primary-volume tree (`primary:<rel>`) to the same
     `/storage/emulated/0/<rel>` path that 1.6.0 stored, so the workspace
     binding (`FolderLocation.bindingFor`) is unchanged; another provider's
     tree gets a namespaced virtual path. `canonical` returns that path
     while the grant is held, otherwise null.
   - `TreeFolderFileSystem` serves paths under a held tree through
     `DocumentsContract`, caching document ids per parent from `names()`
     and dropping them on a miss; a routing file system sends every other
     path (the app's own directory) to `NioFolderFileSystem`.
   - `writeExclusive` writes a complete `.tmp-` sibling
     (`application/octet-stream`) and renames it to the final name; a name
     the provider returns other than the requested one is `EXISTS`, and the
     copy is deleted. `replace` is never used on the shared folder (only on
     app-private files), so the tree file system refuses it.
   - Migration: `acceptsTypedPath` becomes false on Android. A stored path
     token from 1.6.0 resolves to nothing; only then, and only on a host
     without iCloud, the folder section offers "Choose the folder again",
     which accepts only a tree mapping to exactly the stored path. The
     coordinator's anchor check still rejects a different workspace.
   - Failure modes E2E reaches poorly, written before coding: a provider
     renaming a clash to `name (1)`, a lost grant, a partial read. The clash
     is exercised by pre-creating a file with `adb` before a join; what stays
     out of reach is recorded as an accepted risk, without instrumentation.
4. Grants (`androidApp`): remove `MANAGE_EXTERNAL_STORAGE` from the manifest,
   `Permissions.all()`, onboarding, and the driver's grant list; a localized
   disclosure dialog before usage access and before the overlay; measure the
   battery exemption under `dumpsys deviceidle force-idle` (a schedule start
   and a folder exchange) and drop the permission unless they fail; record
   Play's exact-alarm and target API rules with links. `PRIVACY.md` (every
   shipped language) describes the picked folder in this pull request,
   before Play review.
5. Build: `release` build type with R8 (keep rules reasoned from
   kotlinx-serialization, the `posato://join` link, and resources; a run
   only confirms them); `versionCode = major * 1000000 + minor * 10000 +
   patch * 100 + build` (1.6.1 build 1 is 1060101), leaving room for
   re-uploads; the AAB signed with `jarsigner` by a new upload key
   (`~/Library/Developer/Posato/posato-play-upload.p12`, password from the
   login Keychain on stdin, never on a command line; Gradle never sees a
   key). The APK keeps the release key. PEPK exports the release key for
   Play App Signing; the PEPK zip lives outside the worktree and is deleted
   after the upload. `releasing.md` and `build-variants.md` describe the
   AAB, R8, and the build digit; the keys are recorded in `~/machine-wiki`.
6. `posato-provisioning play`: service-account JWT from a JSON key outside
   Git (mode 600, path in `local.properties`); `upload` (AAB and R8
   `mapping.txt` to a track, release notes per language), `listing` (text,
   icon, feature graphic, phone screenshots per language), and `status`;
   documented beside the App Store commands. Data safety stays a one-off
   Play Console form.
7. `posato-control`: grants without all-files access, the folder chosen
   through the DocumentsUI picker; `verify-posato` skill, feature map, and
   scenarios updated.
8. Store content: listing text in the shipped languages from the App Store
   text, Android screenshots, the 512 px icon and the 1024 x 500 feature
   graphic, the privacy policy URL; Data safety answers with their reasoning
   (no data collected or shared; lookups forwarded to the network's DNS or
   Google Public DNS); the permitted VpnService use and the `specialUse`
   description, each with its video.
9. Verify on the Galaxy and the Posato AVD with a Tart peer: `AC-01`
   including the upgrade from the GitHub `Posato-1.6.0.apk` to a 1.6.1 APK
   signed locally with the release key; `AC-02` with one run that taps
   through the onboarding grant buttons; `AC-03` on the R8 build adding an
   export and import and a join over the folder; one poll's cost measured.
   Then the completed-change review and the quality gate.
10. Maintainer checklist in Play Console, in this order: create the app;
    enroll the existing key through PEPK and register the upload
    certificate; **stop gate**: App integrity shows an app signing SHA-256
    equal to the release key's fingerprint; add the test phone's account as
    an internal tester; upload the first AAB to the internal track by hand
    (the API acts only on an app with a build); link the API service
    account; Data safety, content rating, declarations with videos.
11. Install from the internal track on the Galaxy, `AC-03` and `AC-04`; the
    pull request ready and handed to `babysit-pr`.
12. After the merge, build the production AAB from the merged main commit in
    a clean clone (next build digit), record its commit, upload it to the
    internal track through `play upload`, rerun the `AC-03` smoke from Play,
    and promote it to production after the maintainer's go. A closeout pull
    request updates the limits page, README, and `posato.app` in every
    shipped language once 1.6.1 is live (`AC-05`).

## High-risk plan review

- **Verdict:** changes-required (first pass), second pass pending
- **Critical or Required findings:** R1 the binding of a re-picked folder
  must equal the 1.6.0 path; R2 exclusive writes must not expose partial
  files; R3 app signing enrollment gates the first upload; R4 the first
  upload is manual; R5 the versionCode needs a build digit; R6 production
  must ship a build of the merged commit.
- **Resolution:** folded into plan steps 3, 5, 10, and 12.

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
