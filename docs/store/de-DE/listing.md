# App Store listing — German (Germany)

Status: draft for I18N-002. Nothing in this directory has been uploaded.
`store prepare` can write it later with `--locale de-DE`, App Store
Connect's locale for German. The default remains `en-US`, and this task does
not call App Store Connect.

An independent agent reviews the German wording and translates the key
screens back into English; the maintainer accepts the copy from that report
in the pull request. This directory translates the current English listing,
which is the 1.5 description and What's New.

## Name

Posato

## Subtitle

Raum für das, was zählt.

24 characters. The product line "Ein wenig Raum. Für das, was zählt." is 35
characters, over the subtitle's 30. This shorter form follows the English
decision to use "Space for what matters."

## Description

[description.txt](description.txt) is the text for
`store prepare --locale de-DE --description`. It follows the English
description and does not name Android, because App Review guideline 2.3.10
does not allow other mobile platforms in the metadata.

## Keywords

Draft, 89 characters, not uploaded. `store prepare` does not set keywords.

Fokus,Pause,Ablenkung,Websites,Apps,Bildschirmzeit,Konzentration,Auszeit,Achtsamkeit,Ruhe

## Screenshots

The set repeats the English screens from the German interface on the same
Simulators, in Dark Mode, with the synthetic websites `news.example`,
`social.example`, and `video.example` in the first set, renamed "Fokus" from
[capture.json](capture.json), and one weekday schedule
"Konzentriert arbeiten":

- [Paused websites](screenshots/iphone-6.9/01-paused-websites.png)
- [Session duration](screenshots/iphone-6.9/02-session-duration.png)
- [Schedules](screenshots/iphone-6.9/03-schedules.png)
- [About Posato](screenshots/iphone-6.9/04-about-posato.png)

The iPad set shows the same screens in portrait:

- [Paused websites](screenshots/ipad-13/01-paused-websites.png)
- [Session duration](screenshots/ipad-13/02-session-duration.png)
- [Schedules](screenshots/ipad-13/03-schedules.png)
- [About Posato](screenshots/ipad-13/04-about-posato.png)

Capture them with the
[capture command](../en-US/listing.md#capture-recipe), which reads
[capture.json](capture.json):
`posato-control store capture -t sim --udid <udid> --locale de-DE --out docs/store/de-DE/screenshots/iphone-6.9 --prepare`,
and `ipad-13` on the iPad. The iPhone is the iPhone 17 Pro Max
Simulator with iOS 26.5 (1320 × 2868), the iPad the iPad Pro 13-inch (M5)
Simulator with iOS 27.0 (2064 × 2752); both are RGB PNGs without an alpha
channel. Nothing is uploaded.

## What's New in 1.5.0

[whats-new-1.5.0.txt](whats-new-1.5.0.txt) translates the current English
What's New. It names only what changed on iPhone and iPad.
