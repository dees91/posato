# `DESIGN-002`: Give Posato its application icons, store assets, and licenses screen

- **Review tier:** `standard`
- **Tier reason:** Brand assets, screenshots, and read-only shared information screens change no security, privacy, or synchronization boundary; an independent check still covers design fidelity and the new navigation entry.
- **Dependencies:** completed `RELEASE-001` (merged as `f96c0c6`). `DOCS-001` depends on this row.
- **Integration group:** `PR-STORE-ASSETS`, roadmap wave Release/R2, in parallel with `MACOS-008` and `IOS-003`.
- **Authority:** [MVP roadmap](../mvp-roadmap.md), [DESIGN.md](../../../DESIGN.md) (identity and iconography, components), [product identity](../../product/product-identity.md), [LICENSE](../../../LICENSE), [NOTICE](../../../NOTICE), [third-party notices](../../../THIRD_PARTY_NOTICES.md).

## Outcome

Posato has an application icon on iOS and macOS derived from its open-interval mark, App Store screenshots and listing copy the maintainer has accepted, and an About Posato entry with short app information and the installed version, leading to a licenses screen on both platforms that shows the license, notice, and third-party notices shipped with the app.

## Boundaries

- Present at least two icon proposals derived from the mark and the semantic palette; the maintainer chooses before the final set is produced. The icon works on iOS 18 to 26 (including dark and tinted appearances) and macOS 15 to 26.
- Fixed hand-off paths, frozen for this wave: an `AppIcon` set in `iosApp/iosApp/Assets.xcassets` and `desktopApp/Config/Posato.icns`. Do not edit `iosApp.xcodeproj` or `desktopApp/build.gradle.kts`; `IOS-003` and `MACOS-008` wire the icons.
- About Posato shows the installed version from platform metadata. The licenses screen reads the repository's `LICENSE`, `NOTICE`, and `THIRD_PARTY_NOTICES.md` as shared resources produced from those files at build time, never from a second hand-maintained copy. Amend DESIGN.md with its entry point and layout within the existing components and voice.
- Screenshots come from the real app with synthetic data only; no personal websites, apps, device names, or account details. Listing copy follows the DESIGN.md voice and the README limits and makes no claim beyond verified behavior.
- Roadmap revision 16 adds the licenses screen to this row's outcome (maintainer decision, 2026-09-14).
- Non-goals: the showcase README and demo video (`DOCS-001`), App Store Connect metadata upload (`IOS-003`, `RELEASE-002`), marketing website, new product features.

## Acceptance

- `AC-01` — The maintainer has chosen an icon; DESIGN.md records it, and the source artwork is tracked with its export.
- `AC-02` — The icon appears on the iPhone home screen and in the macOS Dock and Finder from the fixed paths once wired.
- `AC-03` — On both platforms About Posato shows the installed version and opens the licenses screen, which shows the three texts in full, scrollable, with accessible labels and a return path.
- `AC-04` — App Store screenshots in the currently required iPhone sizes and a listing draft (name, subtitle, description, keywords) are tracked and accepted by the maintainer.

## Verification

- Drive both apps through [verify-posato](../../../.agents/skills/verify-posato/SKILL.md) to reach the licenses screen and capture the screenshots; evidence stays under `build/verification/`.
- `./gradlew quality`; independent completed-change review against DESIGN.md.
- Check the required screenshot sizes against current App Store Connect documentation and date the check.

## Decisions or blockers

- **Decided (2026-09-14):** the agent designs the icon from DESIGN.md; notices ship as files with an in-app screen.
- **Decided (2026-09-14):** proposal B — Forest; About Posato beside the iPhone wordmark and below On this Mac in the sidebar, with short information, installed version, and a Licenses disclosure leading to the three documents and a return path.
