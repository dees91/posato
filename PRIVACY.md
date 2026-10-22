# Posato Privacy Policy

Effective October 21, 2026.

Posato does not send your pause sets, website list, app choices, sessions,
schedules, or workspace data to its developer. There is no Posato account, no
analytics, no advertising, and no Posato-operated server. This policy covers
Posato for Mac, iPhone, Linux, and Android. Optional iCloud or folder sync,
the blocking on each device, and macOS update requests are described below.

## Data Posato keeps on your devices

To work, Posato stores on each device:

- your pause sets: their names, which set is the default, which website
  domains each contains, and which apps you chose for each on that device,
  stored as system-provided selections that Posato cannot read as app names
  on iPhone; on Mac, Linux, and Android, Posato stores which apps they are;
- your sessions: when they start and end, which pause set each used, and
  whether they ended early;
- your schedules: their names, their pause sets, the weekdays and times they
  repeat, whether each is on, and which scheduled pauses you skipped or ended
  early;
- settings such as whether sync is on, which folder you chose for folder
  sync, and whether the macOS helper is enabled;
- when sync is on, when this device last finished syncing.

This data stays in Posato's private storage on the device until you change or
remove it. On Mac, iPhone, and Linux, this data, except app choices on iPhone,
can also be included in your device backups, such as iCloud Backup, Time
Machine, or a backup of your Linux home folder, under your backup settings.
On Android, Posato excludes its data from Android backups and device
transfers. On Mac, moving Posato to the Trash does not delete this data; it
remains in Posato's application data folder until you remove it. On Linux,
removing the package does not delete it either; it remains in Posato's folder
in your home folder's application data until you remove it.

When sync is on, through iCloud or a folder, Posato also keeps the history of synchronized changes
in its private storage on each linked device: every pause set created,
renamed, or deleted, every website added to or removed from a set, which set
is the default and which set each session and schedule used, and when each
change was made, including when past sessions started, when they were scheduled to end, and whether they
were ended early. Entries from before Posato 1.3 also record changes to the
shared app group name it used then. Changing or removing a website or a set
does not erase its earlier entries from this history. The history stays until **Remove workspace**
completes on that device or Posato's data is removed from it, and it can be
included in device backups like the data above.

Posato does not collect browsing history, the pages you visit, which pages were
blocked, how often you open apps, usage scores, or any other activity record.

## iCloud sync

iCloud sync is optional and off until you choose **Sync with iCloud**.

- Your pause sets (their names, their websites, and which is the default),
  in changes from before Posato 1.3 the shared app group name, session start
  and end, and your schedules, including skipped and ended scheduled pauses,
  are encrypted on
  your device before they are stored in the private CloudKit
  database of your own iCloud account. Posato's developer cannot read or access
  them.
- Each change, and each device's registration, is stored as a separate
  encrypted record, and these records
  accumulate in your iCloud database until you choose **Remove workspace**;
  Posato does not clean them up automatically.
- The encryption key is shared between your devices through iCloud Keychain.
- App choices are never synchronized; each device keeps its own.
- Apple operates iCloud and can observe technical information needed to provide
  it, such as your account association, timing, record sizes, and counts, under
  Apple's own privacy policy.

**Remove workspace** deletes Posato's synchronized records from your iCloud
database, its history of synchronized changes on the device where you remove it,
and the workspace key from iCloud Keychain, so your other devices lose access to
the workspace too; their own copies of your pause sets, sessions, and change
history stay on those devices until you remove the workspace there. Pause
sets and websites saved on the device where you remove the workspace stay on
that device. Apple
may retain backups for a period under its own policies. If every copy of the
key is lost, synchronized data cannot be recovered.

## Folder sync

Folder sync is optional and off until you choose a folder in Posato. It is
the only sync on Linux and Android, and on Mac and iPhone it replaces iCloud
sync for that workspace.

- Posato writes a `Posato` folder inside the folder you choose. It holds one
  file with random workspace identifiers and, for each change, a separate
  file encrypted on your device with the same encryption as iCloud sync: your
  pause sets (their names, their websites, and which is the default), session
  start and end, and your schedules, including skipped and ended scheduled
  pauses. App choices are never synchronized; each device keeps its own.
- These files accumulate in the folder until you choose **Remove workspace**,
  which deletes Posato's folder from it. Posato does not clean them up
  automatically.
- Posato does not upload anything itself. The service that keeps the folder
  in sync, such as Dropbox, OneDrive, iCloud Drive, or Syncthing, copies the
  files under its own privacy policy. It cannot read their content, but it can
  observe technical information such as file names, which are random, file
  sizes, counts, and when files change, and it may keep earlier versions or
  deleted files for a while.
- In folder sync, each device keeps the workspace key in a file in Posato's
  private storage that only your user account can read, not in iCloud
  Keychain. On Android, the file is also sealed with a key that never leaves
  that phone's Android Keystore. On Mac, iPhone, and Linux the key file can be
  included in device backups like the data above.
- To add a device, Posato on a device in the workspace shows a one-time
  pairing code, also as a QR code and as a `posato://` link that contains only
  that code. It places the workspace key in the folder, encrypted with a key
  derived from the code. It deletes that file when the new device has joined,
  when you close the code, or, once the code has expired after 10 minutes,
  the next time Posato starts or shows a new code. Posato shows the QR code
  but does not use your camera. If you share the link through a message,
  mail, or the clipboard, it passes through the app you choose. Anyone who
  has the code or the link and can open the folder, or a copy of that file
  in its file history, can recover the workspace key, even after the code
  expires, so share them only with your own devices.

**Remove workspace** deletes Posato's folder from the folder you chose, if
this device can still reach it, and the workspace key file and the history of
synchronized changes on the device where you remove it. Other devices keep
their own copies of your pause sets, sessions, change history, and key file
until you remove the workspace there. The sync service may keep earlier
versions of the files under its own policy. If every copy of the key is lost,
synchronized data cannot be recovered.

## Screen Time on iPhone

On iPhone, Posato uses Apple's Screen Time framework to block the websites and
apps you choose. Your app choices are opaque system selections that stay on the
device; Posato does not receive app names or usage from Screen Time. So that
a schedule can start while Posato is closed, Posato keeps a copy of your
schedules and each pause set's websites and app choices in storage shared
only with its own Screen Time extension on the same iPhone. It never leaves
the device, is excluded from device backups, and is deleted when Posato's data
or the workspace is removed.

## Blocking on Mac

On Mac, Posato's background helper blocks chosen websites by routing browser
traffic through a local proxy on your Mac while a session is active. The helper
examines each request only long enough to allow or block it and does not store
or send it. To show its pause page in the current Safari or Chrome tab, Posato
may use Automation permission; it reads only the current tab's address at that
moment and keeps no record. Posato does not decrypt HTTPS traffic.

If you choose to start sessions on a Mac without an administrator password,
the helper keeps a record of that permission in a system file that only the
system can read. The record holds your Mac account's user number and system
identifier and your Mac's hardware identifier. It never leaves your Mac and
is deleted when you turn the option off or remove Posato from this Mac.

## Blocking on Linux

On Linux, Posato installs a pausing service that runs with system
permissions, once, with your password. Only your user account and the system
can send it requests. While a pause runs, the service:

- adds each paused website and its `www` name to the system file
  `/etc/hosts`, which other user accounts on the computer can read, and removes
  them when the pause ends;
- ends the paused programs you chose that run under your user account,
  checking which program each running process is, without reading or keeping
  what you do in it.

The service keeps your user name and user number in a system configuration
file, and the pause it enforces, with its websites and program locations, in
a system file that only the system can read, so a restart keeps the pause.
After the pause ends, that file keeps only the session's identifier until
Posato reads it. Removing the package removes the service, restores
`/etc/hosts`, and deletes both files. Nothing the service handles leaves your
computer.

## Blocking on Android

On Android, Posato needs several permissions, each given in Android's own
settings:

- **VPN.** While a pause with websites runs, Posato runs a local VPN that
  carries only name lookups, the questions your phone asks to find a website's
  address. It answers paused names itself and passes every other lookup to the
  DNS server of the network you are on, or to Google Public DNS when that
  network offers none Posato can use. No other traffic enters the VPN, and
  Posato does not record or keep lookups.
- **Usage access.** While a pause with apps runs, Posato checks about twice a
  second which app is in front, so it can cover a paused app with its block
  screen. It keeps only the app in front at that moment, in memory, and does
  not record app usage.
- **Display over other apps**, to show the block screen.
- **Notifications, exact alarms, and running without battery limits**, so
  that pauses and schedules start and end on time while Posato is closed. A
  silent notification shows while Posato keeps them running.
- **Access to all files**, so that Posato can read and write the folder you
  choose for folder sync. Posato reads and writes only that folder.

Posato reads the list of apps you can open from the launcher, so that you can
choose apps to pause; the list stays on the phone. The pause it enforces, with
its websites and app package names, is kept in Posato's private storage until
the pause ends, so a restart keeps it.

## Export and import

You can export your pause sets to a file and import it on another device.
The file holds your pause sets' names and websites, which set is the
default, and your schedules. It never holds app choices, sessions, history,
device names, or sync keys. Posato writes the file only where you save or
share it, and it sends nothing anywhere itself.

- Without a code, anyone who gets the file can read your pause sets and
  websites.
- With **Protect with a code**, the file is encrypted with a key derived
  from a nine-character code that Posato shows once and does not keep. The
  code stops a casual reader of a file that reaches the wrong person, but
  someone who has the file and spends a lot of computing power on guessing
  could open it, so send the file and the code only where you trust them.
- On iPhone, a shared file is a temporary copy that Posato deletes when the
  share sheet closes, and an imported file is read from a temporary copy
  that Posato deletes after reading it.

An import changes your pause sets and schedules like edits made by hand; if
sync is on, the changes reach your other devices.

## Notifications

On Mac and iPhone, Posato can show local notices when a pause ends or starts
on another device. They are created on your device by your device's
notification system; Posato sends nothing to a server for them. You can turn
them off in About Posato or in your device's settings.

## Updates on Mac

Posato for iPhone, Linux, and Android does not check for updates itself. On
Mac, Posato asks before checking for updates automatically. If you agree, it
checks once a day while the app is running. You can turn automatic checks
off or choose Check for updates yourself. Each update requires your action
to download and install, and installation waits until no session is active.

Update information and downloads come from GitHub Releases through Sparkle,
an update library included in Posato. Requests do not contain your website
list, app choices, sessions, iCloud data, an installation identifier, or a
system profile. Posato compares versions and system requirements on your Mac.

GitHub and its delivery providers can receive your IP address, request time,
the resource requested, and technical connection information. A download
address identifies the version you request. They handle this information
under [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
GitHub makes aggregate download counts available; Posato adds no usage
analytics or tracking identifier. Opening a release or support link in your
browser is subject to that website's policies and your browser settings.

Update preferences are stored on this Mac and are not synchronized through
iCloud. Sparkle stores update-check state and temporary update files locally.

## Diagnostics

Posato does not collect or upload diagnostics, crash reports, or telemetry.
Apart from the sync you choose, the lookups the Android VPN passes on, and the
update requests described in Updates on Mac, Posato makes no network requests
of its own.
Your operating system or app store may collect diagnostics under your device
settings and their own policies; Posato does not access or add to them.

If you ask for help, share only what you choose. Please do not include website
names, app names, device details, screenshots, or logs in public reports.

## Children

Posato is not designed for managing another person's device and does not
knowingly collect data from anyone.

## Changes

Changes to this policy will be published at the same address with a new
effective date.

## Contact

Privacy questions: privacy@posato.app
