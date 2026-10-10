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
  under "Or sync through a folder instead of iCloud."), the **Folder path**
  field or **Choose folder…** (or **Use another folder** after an earlier
  choice), **Use this folder**, **Sync with this folder**.
- A member: **Add a device** shows the code; **Done** withdraws it.
- A new device: after choosing the folder, **Pairing code** and **Join**.

## Driving it with posato-control

Preconditions: every device has its own local folder, and `$PC relay`
copies between them through `build/verification/sync-folder`, standing in
for a sync service. Start the relay detached, in its own session, so it
outlives the command that started it, and never start a second one.

- **Relay:** `$PC relay --line primary --android emulator-5560
  --duration-seconds 5400` for a Mac guest and the emulator, or `--linux`
  for the Linux clone. Proof: its final envelope reports `copies`.
- **Mac creates:** `$PC flow folder link -t desktop --vm primary --path
  '/Users/admin/PosatoSync'`. Proof: `outcome` is `linked`.
- **Mac offers and another Mac joins:** `flow folder offer` returns `code`;
  on the peer `flow folder link --path ...` returns `waitingForCode`, then
  `flow folder join --code <code>` returns `linked`; `flow folder done` on the
  first closes the code.
- **Linux or Android joins:** after choosing the folder (Linux
  `/mnt/shared/posato-sync`, Android `/sdcard/PosatoSync`), click
  **Pairing code**, type the code in lower case, and press **Join**. Proof:
  `Add a device` appears.
- **Exchange:** create a set with `flow set` on a Mac or start a pause on
  Android; proof is the other device's enforcement (`/etc/hosts` on Linux,
  the DNS answer on Android, `observe` on a Mac) within two minutes.
- **Removal:** `flow folder remove` on one device. Proof: the folder is empty
  and the other device shows "Sync needs attention" within two minutes and
  the folder stays empty.

## Gotchas

- Two guests must not share one directory through the virtualization file
  share: the second guest reads stale contents. Each guest keeps its own
  folder and the relay copies.
- Create the workspace on one device before linking the others; two devices
  that create at the same moment before their folders synchronize diverge.
- A device that once chose another folder shows **Use another folder**;
  `flow folder link` presses it so no earlier choice carries over.
- A pairing code fails with "No offer for this code" until the offer reaches
  the joining device's folder; `flow folder join` presses **Join** again until
  it does.
- A file the product rejects (wrong name, size, or content) is skipped and
  never accepted; when one device misses a change, compare the bundle files'
  checksums across folders before suspecting the product.
