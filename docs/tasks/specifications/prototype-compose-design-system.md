# PROTOTYPE-DS-001: Provide the interaction prototype as reusable Compose components

- **Review tier:** Standard
- **Tier reason:** New reusable component contracts and multiplatform build integration.
- **Dependencies:** Prototype design commit `d7db29e`; existing pinned Compose toolchain.
- **Integration group:** Prototype design worktree; no production-screen migration.
- **Authority:** Explicit maintainer request on 2026-09-05.

## Outcome

The interaction prototype has a complete, reusable Compose Multiplatform
component library and an executable component catalog.

## Boundaries

- Keep the library and catalog under `prototypes/mvp-interaction-flow/compose`.
  Reuse the repository's Gradle toolchain and quality checks in one isolated
  `:prototypeDesignSystem` module with no production application consumers.
- Cover foundations, controls, forms, product patterns, and inspection chrome
  actually present in the HTML prototype. Document their source mapping.
- Components render caller-owned state and callbacks; they do not implement
  blocking, synchronization, storage, permission handling, or the JS reducer.
- Preserve `DESIGN.md` and the production `PosatoTheme`. Exact prototype
  geometry is not promoted into an accepted production design contract.

## Acceptance

- `AC-01` — Every reusable HTML component family maps to a public composable
  or a documented composition of reusable components.
- `AC-02` — Light/dark tokens and compact/expanded layouts are inspectable;
  controls expose selected, disabled, error, and keyboard-accessible states.
- `AC-03` — Components accept root modifiers and caller content where it varies.
- `AC-04` — A runnable desktop catalog and deterministic common-code previews
  demonstrate all families without production services or private data.
- `AC-05` — Applicable compilation and quality checks pass; independent review
  has no unresolved Critical or Required findings.

## Verification

- Compile JVM, preview-only Android, and iOS source sets; run formatting and
  static analysis. Inspect the running desktop catalog and capture local evidence.
- Keep screenshots and runtime output under ignored `build/verification/`.
