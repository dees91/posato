# App Store listing — French (France)

Status: draft for I18N-002. Nothing in this directory has been uploaded.
`store prepare` can write it later with `--locale fr-FR`, App Store Connect's
locale code for French (France). The default remains `en-US`, and this task
does not call App Store Connect.

An independent agent reviews the French wording and translates the key
screens back into English; the maintainer accepts the copy from that report
in the pull request. This directory translates the current English listing,
which is the 1.5 description and What's New.

## Name

Posato

## Subtitle

De l’espace pour l’essentiel

28 characters. The product line "Un peu d’espace. Pour l’essentiel." is 34
characters, over the subtitle's 30. This shorter form follows the English
decision to use "Space for what matters."

## Description

[description.txt](description.txt) is the text for
`store prepare --locale fr-FR --description`. It follows the English
description and does not name Android, because App Review guideline 2.3.10
does not allow other mobile platforms in the metadata.

## Keywords

Draft, 85 characters, not uploaded. `store prepare` does not set keywords.

concentration,pause,distractions,sites web,apps,temps d’écran,focus,déconnexion,calme

## Screenshots

The set repeats the English screens from the French interface on the same
Simulators, in Dark Mode, with the synthetic websites `news.example`,
`social.example`, and `video.example` in the first set "Concentration", and
one weekday schedule "Travail de fond":

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
`posato-control store capture -t sim --udid <udid> --locale fr-FR --out docs/store/fr-FR/screenshots/iphone-6.9 --prepare`,
and `ipad-13` on the iPad, on the same Simulators as the English set. Nothing
is uploaded.

## What's New in 1.5.0

[whats-new-1.5.0.txt](whats-new-1.5.0.txt) translates the current English
What's New. It names only what changed on iPhone and iPad.
