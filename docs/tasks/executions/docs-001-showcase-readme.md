# Execution: `DOCS-001`

- **Brief:** [Showcase README](../specifications/docs-001-showcase-readme.md)
- **Status:** `blocked`
- **Review tier:** `standard`
- **Implementer:** Codex
- **Reviewer:** Independent Codex completed-change review
- **Branch:** `feature/docs-001-showcase-readme`
- **Updated:** 2026-09-16

## Plan

1. Preserve README claims, limits, privacy, and source-build routes; apply the accepted identity and reference README structure with Clarity and Show-me.
2. Capture synthetic real-app flows through posato-control and assemble a pinned Remotion project under video/.
3. Render and inspect the GIF, walkthrough, and screenshots; check clean-install reproduction, media size, and links.
4. Obtain independent completed-change review, then hand the finished walkthrough and README to the maintainer for upload and acceptance.

## Result

- README leads with the identity, a value sentence, and an animated demo, followed by source-build quick start, illustrated user steps, and documentation routes. Original limits, platform, privacy, support, documentation, and license sections are retained verbatim.
- The Remotion compositions use curated real-app images and a real Mac early-end recording. Hero is 24 seconds; Walkthrough is 47 seconds. The media README documents pinned dependencies, rendering, provenance, and size budgets.
- The Mac and physical iPhone each reached **Restrictions active.** after the maintainer granted the relevant system permission. These were separate local sessions, not a demonstration of synchronization. The full duration controls use the accepted larger Simulator image; other iPhone images use the physical device.
- Both sessions were ended. Existing Mac choices were preserved (two domains and one application). The newly installed iPhone app's two synthetic domains and single selected application were cleared; relaunch confirmed no session, zero websites, and zero selected apps. The completed onboarding, granted permission, and empty application-group metadata remain on the iPhone.
- Raw captures and device identifiers remain under ignored build/verification/. Only reviewed, reduced artwork exports are tracked, as authorized by the brief.

## Dependency review

- `user-confirmed` (2026-09-16): the maintainer develops Posato as an individual. Remotion 4.0.525's installed [license](https://github.com/remotion-dev/remotion/blob/v4.0.525/LICENSE.md) permits individual video/image creation, including commercial use.
- Reviewed the official Remotion 4.0.525 release notes and package licenses; rendering and Studio are the consumers. All Remotion packages share the exact version; React and TypeScript follow the compatible blank scaffold. These tools stay outside the application builds.
- Removed unused Tailwind packages. Updated scaffold ESLint 9.19.0 to 9.39.5 for the plugin-kit advisory. The clean npm install reports zero known vulnerabilities; this is a dated check, not a security guarantee.

## Verification

- Signed desktop and device builds succeeded; posato-control drove the real setup, selection, duration, start, early-end, and cleanup paths. iPhone Screen Time consent and native app selection were maintainer-attended.
- The device initially appeared paired with a disconnected CoreDevice tunnel. A read-only devicectl device-details query established the tunnel before driver commands. No driver or application source changed.
- One capture recipe looked for Done during first-website onboarding, which has Continue instead. Resuming at the observed Continue completed setup without reinserting data.
- Local lint and TypeScript checks pass. The first clean render exposed a Video objectFit lint warning; the property was moved to the supported component prop, and lint now rejects warnings. A fresh npm ci in an isolated ignored directory, followed by lint, both renders, and GIF export, passes after the correction (Node 26.7.0, FFmpeg 8.1.2).
- Final GIF: 960 × 600, 288 frames, 24 seconds, 1,167,154 bytes. Walkthrough: H.264, 1600 × 1000, 30 fps, 47 seconds, 681,924 bytes. Total tracked media: 2,302,670 bytes; every PNG is below 250 KiB. FFmpeg decoded the complete walkthrough without errors, and its contact sheet was visually inspected.
- Local README/media links resolve. A comparison confirms the original limit and privacy-related sections remain unchanged. GitHub's Markdown API rendered the README, and browser inspection loaded every image; PR rendering remains pending publication of the diff.
- No product source or tooling outside video/ changed, so the brief does not require rerunning the Gradle aggregate gate for this documentation task.

## Completed-change review

Passed with no Critical or Required findings. The reviewer examined README
changes against the original and the product/privacy authorities; all video
sources, configuration, locked dependencies, and task records; all nine PNGs;
and sampled source, GIF, and walkthrough frames. Independent checks passed:
lint/TypeScript, npm audit, local links, preserved claim sections, registry
origins, installed Remotion license, full media decoding, and size budgets.

An optional editorial finding noted that the hero cut ended during the app's
asynchronous restriction cleanup. Hero now skips the source clip's first three
seconds and holds the settled inactive state; Walkthrough is unchanged. Focused
re-review confirmed the final frame, lint, GIF decoding, and the corrected
inbound development-guide anchor. No outstanding review findings. No hosted
Codex review is requested for this documentation/media change.

## Blockers

The maintainer must upload the finished walkthrough as a GitHub attachment and
accept the final README. The attachment URL is deliberately absent until that
upload. This task cannot be marked done before the link and acceptance exist.
