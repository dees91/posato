# Showcase media instructions

This folder renders Posato's README demo, the walkthrough, the composed README
stills, the repository social preview, and the posato.app hero video.

Source precedence: the user's request, then `STORYBOARD.md`, then
`src/storyboard.ts`, then the scene and component code. Change copy, timing,
targets, and captures in `src/storyboard.ts` and mirror the human-readable
contract in `STORYBOARD.md`; scenes only read from the storyboard.

Output contract: `npm run media` must reproduce every output from tracked
sources on a clean install and `npm run verify` must pass. The GIF ladder may
lower frame rate, palette, or size, never shorten the story, and the tracked
GIF stays at or below 10 MiB, the site MP4 at or below 3 MiB, composed stills
at or below 1 MiB each.

Captures come only from the real applications through `capture/` with the
synthetic fixture; the repository root `AGENTS.md` privacy rules apply to
every tracked PNG.
