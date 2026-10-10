# Posato showcase storyboard

- **Revision:** 5 (2026-10-10, `DOCS-005`: the `DESIGN-004` interface and longer quick choices in Posato 1.4; scenes and timing unchanged)
- **Format:** `Hero` 24 seconds and `Walkthrough` 52.6 seconds, 1600 x 1000, 30 fps, silent. The hero loops seamlessly as the README GIF and the posato.app hero video; the walkthrough is the GitHub attachment.
- **Audience:** someone meeting Posato on GitHub or posato.app who has ten seconds to decide whether it is for them.
- **Executable projection:** `src/storyboard.ts`. Copy, frame numbers, targets, and captures live there; `src/storyboard.test.ts` enforces this document's rules.

## Purpose

Show, without sound and at 960 pixels wide, that Posato is a pause you set up yourself: keep exact websites and per-device apps in a pause set, pick a duration, start, and see restrictions active on a Mac and an iPhone; or plan pauses ahead with a schedule that starts on its own. Every step is a visible click or tap on a real capture; nothing is implied by a caption alone. The hero is the short version: choose, start, restrictions active, then optional sync and private iCloud storage.

## Rules

The first rule covers every output. The rest govern the walkthrough and the stills; the hero section below states how the hero differs.

- Real captures with synthetic choices only: two pause sets with neutral names (`Focus`, the default, and `Evening`), `example.com`, `example.net`, the built-in Chess application on the Mac, one unnamed built-in app on the iPhone, and two schedules with neutral names (`Deep work`, `Evening reading`). No account, notification, real domain, device name, or home path may appear.
- Mac and iPhone captures sit in the same generic bezels as posato.app: a graphite display bezel on a moss-to-sage wallpaper for the Mac, a rounded graphite phone bezel for the iPhone, each with a one-pixel outline. No Apple artwork, base, or shadow.
- Actions are shown by a springy arrow cursor with a click ring on the Mac and a fingertip dot with one ring on the iPhone. Captures crossfade over eight frames at the click; native pickers and the time wheels are elided. Since 1.2, a set-up Mac starts a pause without an administrator prompt, so none is elided.
- A callout pill names each action while it happens. No stretch longer than 90 frames passes without a visible change.
- Zoom is gated to actions (at most 1.06x) and scenes overlap by six frames. Title cards use the product's dark tokens and the system font stack; no web fonts, music, stock footage, or particles.
- The walkthrough's closing card fades fully to the canvas. The hero instead wipes back to its exact opening composition, so its loop has no cut.

## Hero (720 frames)

The hero follows the walkthrough's real Mac captures through large editorial
crops, a deliberate Start click, and a long quiet active-state hold, then the
sync and privacy scenes. It does not use the walkthrough's device bezels,
callout pills, or title cards.

| Frames | Content |
| --- | --- |
| 0–72 | Tight crop of the saved websites, "Choose what to pause." |
| 72–114 | Brief duration selection under "Start your pause."; time remains only in the UI |
| 114–168 | Review and Start; the heading holds steady, click at frame 156 |
| 168–300 | Restrictions active, end time and early-end action; shared crop settles |
| 300–450 | Mac and iPhone website captures; "Your devices. Connected." |
| 450–630 | "Your iCloud. Your data."; on-device encryption and private iCloud storage |
| 630–720 | "A little space. For what matters." and the open-interval mark |

The hero uses the existing active-session capture in place of the
proposed browser-blocking shot. It demonstrates the reported application state,
not a newly recorded enforcement attempt. The paired website captures illustrate shared choices, not measured delivery
latency or simultaneous activation. Sync is optional and best effort; device-local
app selections are not presented as shared. Privacy copy follows `PRIVACY.md`.
The site poster uses frame 240 (`POSTER_FRAME`).
Adjacent captures share an animated crop with 20-frame eased masked transitions.
Headings preserve their line breaks and fade out before the next text enters.
The closing composition enters over 28 frames, and the final 24 frames use a matching mask to return
to the exact opening composition so the loop boundary has no cut.
The first crop excludes the empty input and feedback from the earlier add action.

The duration and review form one action under a continuous heading. The selected
time is an example in the application, not a marketing claim. The active state
arrives at 5.6 seconds and holds for 4.4 seconds before the closing line.

Every capture has a 36-pixel outer inset, including during masked transitions.
The opening pair sits lower in the canvas to balance the space below it.
The action scenes leave 70 pixels between the headline line box and capture;
review retains space above its introductory line and below the final item row.

## Walkthrough (1578 frames)

| # | Scene | Frames | Layout | Intent |
| --- | --- | --- | --- | --- |
| 1 | `open` | 0-90 | title | The promise plus the three steps. |
| 2 | `websites` | 84-354 | Mac | From Session to Pause sets; open Focus; add a domain. |
| 3 | `apps-mac` | 348-528 | Mac | Apps tab, Choose apps, Chess appears. |
| 4 | `apps-iphone` | 522-672 | iPhone leads | The same choice on the iPhone. |
| 5 | `duration` | 666-876 | Mac | Start a session, pick 45 minutes, review. |
| 6 | `start` | 870-1020 | Mac, then Mac and iPhone | Start; restrictions active on both. |
| 7 | `end-early` | 1014-1194 | Mac and iPhone | End early through Ready to return?; choices stay. |
| 8 | `schedule` | 1188-1368 | Mac | Schedules, Add schedule, save a weekday plan. |
| 9 | `schedule-runs` | 1362-1512 | Mac and iPhone | A scheduled pause runs on its own; the iPhone lists the same kind of plan. |
| 10 | `close` | 1506-1578 | title | Land the line and fade to the canvas. |

## Captures

Names are the files in `public/`; provenance is in `README.md`.

| Capture | State |
| --- | --- |
| `mac-session-inactive` | Session with no active session, the two websites and one application counted; also the state after ending early |
| `mac-pause-sets` | Pause sets: Focus (default) and Evening, each with its schedule |
| `mac-websites-empty` | Focus, Websites, only example.com, empty field |
| `mac-websites-typed` | example.net typed, Add enabled |
| `mac-websites-added` | both domains saved, with the added-count hint after Add |
| `mac-websites` | both domains saved, field at rest (stills and the site) |
| `mac-apps-empty` | Apps tab, Make room beyond the browser. |
| `mac-apps` | Chess chosen |
| `mac-duration` | setup with the default set Focus and the 25-minute default |
| `mac-duration-45` | 45 minutes selected |
| `mac-review-45` | One last look with the end time and Set: Focus |
| `mac-active-45` | Session active, Restrictions active., Set: Focus |
| `mac-end-confirm` | Ready to return? |
| `mac-schedules-empty` | Schedules with no plan yet |
| `mac-schedule-editor` | a new schedule named Deep work with the set Focus, weekdays 09:00 to 11:00 |
| `mac-schedules-one` | Deep work saved, alone, with its next run (the video's Save result) |
| `mac-schedules` | Deep work (Set: Focus) and Evening reading (Set: Evening) saved, each with its next run (the still and the site) |
| `mac-scheduled-active` | Session during Evening reading: a scheduled pause is running with Set: Evening |
| `iphone-websites` | Focus, Websites, both domains |
| `iphone-pause-sets` | Pause sets: Focus (default) and Evening |
| `iphone-apps-empty` | Apps tab, nothing chosen |
| `iphone-apps` | one application chosen |
| `iphone-duration-45` | setup with the set Focus and 45 minutes selected |
| `iphone-active-45` | Session active |
| `iphone-schedules` | Schedules with Deep work saved |

## Updating

1. Bump the revision here and mirror every change in `src/storyboard.ts`.
2. Refresh only the captures whose state changed, through `capture/`.
3. Calibrate targets in Remotion Studio, then `npm run media`.
4. Review a contact sheet, the GIF's first and last frames, and the site hero.
