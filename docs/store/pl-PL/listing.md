# App Store listing — Polish (Poland)

Status: draft for I18N-001. Nothing in this directory has been uploaded.
`store prepare` can write it later with `--locale pl`, App Store Connect's
shortcode for Polish. The default remains
`en-US`, and this task does not call App Store Connect.

The maintainer reviews the Polish wording in the pull request. `DOCS-007`
adds only the later 1.6 changes, in both languages. This directory translates
the current English listing, which is the 1.5 description and What's New.

## Name

Posato

## Subtitle

Przestrzeń na to, co ważne.

27 characters. The product line "Trochę przestrzeni. Na to, co ważne." is 36
characters, over the subtitle's 30. This shorter form follows the English
decision to use "Space for what matters."

## Description

[description.txt](description.txt) is the text for
`store prepare --locale pl --description`. It follows the English
description and does not name Android, because App Review guideline 2.3.10
does not allow other mobile platforms in the metadata.

## Keywords

Draft, 91 characters, not uploaded. `store prepare` does not set keywords.

skupienie,pauza,rozproszenia,strony,aplikacje,czas przed ekranem,koncentracja,przerwa,cisza

## Screenshots

The set repeats the English screens from the Polish interface on the same
Simulators, in Dark Mode, with the synthetic websites `news.example`,
`social.example`, and `video.example` in the unnamed first set, which the
Polish app names "Mój zestaw", and one weekday schedule "Głęboka praca":

- [Paused websites](screenshots/iphone-6.9/01-paused-websites.png)
- [Session duration](screenshots/iphone-6.9/02-session-duration.png)
- [Schedules](screenshots/iphone-6.9/03-schedules.png)
- [About Posato](screenshots/iphone-6.9/04-about-posato.png)

The iPad set shows the same screens in portrait:

- [Paused websites](screenshots/ipad-13/01-paused-websites.png)
- [Session duration](screenshots/ipad-13/02-session-duration.png)
- [Schedules](screenshots/ipad-13/03-schedules.png)
- [About Posato](screenshots/ipad-13/04-about-posato.png)

`I18N-001` captured them on 2026-10-28 with the [English recipe](../en-US/listing.md#capture-recipe),
launching the app with `--arg -AppleLanguages --arg "(pl)" --arg -AppleLocale --arg pl_PL`
while the Simulator stays English. The iPhone is the iPhone 17 Pro Max
Simulator with iOS 26.5 (1320 × 2868), the iPad the iPad Pro 13-inch (M5) Simulator with iOS 27.0
(2064 × 2752); both are RGB PNGs without an alpha channel. About shows
version 1.5.2, the build's `MARKETING_VERSION`; `DOCS-007` recaptures the set
for 1.6. Nothing is uploaded.

## What's New in 1.5.0

[whats-new-1.5.0.txt](whats-new-1.5.0.txt) translates the current English
What's New. It names only what changed on iPhone and iPad.
