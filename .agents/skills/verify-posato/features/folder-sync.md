# Folder sync

Every Posato application can keep its workspace in a folder that another
service keeps in sync, such as Dropbox, OneDrive, or Syncthing. The first
device chooses the folder and creates the workspace there; every other device
chooses its copy of the folder and enters a one-time pairing code that a
member shows with **Add a device**. Pause sets, sessions, and schedules then
reach every device the same way as with iCloud. **Remove workspace** deletes
the workspace from the folder, and the other devices report that sync needs
attention instead of creating it again.

## Sub-features

- `folder-link`: choosing a folder without a workspace creates one; choosing
  one with a workspace waits for a pairing code.
- `folder-pairing-link`: the code dialog shows a QR code of
  `posato://join?code=<code>` and **Share link** (phones) or **Copy link**
  (computers); opening the link fills the code, opens the folder section, and
  joins once the device waits for a code.
- `folder-pairing`: a member shows a 27-character code for 10 minutes; a
  device that enters it joins; a wrong, expired, or mistyped code is refused
  with its own message.
- `folder-exchange`: sets, session starts, early ends, and schedules cross
  between devices within about a minute of the folder syncing.
- `folder-catch-up`: a device that was off receives what changed meanwhile
  after it starts.
- `folder-removal`: removing the workspace empties the folder; other devices
  show "Sync needs attention" and never re-create it.
- `folder-ios`: the iPhone chooses its folder in the Files picker and syncs
  when Posato comes to the front.

## How to get to it (user POV)

- Session, the **Folder sync** row (on Apple devices the **iCloud** row,
  under "Or sync through a folder instead of iCloud."). Unlinked, it shows
  two numbered steps that can be done in either order: step 1, the
  **Folder path** field or **Choose folder…**, then **Use this folder** (a
  chosen folder becomes a chip with **Change**); step 2, in an empty folder
  **Create workspace**, in a folder with a workspace the scan hint (phones)
  and **Pairing code** with **Join**. A scanned link marks step 2 "Pairing
  code received"; the device joins by itself once both steps are done and
  says "The code has not reached this folder yet" while it waits.
- A member: **Add a device** shows the code; **Done** withdraws it.

## Driving it with posato-control

Preconditions: every device has its own local folder, and `$PC relay`
copies between them through `build/verification/sync-folder`, standing in
for a sync service. Start the relay detached, in its own session, so it
outlives the command that started it, and never start a second one.

- **Relay:** `$PC relay --line primary --android emulator-5560
  --duration-seconds 5400` for a Mac guest and the emulator, `--linux`
  for the Linux clone, or `--windows` for the Windows clone, whose Posato
  links `Z:\posato-sync` (`flow folder link -t windows --path
  'Z:\posato-sync'`). Proof: its final envelope reports `copies`.
- **Mac creates:** `$PC flow folder link -t desktop --vm primary --path
  '/Users/posato/PosatoSync'`. Proof: `outcome` is `linked`.
- **Mac offers and another Mac joins:** `flow folder offer` returns `code`;
  on the peer `flow folder link --path ...` returns `waitingForCode`, then
  `flow folder join --code <code>` returns `linked`; `flow folder done` on the
  first closes the code.
- **Linux or Android joins:** the same flows with `-t linux` or `-t
  android`: `flow folder link --path /mnt/shared/posato-sync` (Linux) or
  `--path /sdcard/PosatoSync` (Android) returns `waitingForCode`, then `flow
  folder join --code <code>` returns `linked`.
- **Exchange:** create a set with `flow set` on a Mac or start a pause on
  Android; proof is the other device's enforcement (`/etc/hosts` on Linux,
  the DNS answer on Android, `observe` on a Mac) within two minutes.
- **iPhone joins through iCloud Drive:** link a Mac clone to
  `~/Library/Mobile Documents/com~apple~CloudDocs/PosatoSync` (answer each
  "wants to access files managed by iCloud Drive" alert with
  `vm click --text Allow --exact`; a `vm exec` into that folder waits on
  the alert). On the iPhone tap **Choose folder…**, then the picker's back
  button (`id: BackButton`), `id: DOC.sidebar.item.iCloud Drive`,
  `id: PosatoSync, Folder`, and **Open**; type the Mac's code and press
  `return` (the keyboard hides **Join**). Proof: the
  Mac's set appears after the next `terminate` and `launch`.
- **Join by link:** open the member's link on the joining device instead of
  typing: Android `$PC android shell --script 'am start -a
  android.intent.action.VIEW -d "posato://join?code=<code>"'`, the iPhone
  `xcrun devicectl device process launch --device <udid> --payload-url
  "posato://join?code=<code>" app.posato.ios` (what a camera scan does), a
  Mac guest `$PC vm exec --line <line> --script 'open
  "posato://join?code=<code>"'`. Proof: step 2 reads "Pairing code
  received", and the device joins as soon as its folder is chosen.
- **Removal:** `flow folder remove` on one device. Proof: the folder is empty
  and the other device shows "Sync needs attention" within two minutes and
  the folder stays empty.

## Gotchas

- Two guests must not share one directory through the virtualization file
  share: the second guest reads stale contents. Each guest keeps its own
  folder and the relay copies.
- Create the workspace on one device before linking the others; two devices
  that create at the same moment before their folders synchronize diverge.
- A device that once chose another folder shows the folder chip's
  **Change**; `flow folder link` presses it so no earlier choice carries over.
- Until the offer reaches the joining device's folder, the app waits and
  checks again by itself (for up to 10 minutes, then "No offer for this
  code"); `flow folder join` only waits.
- A file the product rejects (wrong name, size, or content) is skipped and
  never accepted; when one device misses a change, compare the bundle files'
  checksums across folders before suspecting the product.
- A clone booted again with `vm boot` opens Posato at login from the package
  it had before `vm sync`. Adopt and terminate that process, then `launch`;
  the old process fails iCloud sync with a code-signature (`cdhash`)
  mismatch in the sync companion and tests the old build.
