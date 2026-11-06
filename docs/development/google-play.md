# Google Play Publishing

The `play` commands of `posato-provisioning` drive the Google Play release of
the Android app `app.posato.android` through the
[Google Play Developer API](https://developers.google.com/android-publisher)
(Android Publisher v3): they upload a bundle with its R8 mapping to a track,
promote a tested release to production, replace the store listing, and report
what Play holds. They sit beside the App Store `store` commands described in
[Apple development provisioning](apple-provisioning.md), share its envelope,
redaction, and exit codes, and never run in CI.

**The API acts only on an app that already exists in Play Console with one
build uploaded by hand, and it cannot publish an app that was never
published:** the first rollout to production is started in Play Console.
Creating the app, enrolling Play App Signing, the
first upload, Data safety, the content rating, and the permission declarations
are Play Console steps; Data safety is a one-off form with no command.

## One-time setup

The maintainer does this once per Play developer account. Nothing below is
tracked; the key lives outside the checkout.

1. **Google Cloud project.** In the Google Cloud console, create a project for
   Posato publishing. A new Google Cloud **organization** blocks service-account
   key creation by default (the `iam.disableServiceAccountKeyCreation`
   constraint); a project created **without an organization** avoids it, or an
   organization administrator lifts the constraint for this project only.
2. **Enable the API.** Under APIs and services, enable the
   **Google Play Android Developer API** for that project.
3. **Service account and key.** Under IAM and admin > Service accounts, create
   a service account (no project roles are needed), then under its Keys tab add
   a **JSON** key. The file downloads once. Store it with owner-only access:

   ```shell
   mkdir -p ~/Library/Developer/Posato
   mv ~/Downloads/<project>-<key id>.json ~/Library/Developer/Posato/posato-play-service-account.json
   chmod 600 ~/Library/Developer/Posato/posato-play-service-account.json
   ```

   The tool refuses a key file that other users can read or write, and a
   symbolic link.
4. **Invite the service account.** In Play Console > Users and permissions,
   invite the service account's e-mail address. Grant it, for the Posato app,
   the release permissions (**Release to production, exclude devices, and use
   Play App Signing**, and **Release apps to testing tracks**) and **Manage
   store presence**. A new permission can take a while to apply; until it does
   the commands fail with `PLAY_FORBIDDEN`.
5. **Configure the checkout.** Add the path to the ignored `local.properties`:

   | Key | Environment variable | Purpose |
   | --- | --- | --- |
   | `posato.play.serviceAccountKeyPath` | `POSATO_PLAY_SERVICE_ACCOUNT_KEY_PATH` | Absolute path to the untracked JSON key |

   As for the App Store key, the environment wins and a blank value counts as
   absent.

## Commands

Build and run the tool as in [Install and run](apple-provisioning.md#install-and-run).

| Command | Effect |
| --- | --- |
| `play status` | Read-only. Every track with its releases (version codes, status, rollout fraction, release-note languages) and the store listing locales. The edit it opens is deleted, never committed. |
| `play upload --aab <file> --mapping <mapping.txt> --track internal\|production [--notes-dir <dir>] [--status completed\|draft]` | Uploads the bundle, attaches the R8 mapping to the version code Play read from that bundle, makes it the track's only release with the release notes of `--notes-dir` (`<locale>.txt` files such as `en-US.txt` and `pl-PL.txt`, at most 500 characters each), and commits. Prints the version code, the bundle's SHA-256, and the track. `--status` defaults to `completed`. |
| `play promote --version-code <n> --from internal\|production --to internal\|production [--status completed\|draft]` | Makes the release holding `<n>` on `--from` the release of `--to`, keeping its name and release notes. Nothing is uploaded again, so production receives the tested bundle. Fails with `PLAY_RELEASE_MISSING` when `--from` has no such release. |
| `play listing --dir <dir>` | Replaces the listing in `<dir>/<locale>/play/` of each locale in one edit; the repository keeps it in `docs/store`. |

Examples:

```shell
posato-provisioning play status
posato-provisioning play upload --aab <out>/Posato-1.6.1.aab --mapping <out>/mapping.txt \
  --track internal --notes-dir <out>/release-notes
posato-provisioning play promote --version-code 1060101 --from internal --to production
posato-provisioning play listing --dir docs/store
```

Use `--status draft` while the app itself is still a draft in Play Console:
Play accepts only draft releases until the app's first review completes.
`completed` replaces whatever release the track held; staged rollouts are not
supported.

### Listing directory

One subdirectory per locale, named with Play's language code (`en-US`,
`pl-PL`, `de-DE`, `fr-FR`, `es-ES`, `pt-BR`):

```text
<dir>/
  en-US/
    title.txt              at most 30 characters
    short-description.txt  at most 80 characters
    full-description.txt   at most 4000 characters
    icon.png               512 x 512 PNG, at most 1024 KB
    feature-graphic.png    1024 x 500 PNG without alpha
    phone/*.png            2 to 8 PNGs without alpha, uploaded in file-name order
  pl-PL/
    ...
```

The three text files are required; text is trimmed and counted in characters,
not bytes. Each image is optional: an absent one leaves what Play holds, and a
present one replaces every image of its type for that locale. Phone
screenshots must measure 320 to 3840 pixels a side with the longer side at
most twice the shorter. Every file is checked before anything is sent, so a
refusal (`RELEASE_INPUT_INVALID`) names the file and the limit and changes
nothing. The listing's promotional video field is not managed; an update
replaces the whole listing resource and may clear it.

## Edits

Every change happens inside one Play **edit** that becomes visible only when it
is committed. A command opens an edit, does its work, and commits; when any
step fails, including the commit itself, it deletes the edit, so a half-done
upload or listing never stays open for a later commit to publish. If that
delete fails too, the step's own failure is reported and Play expires the
abandoned edit on its own. `play status` always deletes its edit. Play keeps
one open edit per account: a new edit, or a change made in Play Console,
invalidates an open one, so run one `play` command at a time and avoid editing
the listing in Play Console while a command runs.

## Authentication and request bounds

The tool signs an OAuth 2.0 service-account assertion (RS256, scope
`https://www.googleapis.com/auth/androidpublisher`, ten-minute lifetime) with
the JDK and exchanges it at `https://oauth2.googleapis.com/token`; the key
file's own `token_uri` is ignored. The access token is held in memory for the
run and never written.

Requests go only to `androidpublisher.googleapis.com` under
`applications/app.posato.android`, over paths built from constants and plain
identifiers, with redirects refused and a response capped at 4 MiB. Only `GET`
is retried. Uploads go to the `/upload/` endpoints: the bundle through a
resumable session and the mapping and images as simple media uploads, each
attempted once with a ten-minute deadline. The resumable session URL is the
one service-supplied URL the tool follows, and only when it is `https` on a
`googleapis.com` host on the default port without user information; anything
else stops with `PLAY_UPLOAD_REFUSED` before the bundle or the token is sent.

Never printed, logged, or written: the service account's e-mail address, the
key id, the key path, the access token, and the session URL. A failed request
reports its HTTP status, Google's `status` name, and its `reason` codes. Google's
free-text `message` often says exactly what to fix (a reused version code, a
draft-only app) but can quote submitted values, so it goes only to the
`--verbose` transcript on stderr, redacted.

## Play Console declarations

Play asks these in **Policy and programs > App content** and in the Data
safety form, which no API sets. The answers follow
[`PRIVACY.md`](../../PRIVACY.md); review them whenever it changes
(`agent-delegated`, `ANDROID-003`, pending the maintainer's acceptance in
the pull request).

- **Privacy policy:** `https://posato.app/privacy`.
- **Ads:** no ads. **App access:** every function works without an account
  or login, so no credentials are given to reviewers.
- **Target audience:** 13-15, 16-17, and 18 and over, matching the App
  Store's 4+ rating outside the Kids category; no group under 13, which
  would bring the Families policy.
- **Content rating (IARC):** the lowest rating, as the App Store's 4+: a
  utility or productivity app with no violence,
  sexual content, profanity, drugs, gambling, user-generated content,
  sharing of location, or purchases.
- **News, health, financial features, government:** none.
- **Data safety:**
  - Nothing is collected or shared except the name lookups the local VPN
    forwards during a website pause: declare **Web browsing > Web browsing
    history** as collected, processed ephemerally, required, for app
    functionality, not shared (the network's DNS server or Google Public DNS
    answers it as a service provider), not encrypted in transit (plain DNS),
    and with nothing to delete because nothing is kept.
  - Folder sync writes end-to-end encrypted files only into a folder the
    person picks; another app moves them, so Posato collects nothing there.
  - App choices, pause sets, and schedules stay on the device unless sync is
    on, and then only as those encrypted files.
- **Foreground service (`specialUse`):** "Keeps pauses and schedules the
  person set running while the app is closed: it starts and ends them at
  their minute and covers a paused app." Video: a schedule starting with
  Posato closed, then the pause screen over a paused app.
- **VpnService:** core function, a local VPN that carries only name lookups
  during a pause with websites, answers paused names itself, and forwards
  every other lookup unchanged; no other traffic enters it and nothing is
  logged. Video: start a pause, a paused website fails to load, another one
  loads, the pause ends.
- **Exact alarms:** `SCHEDULE_EXACT_ALARM`, granted by the person in
  Settings, needs no declaration; `USE_EXACT_ALARM` is not used.
- **Not requested:** all-files access and the battery optimization
  exemption, both removed in `ANDROID-003`.

## Troubleshooting

| Code | Meaning |
| --- | --- |
| `PLAY_KEY_UNREADABLE` | The key file is missing, a link, readable by others (`chmod 600`), or not a service-account JSON key. |
| `PLAY_TOKEN_FAILED` | Google refused the assertion: the key was deleted or disabled, or the API is not enabled for its project. |
| `PLAY_FORBIDDEN` | The service account is not invited for the app, lacks a permission, or the invitation has not applied yet. |
| `PLAY_NOT_FOUND` | The app does not exist in Play Console yet or has no hand-uploaded build. |
| `PLAY_REJECTED` | Play refused the content; rerun with `--verbose` for its message. |

## Gotchas

Observed while publishing the first Android release (2026-11-06):

- PEPK reads passwords only from a console, so a piped password fails with a
  `NullPointerException`; type it, or run it in a pseudo-terminal with
  `script -q /dev/null java -jar pepk.jar ...` and write the password to its
  standard input.
- Keep the PEPK output zip outside the repository and delete it after the
  upload.
- Open the internal-testing opt-in link in a browser signed in to the
  tester's Google account: Chrome on the phone uses the device account,
  another browser asks to sign in.
- After the first internal release and opt-in, the Play Store showed "Item
  not found" for about 20 minutes before Install appeared.
- Start the first production release of a never-published app in Play
  Console; later releases can use `play promote`.
- Play warns that the bundle contains native code without debug symbols. The
  native code comes from AndroidX libraries shipped without symbols, so there
  is nothing to upload.
- The first API call against a new app needs one build uploaded by hand.

## API reference

- [Edits workflow](https://developers.google.com/android-publisher/edits)
- [`edits`](https://developers.google.com/android-publisher/api-ref/rest/v3/edits):
  insert, commit, delete
- [`edits.bundles.upload`](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.bundles/upload)
- [`edits.deobfuscationfiles.upload`](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.deobfuscationfiles/upload)
- [`edits.tracks`](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks):
  list, update
- [`edits.listings`](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.listings):
  list, update
- [`edits.images`](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.images):
  deleteall, upload
- [Media upload](https://developers.google.com/android-publisher/upload)
- [Service-account authorization](https://developers.google.com/identity/protocols/oauth2/service-account)
- [Graphic asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
