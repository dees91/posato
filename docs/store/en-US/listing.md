# App Store listing — English (US)

Status: maintainer accepted, 2026-09-14; copy edited with Clarity as requested.
Uploaded to App Store Connect by `RELEASE-002` on 2026-09-17 with the iPad set
below and submitted to App Review for version 1.0.0 (3). `DOCS-003` prepared
the 1.2 description, What's New, and screenshots on 2026-09-27 for
`RELEASE-004`; nothing was uploaded. `DOCS-004` prepared the 1.3 subtitle,
description, What's New, and screenshots on 2026-10-02 for `RELEASE-005`.

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
every device; 1.3 adds pause sets and Intel Macs. Earlier text is in Git
history.

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
Schedules as the third screen, which moved About Posato to fourth; it awaits
the maintainer's acceptance.

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

Repeat these steps for each Simulator: iPhone 17 Pro Max (1320 × 2868) and
iPad Pro 13-inch (M5) in portrait (2064 × 2752). Drive the app with
[`posato-control`](../../../tools/posato-control/README.md) and name the booted
Simulator with `--udid <udid>`.

1. Run `xcrun simctl ui <udid> appearance dark`.
2. Install fresh with `launch -t sim --fresh`, then run
   `tools/posato-control/fixtures/scenarios/first-install-skip.json`.
3. Add the synthetic websites `news.example`, `social.example`, and
   `video.example`, then add a schedule named `Deep work` with the default
   weekdays and hours.
   The About screen shows the build's `MARKETING_VERSION`; capture from a build
   of the release version, even when `Version.xcconfig` is bumped only for the
   capture.
4. Relaunch without `--fresh` before the first capture, so the field shows the
   default hint instead of the feedback shown after an add.
5. Capture the session duration screen first, with the 25-minute preset
   selected. Before capturing, run
   `xcrun simctl status_bar <udid> override --time <HH:MM now> --batteryState discharging --batteryLevel 100 --wifiBars 3`,
   so the status bar matches the "Ends at" time.
6. Capture About Posato, the paused items, and Schedules with the same
   status-bar time. The About screen hides the tab bar, so go Back before
   switching tabs.
7. Capture each screen with `xcrun simctl io <udid> screenshot raw.png`. Then
   remove the alpha channel with `ffmpeg -i raw.png -pix_fmt rgb24 <out>.png`,
   naming the output after the files above.
8. Clear the override with `xcrun simctl status_bar <udid> clear`.

`posato-provisioning store prepare --screenshots docs/store/en-US/screenshots`
uploads both sets in file-name order; see the
[iOS App Store release](../../development/apple-provisioning.md#ios-app-store-release).

## What's New in 1.3.0

[whats-new-1.3.0.txt](whats-new-1.3.0.txt) is the text for
`store prepare --whats-new`.

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
