# App Store listing — English (US)

Status: maintainer accepted, 2026-09-14; copy edited with Clarity as requested.
Uploaded to App Store Connect by `RELEASE-002` on 2026-09-17 with the iPad set
below and submitted to App Review for version 1.0.0 (3). `DOCS-003` prepared
the 1.2 description, What's New, and screenshots on 2026-09-27 for
`RELEASE-004`; nothing was uploaded. `DOCS-004` prepared the 1.3 subtitle,
description, What's New, and screenshots on 2026-10-02 for `RELEASE-005`.
`DOCS-005` prepared the 1.4 description, What's New, and screenshots on
2026-10-10 for `RELEASE-006`. `DOCS-006` prepared the 1.5 description and
What's New on 2026-10-21 for `RELEASE-007`.

## Name

Posato

## Subtitle

Space for what matters.

The product line "A little space. For what matters." (`WEB-002`) has 33
characters, over the subtitle's 30; the maintainer chose this shorter form on
2026-10-02.

## Description

The description is [description.txt](description.txt), the exact text
`posato-provisioning store prepare --description` uploads. Since 1.2 it adds
schedules, schedule sync, the one-time Mac setup, and the advice to update
every device; 1.3 adds pause sets and Intel Macs; 1.4 adds the longer quick
choices; 1.5 adds folder sync, joining by QR code, export and import, and
Posato on Linux. It does not name Android, because App Review guideline 2.3.10 does not
allow other mobile platforms in the metadata. Earlier text is in Git history.

## Keywords

focus,pause,distractions,websites,apps,screen time,concentration,break,intention,quiet

## Screenshots

The set contains four unmodified real iPhone Simulator captures in Dark Mode, with synthetic website entries, one schedule, and local-only setup:

- [Paused websites](screenshots/iphone-6.9/01-paused-websites.png)
- [Session duration](screenshots/iphone-6.9/02-session-duration.png)
- [Schedules](screenshots/iphone-6.9/03-schedules.png)
- [About Posato](screenshots/iphone-6.9/04-about-posato.png)

All captures are 1320 × 2868 RGB PNGs with no alpha channel. They show the
accepted About Posato entry. The maintainer accepted the three-screen set on
2026-09-14 and requested Dark Mode for the final captures. `RELEASE-003`
recaptured the same three screens for 1.1.0 on 2026-09-25, because the
website list now names the included `www` variant and About shows the new
version. `DOCS-003` recaptured the set for 1.2.0 on 2026-09-27 and added
Schedules as the third screen, which moved About Posato to fourth. `DOCS-004`
recaptured both sets for 1.3.0 on 2026-10-02: the first screen now shows the
pause set Focus with its websites, and Session and Schedules show the set;
the maintainer accepted it on 2026-10-04. `DOCS-005` recaptured both sets for
1.4.0 on 2026-10-10 with the `DESIGN-004` interface and the `SESSION-007`
quick choices, on an iPhone 17 Pro Max Simulator with iOS 26.5, because
Xcode 27 no longer ships that device; the maintainer accepted the 1.4 set
(`user-confirmed`, 2026-10-10, by questionnaire).

The captures show session setup, paused websites, and About Posato without private
account, application, or device labels. Simulator captures must not imply that Screen
Time authorization or enforcement succeeded on that target.

The iPad set repeats the three screens on an iPad Pro 13-inch (M5) Simulator in
Dark Mode with the same synthetic websites, as 2064 × 2752 RGB PNGs without an
alpha channel; the maintainer accepted it on 2026-09-17 and `RELEASE-003`
recaptured it for 1.1.0 on 2026-09-25, in portrait and with the iPad wording
from `IOS-004`:

- [Paused websites](screenshots/ipad-13/01-paused-websites.png)
- [Session duration](screenshots/ipad-13/02-session-duration.png)
- [Schedules](screenshots/ipad-13/03-schedules.png)
- [About Posato](screenshots/ipad-13/04-about-posato.png)

### Capture recipe

Capture each store locale on two Simulators: iPhone 17 Pro Max (1320 × 2868)
and iPad Pro 13-inch (M5) in portrait (2064 × 2752). When Xcode no longer lists
the iPhone, create it with `xcrun simctl create` from the
`com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro-Max` device type and an
installed iOS 26 runtime. The About screen shows the build's
`MARKETING_VERSION`; capture from a build of the release version, even when
`Version.xcconfig` is bumped only for the capture.

One [`posato-control`](../../../tools/posato-control/README.md) command
captures a locale's set:

```bash
tools/posato-control/build/install/posato-control/bin/posato-control \
  store capture -t sim --udid <udid> --locale en-US \
  --out docs/store/en-US/screenshots/iphone-6.9 --prepare
```

`--locale` names a directory here, and its `capture.json` gives the launch
language and locale (`en`, `en_US`), the schedule name (`Deep work`), and the
optional set name (`Focus`). A new language needs only its own directory with
a `capture.json`. The command switches the Simulator to Dark Mode, launches
Posato in that language while the Simulator stays English, sets the status bar
to the current time (so it matches Session's "Ends at"), battery 100 %, and
three Wi-Fi bars, and writes `01-paused-websites.png`,
`02-session-duration.png`, `03-schedules.png`, and `04-about-posato.png` as RGB
PNGs without an alpha channel. It clears the status-bar override even when a
step fails. Every control is pressed by its id, so the path is the same in
every language.

`--prepare` first resets Posato, skips onboarding with
`first-install-skip.json`, adds `news.example`, `social.example`, and
`video.example` to the first set, renames that set when `capture.json` names
one, and adds the schedule for 09:00-12:00 on weekdays with the default set.
It does this in English with a 24-hour clock (`en_GB`), because the
onboarding scenario and the schedule editor flow read English labels and turn
24-hour wheels; the names come from `capture.json`. Leave
`--prepare` out only to capture the same locale again on a Simulator it
already prepared; another locale needs its own `--prepare`, or its
screenshots show the previous locale's set and schedule names. A set without a `setName` keeps the
name the app gives the first set in each language.

For a narrow pass, run the same command on a small Simulator, such as iPhone
16e or iPhone SE, with `--out build/verification/store-narrow-<locale>/`, and
look for truncated or wrapped text before accepting a translation. Those
captures stay in ignored `build/verification/`.

`posato-provisioning store prepare --screenshots docs/store/en-US/screenshots`
uploads both sets in file-name order; see the
[iOS App Store release](../../development/apple-provisioning.md#ios-app-store-release).

## What's New in 1.5.0

[whats-new-1.5.0.txt](whats-new-1.5.0.txt) is the text for
`store prepare --whats-new`. It names only what changed on iPhone and iPad.

## What's New in 1.4.0

[whats-new-1.4.0.txt](whats-new-1.4.0.txt) was the 1.4.0 text.

## What's New in 1.3.0

[whats-new-1.3.0.txt](whats-new-1.3.0.txt) was the 1.3.0 text.

## What's New in 1.2.0

[whats-new-1.2.0.txt](whats-new-1.2.0.txt) was the 1.2.0 text.

## What's New in 1.1.0

- Edit your websites and apps straight from the Session screen. The search field there now clearly filters your chosen items.
- www.example.com and example.com now count as one website. Posato tells you so when you add one.
- Adding websites during setup is smoother: the field stays ready for the next website and shows how many you have saved.
- On iPad, Posato now says iPad instead of iPhone and shows a sidebar in landscape.

## Store settings

Accepted by the maintainer on 2026-09-17: primary category Productivity; free in
all territories, including new ones; age rating 4+ with every questionnaire
answer none or no (Parental Controls no, because Posato only restricts the
person who runs it); copyright "2026 Piotr Krawczyk"; no third-party content;
support `https://posato.app/support/` and marketing `https://posato.app/`;
manual release after approval; review notes explain that no account is needed
and that the Mac app is not required.

Use portrait PNGs at 1320 × 2868, without an alpha channel, for the 6.9-inch
slot. Apple accepts this size; a 6.5-inch set is required only when a 6.9-inch
set is absent. Checked 2026-09-14 against Apple's
[screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/).

Screenshots are release artwork, explicitly tracked by this task. Verification
captures, logs, and identifiers remain under ignored `build/verification/`.
