# App Store listing — English (US)

Status: maintainer accepted, 2026-09-14; copy edited with Clarity as requested.
Uploaded to App Store Connect by `RELEASE-002` on 2026-09-17 with the iPad set
below and submitted to App Review for version 1.0.0 (3).

## Name

Posato

## Subtitle

Pause. Then choose.

## Description

Choose the websites and apps you want to step away from, then start a pause
on your iPhone. Give yourself time to read, work, or be somewhere else.

PAUSE ON YOUR TERMS

Set a duration from 5 minutes to 24 hours. Posato uses Apple's Screen Time
controls to pause your choices for that session. You can deliberately end it
early or remove Posato at any time. The pause gives you a moment to reconsider
before opening something out of habit.

OPTIONAL ICLOUD SYNC

Link your iPhone and Mac through your Apple Account to sync website choices
and sessions. App choices stay on each device. Changes are encrypted on your
device before they reach your private iCloud database.

Sync can be delayed or fail to arrive, and Posato cannot wake a sleeping
device. The Mac app is a separate download and needs administrator approval
before blocking starts.

NO POSATO ACCOUNT

No analytics, usage scores, streaks, or Posato-operated server. Your choices
stay on your devices unless you turn on iCloud sync. Posato is open source
under the Apache License 2.0.

BEFORE YOU START

Screen Time permission is required on iPhone. The system may keep restrictions
in place after a session ends. For sessions shorter than 15 minutes, keep
Posato open at the end or open it again to clear them.

## Keywords

focus,pause,distractions,websites,apps,screen time,concentration,break,intention,quiet

## Screenshots

The accepted set contains three unmodified real iPhone Simulator captures in Dark Mode, with synthetic website entries and local-only setup:

- [Paused websites](screenshots/iphone-6.9/01-paused-websites.png)
- [Session duration](screenshots/iphone-6.9/02-session-duration.png)
- [About Posato](screenshots/iphone-6.9/03-about-posato.png)

All captures are 1320 × 2868 RGB PNGs with no alpha channel. They show the
accepted About Posato entry. The maintainer accepted the three-screen set on
2026-09-14 and requested Dark Mode for the final captures. `RELEASE-003`
recaptured the same three screens for 1.1.0 on 2026-09-25, because the
website list now names the included `www` variant and About shows the new
version.

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
- [About Posato](screenshots/ipad-13/03-about-posato.png)

### Capture recipe

Repeat these steps for each Simulator: iPhone 17 Pro Max (1320 × 2868) and
iPad Pro 13-inch (M5) in portrait (2064 × 2752). Drive the app with
[`posato-control`](../../../tools/posato-control/README.md) and name the booted
Simulator with `--udid <udid>`.

1. Run `xcrun simctl ui <udid> appearance dark`.
2. Install fresh with `launch -t sim --fresh`, then run
   `tools/posato-control/fixtures/scenarios/first-install-skip.json`.
3. Add the synthetic websites `news.example`, `social.example`, and
   `video.example`.
4. Relaunch without `--fresh` before the first capture, so the field shows the
   default hint instead of the feedback shown after an add.
5. Capture the session duration screen first, with the 25-minute preset
   selected. Before capturing, run
   `xcrun simctl status_bar <udid> override --time <HH:MM now> --batteryState discharging --batteryLevel 100 --wifiBars 3`,
   so the status bar matches the "Ends at" time.
6. Capture About Posato and the paused items with the same status-bar time.
   The About screen hides the tab bar, so go Back before switching tabs.
7. Capture each screen with `xcrun simctl io <udid> screenshot raw.png`. Then
   remove the alpha channel with `ffmpeg -i raw.png -pix_fmt rgb24 <out>.png`,
   naming the output after the files above.
8. Clear the override with `xcrun simctl status_bar <udid> clear`.

`posato-provisioning store prepare --screenshots docs/store/en-US/screenshots`
uploads both sets in file-name order; see the
[iOS App Store release](../../development/apple-provisioning.md#ios-app-store-release).

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
