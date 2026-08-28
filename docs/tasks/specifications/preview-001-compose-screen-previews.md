# `PREVIEW-001`: Add deterministic shared screen previews

- **Review tier:** `standard`
- **Tier reason:** This adds build tooling, a shared platform source set, and a
  reusable UI-delivery convention without changing product behavior.
- **Dependencies:** `TARGETS-001`
- **Integration group:** `PR-DOMAINS`
- **Authority:** Maintainer request on 2026-08-27

## Outcome

The exact-domain screen has deterministic named common-code Compose previews,
and later product screens have a small, review-enforced preview convention.

## Boundaries

- Add only the Android KMP library target required by common preview tooling.
- Render previews through the existing state-and-callback screen overload with
  synthetic states and no ViewModel, store, or platform I/O.
- Do not add an Android application, UI test framework, screenshot test, or
  adaptive-layout contract.

## Acceptance

- `AC-01` — The IDE renders the same complete named exact-domain state matrix
  for phone and desktop through one `PreviewParameterProvider` stored separately
  from the two preview functions in the screen file.
- `AC-02` — The Android preview target and all existing JVM and iOS quality
  surfaces compile without adding an Android product host or runtime claim.
- `AC-03` — The quality contract, architecture baseline, development guidance,
  and wiki distinguish tooling from Android product support.

## Verification

- Run focused Android compilation, the aggregate quality gate, and the
  credential-free iOS Simulator host build.
- Inspect the preview matrix after Android Studio Gradle sync.

## Decisions or blockers

- Use AGP 9.0.1, Android SDK Platform 36, and Build Tools 36.0.0 only for
  preview tooling. Android product targets remain a future decision.
