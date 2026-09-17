# PROTOTYPE-APP-001: Run the interaction prototype as native Compose apps

- **Review tier:** Standard
- **Tier reason:** Isolated mock state, shared UI, and two new native hosts.
- **Dependencies:** Committed prototype design system at `566bdb6`.
- **Integration group:** Prototype migration on `design/mvp-interaction-polish`.
- **Authority:** Maintainer-approved native prototype implementation plan.

## Outcome

Replace the HTML workbench with a Compose Multiplatform prototype using the
existing design system, running as an iPhone app and a resizable macOS app.

## Boundaries

- A separate `:prototypeApp` depends on `:prototypeDesignSystem`, not production modules.
- Keep all data synthetic and in memory; installations do not communicate.
- Keep the fixed 17:45 clock and manually triggered external outcomes.
- Use native hosts without simulated device frames or replacement window chrome.
- Preserve `DESIGN.md` as production authority; prototype geometry is evidence only.

## Acceptance

- `AC-01` — All 16 surfaces, strict transitions, forms, and four walkthroughs remain usable.
- `AC-02` — Hidden, accessible controls expose moments, walkthroughs, Free play, state, and appearance.
- `AC-03` — iPhone safe areas and desktop resizing preserve usable content and editor drafts.
- `AC-04` — macOS packaged launch and iOS Simulator launch demonstrate mocked native flows.
- `AC-05` — Equivalent common regression coverage passes before HTML and Node tests are removed.
- `AC-06` — Custom duration uses keyboard-free wheels with step arrows; website
  entry stays inline for repeated and batch additions, with secondary search and
  recoverable validation feedback. These remain mock prototype interactions.

## Verification

- Common model tests, JVM and both iOS target builds, focused and aggregate quality gates.
- Existing native AX bridge and iOS driver with prototype bundle override; ignored runtime evidence.
- Independent completed-change review; update run instructions and prototype provenance.
