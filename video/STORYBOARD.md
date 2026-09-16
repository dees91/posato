# Posato showcase storyboard

- **Revision:** 1 (2026-09-16)
- **Format:** `Hero` 22 seconds and `Walkthrough` 42 seconds, 1600 x 1000, 30 fps, silent. The hero loops seamlessly as the README GIF and the posato.app hero video; the walkthrough is the GitHub attachment.
- **Audience:** someone meeting Posato on GitHub or posato.app who has ten seconds to decide whether it is for them.
- **Executable projection:** `src/storyboard.ts`. Copy, frame numbers, targets, and captures live there; `src/storyboard.test.ts` enforces this document's rules.

## Purpose

Show, without sound and at 960 pixels wide, that Posato is a timed pause you set up yourself: choose exact websites, choose apps per device, pick a duration, start, and see restrictions active on a Mac and an iPhone. Every step is a visible click or tap on a real capture; nothing is implied by a caption alone.

## Rules

- Real captures with synthetic choices only: `example.com`, `example.net`, the built-in Chess application on the Mac, and one unnamed built-in app on the iPhone. No account, notification, real domain, device name, or home path may appear.
- Mac and iPhone captures sit in the same generic bezels as posato.app: a graphite display bezel on a moss-to-sage wallpaper for the Mac, a rounded graphite phone bezel for the iPhone, each with a one-pixel outline. No Apple artwork, base, or shadow.
- Actions are shown by a springy arrow cursor with a click ring on the Mac and a fingertip dot with one ring on the iPhone. Captures crossfade over eight frames at the click; native pickers and the administrator prompt are elided.
- A callout pill names each action while it happens. No stretch longer than 90 frames passes without a visible change.
- Zoom is gated to actions (at most 1.06x) and scenes overlap by six frames. Title cards use the product's dark tokens and the system font stack; no web fonts, music, stock footage, or particles.
- The closing card fades fully to the canvas so the loop has no flash.

## Hero (660 frames)

| # | Scene | Frames | Layout | Intent |
| --- | --- | --- | --- | --- |
| 1 | `open` | 0-66 | title | State the promise: Pause. Then choose. |
| 2 | `websites` | 60-246 | Mac | Show a domain being added: click the field, click Add. |
| 3 | `apps-iphone` | 240-390 | iPhone leads, Mac beside | Show apps chosen on the iPhone; the Mac already shows Chess. |
| 4 | `duration` | 384-516 | Mac | Pick 45 minutes, open the review. |
| 5 | `start` | 510-606 | Mac, then Mac and iPhone | Start the pause; both devices show the active session. |
| 6 | `close` | 600-660 | title | Land the line and fade to the canvas. |

## Walkthrough (1260 frames)

| # | Scene | Frames | Layout | Intent |
| --- | --- | --- | --- | --- |
| 1 | `open` | 0-90 | title | The promise plus the three steps. |
| 2 | `websites` | 84-354 | Mac | From Session to Paused items; add a domain. |
| 3 | `apps-mac` | 348-528 | Mac | Apps tab, Choose apps, Chess appears. |
| 4 | `apps-iphone` | 522-672 | iPhone leads | The same choice on the iPhone. |
| 5 | `duration` | 666-876 | Mac | Start a session, pick 45 minutes, review. |
| 6 | `start` | 870-1020 | Mac, then Mac and iPhone | Start; restrictions active on both. |
| 7 | `end-early` | 1014-1194 | Mac and iPhone | End early through Ready to return?; choices stay. |
| 8 | `close` | 1188-1260 | title | Land the line and fade to the canvas. |

## Captures

Names are the files in `public/`; provenance is in `README.md`.

| Capture | State |
| --- | --- |
| `mac-session-inactive` | Session with no active session, the two websites and one application counted; also the state after ending early |
| `mac-websites-empty` | Paused items, Websites, only example.com, empty field |
| `mac-websites-typed` | example.net typed, Add enabled |
| `mac-websites-added` | both domains saved, with the added-count hint after Add |
| `mac-websites` | both domains saved, field at rest (stills and the site) |
| `mac-apps-empty` | Apps tab, Make room beyond the browser. |
| `mac-apps` | Chess chosen |
| `mac-duration` | setup with the 25-minute default |
| `mac-duration-45` | 45 minutes selected |
| `mac-review-45` | One last look with the end time |
| `mac-active-45` | Session active, Restrictions active. |
| `mac-end-confirm` | Ready to return? |
| `iphone-websites` | Paused items, Websites, both domains |
| `iphone-apps-empty` | Apps tab, nothing chosen |
| `iphone-apps` | one application chosen |
| `iphone-duration-45` | setup with 45 minutes selected |
| `iphone-active-45` | Session active |

## Updating

1. Bump the revision here and mirror every change in `src/storyboard.ts`.
2. Refresh only the captures whose state changed, through `capture/`.
3. Calibrate targets in Remotion Studio, then `npm run media`.
4. Review a contact sheet, the GIF's first and last frames, and the site hero.
