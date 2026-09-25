# Posato Privacy Policy

Effective September 26, 2026.

Posato does not send your website list, app choices, sessions, or iCloud
workspace data to its developer. There is no Posato account, no analytics,
no advertising, and no Posato-operated server. Optional iCloud sync and
macOS update requests are described below.

## Data Posato keeps on your devices

To work, Posato stores on each device:

- the website domains you choose to pause;
- the apps you choose to pause on that device, stored as system-provided
  selections that Posato cannot read as app names on iPhone;
- your sessions: when they start and end, and whether they ended early;
- settings such as whether iCloud sync and the macOS helper are enabled.

This data stays in Posato's private storage on the device until you change or
remove it. This data, except app choices on iPhone, can also be included in your
device backups, such as iCloud Backup or Time Machine, under your backup
settings. On Mac, moving Posato to the Trash does not delete this data; it
remains in Posato's application data folder until you remove it.

When iCloud sync is on, Posato also keeps the history of synchronized changes
in its private storage on each linked device: every website added or removed,
changes to the shared app group name, and when each change was made, including
when past sessions started, when they were scheduled to end, and whether they
were ended early. Changing or removing a website does not erase its earlier
entries from this history. The history stays until **Remove workspace**
completes on that device or Posato's data is removed from it, and it can be
included in device backups like the data above.

Posato does not collect browsing history, the pages you visit, which pages were
blocked, how often you open apps, usage scores, or any other activity record.

## iCloud sync

iCloud sync is optional and off until you choose **Sync with iCloud**.

- Your website list, shared app group name, and session start and end are
  encrypted on your device before they are stored in the private CloudKit
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
the workspace too; their own copies of your website list, sessions, and change
history stay on those devices until you remove the workspace there. Websites
saved on the device where you remove the workspace stay on that device. Apple
may retain backups for a period under its own policies. If every copy of the
key is lost, synchronized data cannot be recovered.

## Screen Time on iPhone

On iPhone, Posato uses Apple's Screen Time framework to block the websites and
apps you choose. Your app choices are opaque system selections that stay on the
device; Posato does not receive app names or usage from Screen Time.

## Blocking on Mac

On Mac, Posato's background helper blocks chosen websites by routing browser
traffic through a local proxy on your Mac while a session is active. The helper
examines each request only long enough to allow or block it and does not store
or send it. To show its pause page in the current Safari or Chrome tab, Posato
may use Automation permission; it reads only the current tab's address at that
moment and keeps no record. Posato does not decrypt HTTPS traffic.

## Updates on Mac

Posato asks before checking for updates automatically. If you agree, it
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
Update requests are limited to the purposes described in Updates on Mac.
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
