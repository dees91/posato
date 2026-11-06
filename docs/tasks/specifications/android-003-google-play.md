# `ANDROID-003`: Posato for Android on Google Play

- **Review tier:** high-risk
- **Tier reason:** the sync folder's storage access changes for existing
  installs, a store release and its signing keys are account-level resources
  that cannot be taken back (Play App Signing enrollment, the package name
  bound on the first upload), and the store declarations describe privacy.
- **Dependencies:** none; the Play organization developer account is
  verified (2026-11-05).
- **Integration group:** `PR-ANDROID-PLAY`
- **Authority:** [release roadmap](../release-roadmap.md) revision 42
  (outside the release cycle, `user-confirmed` 2026-11-06),
  [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md),
  [`PRIVACY.md`](../../../PRIVACY.md).
- **Record:** [execution record](../executions/android-003-google-play.md).

## Outcome

A person installs Posato 1.6.1 for Android from Google Play, picks the sync
folder in Android's folder picker without granting access to all files, and
sees why Posato needs usage access and the overlay before granting them.

## Boundaries

- Storage: the folder is a persistable Storage Access Framework tree URI,
  read and written through `DocumentsContract`; the app's own files stay on
  `java.nio`. `MANAGE_EXTERNAL_STORAGE` leaves the manifest. An install that
  stored a path asks for the folder again instead of failing silently.
- Grants: a disclosure screen before usage access and the overlay; the
  battery exemption is dropped unless a phone run shows that schedules or
  sync fail without it; exact alarms and `targetSdk` are checked against
  Play's current policy.
- Build: a `release` build type with R8 producing an AAB and the existing
  APK, `versionCode` derived from `versionName`, signed by a new upload key
  kept outside Git; the existing release key becomes the Play app signing
  key through PEPK.
- Tooling: `posato-provisioning` gains Play commands through the Play
  Developer API (upload to a track, listing text and images, Data safety);
  `posato-control` grants and the folder flow follow the picker.
- Store: listing in every shipped language, Data safety, content rating,
  and the VpnService and `specialUse` declarations; internal track first,
  production only after the maintainer's go.
- Docs: the limits page, README, `PRIVACY.md`, and `posato.app` in every
  shipped language name Google Play.
- Non-goals: Mac, iOS, Linux, Windows, and the GitHub Releases APK (they
  stay on 1.6.0); Play billing, ads, or any analytics; Android below the
  current `minSdk`.

## Acceptance

- `AC-01` - On the test phone and the Posato emulator, a folder picked in
  the system picker carries a pause to and from a Tart peer in both
  directions; after Posato restarts the folder still works; an install
  upgraded from 1.6.0 with a stored path asks for the folder and then
  syncs.
- `AC-02` - The release manifest has no all-files access, every remaining
  sensitive permission has a written Play justification, and the
  disclosure appears before each of the two Settings grants.
- `AC-03` - The R8 release build, installed from Play's internal track on
  the test phone, completes onboarding, a pause blocking a website and an
  application, and a schedule with the app closed.
- `AC-04` - Play App Signing holds the existing key (the Play-delivered
  APK's certificate digest equals the GitHub 1.6.0 APK's), and the listing,
  Data safety, content rating, and declarations are complete.
- `AC-05` - After the maintainer's go, 1.6.1 is live in production and the
  public docs name Google Play in every shipped language.

## Verification

- `posato-control -t android` on the wired Galaxy and the Posato AVD, with a
  Tart Linux or Mac peer in the same folder workspace.
- The R8 build under the same flows, and `apksigner` digests of the Play
  and GitHub APKs.

## Decisions or blockers

- Maintainer-only Play Console steps: create the app entry, link the API
  service account, enroll app signing, answer the content rating and the
  declarations (with the VpnService video), and the production go.
