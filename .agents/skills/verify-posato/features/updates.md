# Updates on Mac

A Developer ID release of Posato for Mac asks once whether to check GitHub
Releases for updates automatically. About Posato and the application menu can
check on demand. A found update downloads and replaces the application after
Install and Relaunch, keeps the user's data, and never installs during an
active pause.

## Sub-features

- `update-consent` asks "Check for updates automatically?" once setup
  completes and records the answer.
- `update-manual` finds a newer release from About Posato and installs it.
- `update-kept-data` keeps websites, the workspace, and This Mac setup across
  the update.

## How to get to it (user POV)

- Finish first-run setup in a release build; the consent alert appears on its
  own. It also appears on the first open of a replaced installation.
- About Posato, Updates section: the **Check for updates automatically** row
  and the **Check for Updates…** button.
- Application menu: **Check for Updates…** below **About Posato**.

## Driving it with posato-control

Preconditions:

- Only notarized releases and candidates update; a development package has no
  update feed. Prepare the previous release in a fresh clone, never on the
  host Mac.
- The newer release must be published to the feed the older build reads, or
  the check reports no update.

- **Prepare the previous release:** download its image with
  `gh release download v<previous> --pattern '*.dmg' --dir <scratch>`, then
  `$PC vm create --line primary` (a notarized DMG needs no staged package) and
  `$PC vm install --line primary --dmg <scratch>/Posato-<previous>.dmg`.
  `$PC launch -t desktop --vm primary` opens it. Complete that release's own
  first-run flow: its labels can differ from the current fixtures, so read
  them with `snapshot --format text` and tap them one by one. Answer system
  dialogs as they appear (see "Answering dialogs" below). If This Mac still
  shows **Check Mac setup**, tap it, answer the prompts, and tap **Check
  again** until the helper is enabled.
- **Consent:** `$PC update-consent -t desktop --vm primary --answer allow`
  right after setup. The result says `answered: true`; a second call waits
  and reports `answered: false`.
- **Seed data:** add `example.com` as in [Websites](./websites.md) so the
  update can prove that data survives.
- **Manual update:** open About Posato, tap **Check for Updates…**, then
  `$PC vm click --line primary --text "Install Update"` and, once the download
  finishes, `$PC vm click --line primary --text "Install and Relaunch"`. Wait
  with `vm wait-text --text "Install and Relaunch" --absent`, then
  `$PC launch -t desktop --vm primary --adopt` tracks the relaunched process.
- **Proof:** `$PC vm exec --line primary --script 'defaults read
  /Applications/Posato.app/Contents/Info CFBundleShortVersionString'` names
  the new version; answer the consent alert again with `update-consent`;
  `db query` still lists `example.com`; This Mac shows the helper enabled, or
  **Set up Posato** when the new release needs setup again. A pause started
  after the update blocks `example.com` (`observe --expect blocked`).
- **Upgrade from the previous release's development build** (a migration
  or a stored-state change, without notarized candidates): check out the tag
  in its own worktree (`git worktree add --detach ../posato-v<previous> v<previous>`,
  then copy `local.properties`) and build there with that tag's own
  `posato-control`.
  - Mac: `$PC build -t desktop` in the tag worktree, then copy its package
    into this worktree's staging with `ditto`, from the tag's
    `desktopApp/build/compose/binaries/main/verification-package/Posato.app`
    (`development-package` for tags before 1.4) to the same
    `verification-package` path here,
    `vm create`, onboard by hand (see [First install](./onboarding.md)), and
    create the state to migrate. Then `$PC build -t desktop` here,
    `vm sync`, and `launch` opens the upgraded database.
  - iPhone: `reset -t device --yes` and `install`, `launch` from the tag
    worktree, create the state with that release's labels, then `terminate`.
    `build -t device` and `install -t device` here install over it and keep
    the data. A scenario with `"launch": {"skip": true}` checks what the
    closed application leaves in force before the first launch.
- **Answering dialogs:** `$PC vm dialogs --line primary` names each open
  system dialog by its owning process. Answer `admin` with
  `vm prompt admin`, `gatekeeper` with `vm prompt gatekeeper`, and helper
  approval in Login Items with `vm prompt background`.

## Gotchas

- The consent alert is modal. Until it is answered, every tap on the window
  fails with the element not found or not hittable.
- After **Set up Posato**, the setup screen stays open until **Continue**;
  the app is not ready before that.
- Do not treat "password to allow this" on screen as an administrator
  dialog: macOS Background Items notices use the same words. Check
  `vm dialogs` for `admin` first.
- A clone created after another one can pause iCloud Keychain in the earlier
  clone. `vm create` resumes its own clone; run `vm icloud --resume` on every
  clone once all of them have booted.
- Installing waits for an active pause to end: **Install Update** then shows
  "The update can’t be installed now". End the session first.
- `vm install` refuses an existing installation; the manual move from a
  release without an updater uses `--replace`.
