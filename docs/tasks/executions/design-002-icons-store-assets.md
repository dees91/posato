# Execution: `DESIGN-002`

- **Brief:** [Icons, store assets, and licenses](../specifications/design-002-icons-store-assets.md)
- **Status:** `done`
- **Review tier:** `standard`
- **Implementer:** Codex
- **Reviewer:** Independent Codex completed-change review
- **Branch:** `feature/design-002-icons-store-assets`
- **Updated:** 2026-09-14

## Plan

1. Present mark-derived icon proposals and agree on the information-screen entry.
2. Generate the legal resources from the root files and implement About Posato and licenses.
3. Export Forest at the frozen platform paths; capture synthetic store screenshots and draft listing copy.
4. Verify the native applications, resolve review and maintainer feedback, and run the aggregate quality gate.

## Result

- The maintainer chose Forest from two vector proposals. Default, dark, and tinted iOS assets and the padded macOS ICNS are exported with editable sources.
- About Posato shows the installed version and opens the three bundled documents. It preserves the preceding primary destination on return. Sidebar text aligns, and buttons retain flat hover states.
- Legal files are copied from the root originals at build time. Third-party notices render native Markdown with a responsive component table, selectable text, and explicit accessible Read actions for local document links.
- The maintainer accepted the three synthetic real-app store captures and English listing. The requested Clarity pass simplified the description without strengthening its product claims or removing limits.
- Runtime verification exposed a transient missing Mac accessibility region while scrolling. The driver now waits for the region within its existing timeout; regression tests cover both recovery and timeout without scrolling elsewhere. The final consolidated retry loop also passes Detekt and an independent correction review.

## Dependency review

`org.jetbrains:markdown:0.7.13` supplies the GFM parser for the repository-owned document, replacing raw Markdown display. Reviewed the maintained JetBrains project's release notes (link-destination parsing fix), published JVM and iOS variants, Apache-2.0 license, and resolved metadata. The common dependency is Kotlin stdlib, already present. The dependency is listed in third-party notices. The OSV Maven query on 2026-09-14 returned no entries; this is a dated check, not a security guarantee. Focused content tests, both native hosts, and the aggregate gate check compatibility.

## Completed-change review

- **Verdict:** Passed; no outstanding Critical or Required findings.
- **Scope:** Shared resource generation, About/version platform implementations, navigation and button changes, Markdown mapping/rendering, driver recovery, icon sources/exports, listing and store captures.
- **Examined:** Shared resource/dependency configuration; complete About/version, licenses, Markdown, navigation, button, and driver diffs; both focused test files; the 61-step recipe and feature guide. Also reviewed DESIGN, brief, roadmap, wiki, listing, artwork sources/exports, and store captures. A final correction review covered the extracted navigation scaffold, information-screen routing, inline link helper, table weight constant, and retry state.
- **Independent checks:** The reviewer ran `:shared:jvmTest --tests app.posato.feature.licenses.LicenseMarkdownTest` and `:posato-control:test --tests app.posato.control.desktop.DesktopScrollerTest` (12 passed). Checked icon geometry/alpha/sizes, generated legal resource bytes, scenario source-text expectations, platform version metadata, and native screenshots/snapshots for all three successful runs.

## Verification

| Check | Result |
| --- | --- |
| `./gradlew quality` | Passed after final corrections; native builds/tests, static analysis, and macOS packaging included |
| Markdown content mapping tests | Six passed: headings, soft lines, every table cell/header, attribution styling, code, local links, and readable fallback |
| DesktopScroller regression suite | Six passed; new recovery/timeout tests first reproduced the failure |
| Native build, install, doctor, and document/navigation scenario | Passed on macOS, iPhone Simulator, and connected iPhone; all 61 steps on each, including Read action, every document end, and both return destinations; final structural cleanup rechecked on macOS and Simulator |
| Resource fidelity | 15 current generated, native-bundle, and packaged-JAR copies matched the root files byte-for-byte |
| iOS `actool` AppIcon compilation | Passed |
| iOS PNG and macOS ICNS inspection | Opaque 1024-square iOS sources; ten macOS size representations with transparent outer padding |
| Store screenshot capture and cleanup | Three 1320 × 2868 RGB PNGs; only synthetic websites; all added rows removed and zero remaining confirmed |
| Listing fields and screenshot requirements | Within field limits; Apple screenshot specification checked 2026-09-14 |
| `git diff --check` | Passed |

Raw runtime evidence remains under ignored `build/verification/`; no device identifiers or raw captures are tracked. The store screenshots are the explicitly authorized release-artwork exception.

## Follow-up and evidence limits

- `AC-01`, `AC-03`, and `AC-04` are satisfied within this PR's scope.
- `AC-02` installed Home Screen, Dock, and Finder appearance remains dependent on `IOS-003` and `MACOS-008`, which own the frozen wiring files. This PR supplies and validates exports; it does not establish installed icon appearance.
- Store upload and distribution readiness remain with the release tasks. Native checks here do not establish the complete supported OS matrix.
