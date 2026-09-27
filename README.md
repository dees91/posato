# Posato

**Pause. Then choose.**

Posato blocks the websites and apps you choose for a timed pause on your Mac
or iPhone, now or on a schedule, within the [limits](#limits) below.

<p align="center">
  <img src=".github/assets/demo.gif" width="864" alt="Posato demo: choose websites and apps to pause on a Mac and an iPhone, set a duration, start a session, and add a weekday schedule">
</p>

Watch the [full 53-second walkthrough](https://posato.app/media/walkthrough.mp4),
which adds the Mac app picker, ending a session early, and a scheduled pause
that starts on its own.

[Quick start](#quick-start) · [How it works](#how-it-works) ·
[Limits](#limits) · [Privacy](#privacy) · [Documentation](#project-documentation)

No Posato account, no analytics, and no Posato-operated server.
Open source under the [Apache License 2.0](LICENSE).

## Quick start

<p>
  <a href="https://github.com/dees91/posato/releases/latest"><img src="website/public/badges/download-for-mac.svg" width="186" height="56" alt="Download Posato for Mac"></a>
  <a href="https://apps.apple.com/app/posato/id6812237585"><img src="website/public/badges/download-on-the-app-store.svg" width="186" height="56" alt="Download Posato on the App Store"></a>
</p>

- **Mac:** download the signed and notarized DMG from
  [GitHub Releases](https://github.com/dees91/posato/releases/latest), open it, and move Posato to your Applications folder.
  Posato 1.1 and later can check for updates after you agree; to move from 1.0,
  quit Posato and replace it with the new version.
- **iPhone:** install Posato from the [App Store](https://apps.apple.com/app/posato/id6812237585).

Posato targets **macOS 15 or later on Apple silicon** and **iOS 18 or later**;
the [platform matrix](docs/product/limits-and-platforms.md#supported-platforms)
records what was checked on each of them. Intel Macs, Android, Linux, and Windows are
[planned for later releases](docs/product/limits-and-platforms.md#planned-platforms).

To build it yourself, follow the [build instructions](docs/development/README.md#build-from-source).
Unsigned builds let you explore the apps; blocking and sync require Apple
Development signing. The iOS Simulator cannot use Screen Time controls.

## How it works

### Choose what to pause

Add exact website domains, then choose apps on each device. App choices stay
on that device; only the shared app group name synchronizes.

<p align="center">
  <a href=".github/assets/step-websites.png"><img src=".github/assets/step-websites.png" width="960" alt="Paused items on a Mac and an iPhone, side by side, showing the sample domains example.com and example.net"></a>
</p>

### Start a timed pause

Set a duration from **5 minutes to 24 hours**, review your choices, and start
the session. Posato blocks your chosen websites and apps on that device,
within the [limits](#limits) below. On a Mac, a one-time setup lets pauses and
schedules start without asking for your password each time. It ends at the selected time or when you
deliberately end it early. On iPhone, restrictions can linger after it ends.

<p align="center">
  <a href=".github/assets/step-duration.png"><img src=".github/assets/step-duration.png" width="960" alt="Session setup on a Mac and an iPhone, side by side, with a 45-minute pause selected"></a>
</p>

Screenshots and the demo show synthetic choices in the real apps; see the
[capture provenance](video/README.md#capture-provenance).

### Plan pauses ahead

Add a schedule with a name, weekdays, and hours, such as weekday mornings from
9 to 11. It starts and ends on its own on each device you set up, even with
Posato's window closed or the iPhone app closed. Skip the next one or end one
early when plans change. Posato can tell you when a pause ends or when one
starts on another device.

<p align="center">
  <a href=".github/assets/step-schedules.png"><img src=".github/assets/step-schedules.png" width="960" alt="Schedules on a Mac and an iPhone, side by side, with a weekday schedule named Deep work"></a>
</p>

### Share the session with iCloud

Choose **Sync with iCloud** on one Mac and one iPhone signed in to the same
Apple Account to share your website list, sessions, and schedules. No QR code
or invitation is needed. Delivery is best effort. Update Posato on every
device: once a schedule is saved, a device still on Posato 1.1 stops syncing.

## Limits

Posato adds deliberate friction; it is not a lock you cannot open.

- **Mac:** blocking works while Posato runs, including in the menu bar with
  its window closed. Quitting Posato stops it and new scheduled starts.
  Blocking needs administrator approval; after the one-time setup, starts no
  longer ask for it. Paused apps are quit, so save your work first.
  Website blocking covers Safari and Google Chrome Stable using the system
  proxy.
- **iPhone:** restrictions can linger after a session ends. For sessions under
  15 minutes, they clear only when Posato is open at the end or the next time
  you open it.
- **Sync:** delivery is best effort. If every copy of your workspace key is
  lost, synchronized data cannot be recovered.

Read the [full blocking and sync limits](docs/product/limits-and-platforms.md#limits)
for browser coverage, VPN and Private Relay behavior, resuming after sleep,
and data recovery boundaries.

## Privacy

Posato does not collect your data. Your websites, app choices, sessions, and
schedules stay on your devices; with iCloud sync on, changes are encrypted on your device
before they are stored in your private iCloud database. On Mac, update checks go
to GitHub Releases only after you agree. See the [privacy policy](PRIVACY.md).

## Support and security

- Report bugs, ask questions, and share ideas in [GitHub Issues](../../issues).
  Please do not include website names, app names, device details, screenshots,
  or logs.
- Report security vulnerabilities privately, as described in the
  [security policy](SECURITY.md).
- Pull requests are not accepted at the moment; see
  [contributing](CONTRIBUTING.md).

## Project documentation

- [Development guide](docs/development/README.md) and
  [engineering quality contract](docs/development/engineering-quality-contract.md)
- [MVP scope](docs/product/mvp-scope.md) and [design system](DESIGN.md)
- [Architecture decisions](docs/decisions/) and
  [threat model](docs/security/apple-mvp-threat-model.md)
- [Project wiki](docs/wiki/index.md), including feasibility results that informed
  the design, and the [task workflow](docs/tasks/README.md)

## License

Posato is licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE)
and [third-party notices](THIRD_PARTY_NOTICES.md).
