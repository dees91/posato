# App Store listing — Spanish (Spain)

Status: draft for I18N-002. Nothing in this directory has been uploaded.
`store prepare` can write it later with `--locale es-ES`, App Store Connect's
code for Spanish (Spain). The default remains `en-US`, and this task does not
call App Store Connect.

An independent agent reviews the Spanish wording and translates the key
screens back into English; the maintainer accepts the copy from that report
in the pull request. This directory translates the current English listing,
which is the 1.5 description and What's New.

## Name

Posato

## Subtitle

Espacio para lo que importa.

28 characters. The product line "Un poco de espacio. Para lo que importa." is
40 characters, over the subtitle's 30. This shorter form follows the English
decision to use "Space for what matters."

## Description

[description.txt](description.txt) is the text for
`store prepare --locale es-ES --description`. It follows the English
description and does not name Android, because App Review guideline 2.3.10
does not allow other mobile platforms in the metadata.

## Keywords

Draft, 95 characters, not uploaded. `store prepare` does not set keywords.

concentración,pausa,distracciones,sitios web,apps,tiempo de uso,enfoque,descanso,bloquear,calma

## Screenshots

The set repeats the English screens from the Spanish interface on the same
Simulators, in Dark Mode, with the synthetic websites `news.example`,
`social.example`, and `video.example` in the unnamed first set, which the
Spanish app names "Mi conjunto", and one weekday schedule "Trabajo profundo":

- [Paused websites](screenshots/iphone-6.9/01-paused-websites.png)
- [Session duration](screenshots/iphone-6.9/02-session-duration.png)
- [Schedules](screenshots/iphone-6.9/03-schedules.png)
- [About Posato](screenshots/iphone-6.9/04-about-posato.png)

The iPad set shows the same screens in portrait:

- [Paused websites](screenshots/ipad-13/01-paused-websites.png)
- [Session duration](screenshots/ipad-13/02-session-duration.png)
- [Schedules](screenshots/ipad-13/03-schedules.png)
- [About Posato](screenshots/ipad-13/04-about-posato.png)

Capture them with the [capture command](../en-US/listing.md#capture-recipe),
which reads [capture.json](capture.json):
`posato-control store capture -t sim --udid <udid> --locale es-ES --out docs/store/es-ES/screenshots/iphone-6.9 --prepare`,
and `ipad-13` on the iPad. `capture.json` sets no `setName`: renaming the set
presses the native iOS alert's Save button by its English title, so the
first set keeps the name the Spanish app gives it. The iPhone is the iPhone
17 Pro Max Simulator with iOS 26.5 (1320 × 2868), the iPad the iPad Pro
13-inch (M5) Simulator with iOS 27.0 (2064 × 2752); both are RGB PNGs
without an alpha channel. Nothing is uploaded.

## What's New in 1.5.0

[whats-new-1.5.0.txt](whats-new-1.5.0.txt) translates the current English
What's New. It names only what changed on iPhone and iPad.
