# Posato

**Pause. Then choose.**

Posato blocks the websites and apps you choose for a timed pause on your Mac
or iPhone, within the [limits](#limits) below.

<p align="center">
  <img src=".github/assets/demo.gif" width="960" alt="Posato on Mac and iPhone: choose paused items, set a duration, and start a session">
</p>

Watch the [full 47-second walkthrough](https://github.com/user-attachments/assets/a0358f5b-8ba9-4a4c-b462-21c218f5d843),
which adds the review screen and more time for each step.

[Quick start](#quick-start) · [How it works](#how-it-works) ·
[Limits](#limits) · [Privacy](#privacy) · [Documentation](#project-documentation)

No Posato account, no analytics, and no Posato-operated server.
Open source under the [Apache License 2.0](LICENSE).

## Quick start

Posato is **pre-release**, with
[no official download yet](docs/product/limits-and-platforms.md#availability).
It targets **macOS 15 or later on Apple silicon** and **iOS 18 or later**; the
[release test matrix](docs/product/limits-and-platforms.md#supported-platforms)
is still being confirmed. Intel Macs, Android, Linux, and Windows are
[planned for later releases](docs/product/limits-and-platforms.md#planned-platforms).

To try it from source, follow the [build instructions](docs/development/README.md#build-from-source).
Unsigned builds let you explore the apps; blocking and sync require Apple
Development signing. The iOS Simulator cannot use Screen Time controls.

## How it works

### Choose what to pause

Add exact website domains, then choose apps on each device. App choices stay
on that device; only the shared app group name synchronizes.

<p align="center">
  <img src="video/public/mac-websites.png" width="660" alt="Mac Paused items showing the synthetic domains example.com and example.net">
  <img src="video/public/iphone-websites.png" width="230" alt="iPhone Paused items with synthetic website choices">
</p>

### Start a timed pause

Set a duration from **5 minutes to 24 hours**, review your choices, and start
the session. Posato blocks your chosen websites and apps on that device,
within the [limits](#limits) below. It ends at the selected time or when you
deliberately end it early. On iPhone, restrictions can linger after it ends.

<p align="center">
  <img src="video/public/mac-duration.png" width="660" alt="Mac session setup with a 25-minute pause selected">
  <img src="video/public/iphone-duration.png" width="230" alt="iPhone session duration picker">
</p>

Screenshots and the demo show synthetic choices in the real apps; see the
[capture provenance](video/README.md#capture-provenance).

### Share the session with iCloud

Choose **Sync with iCloud** on one Mac and one iPhone signed in to the same
Apple Account to share your website list and sessions. No QR code or invitation
is needed. Delivery is best effort, and the Mac needs administrator approval
before blocking starts.

## Limits

Posato adds deliberate friction; it is not a lock you cannot open.

- **Mac:** Posato must stay open. Starting or resuming blocking needs
  administrator approval. Paused apps are quit, so save your work first.
  Website blocking covers Safari and Google Chrome Stable using the system proxy.
- **iPhone:** restrictions can linger after a session ends. For sessions under
  15 minutes, they clear when Posato is open at the end or the next time you
  open it.
- **Sync:** delivery is best effort. If every copy of your workspace key is
  lost, synchronized data cannot be recovered.

Read the [full blocking and sync limits](docs/product/limits-and-platforms.md#limits)
for browser coverage, VPN and Private Relay behavior, resuming after sleep,
and data recovery boundaries.

## Privacy

Posato does not collect your data. Your websites, app choices, and sessions stay
on your devices; with iCloud sync on, changes are encrypted on your device
before they are stored in your private iCloud database. See the
[privacy policy](PRIVACY.md).

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
