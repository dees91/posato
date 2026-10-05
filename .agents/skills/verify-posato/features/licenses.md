# About Posato and licenses

About Posato shows short app information and the installed version. Its Licenses
disclosure shows the license, notice, and third-party notices shipped with Posato,
available offline. Each document has its full selectable text and a return path.

## Sub-features

- `about-version` opens About Posato and shows the installed application version.
- `licenses-documents` opens every document and reaches its final paragraph.
- `licenses-return` returns to the preceding Session or Pause sets destination.

## How to get to it (user POV)

- After setup, tap About Posato beside the iPhone wordmark or below On this Mac in
  the Mac sidebar. Dismiss the software keyboard with Done first.
- Choose Licenses, then License, Notice, or Third-party notices. Scroll to read
  the full text.
- Back to licenses returns to the document list; Back to About Posato returns
  to the information screen; Back returns to the preceding primary destination. The Mac sidebar can also open either primary destination.

## Driving it with posato-control

Preconditions:

- Launch the current application through the CLI, complete first-install skip
  if needed, and pass doctor. These checks need no iCloud or Screen Time consent.
- Start with no active session. The recipe changes no database state and makes
  no application selection.

- **Documents and return:** `$PC run -t desktop --vm primary --scenario tools/posato-control/fixtures/scenarios/licenses.json`;
  on iOS `$PC run -t <sim|device> --scenario tools/posato-control/fixtures/scenarios/licenses-ios.json`,
  which uses the bar's back labels, scrolls the screen rather than the
  `Document text` group (its text is not inside that group on iOS), and ends
  at Session, because an iPhone reaches About only from Session.
  The recipe enters About Posato from Session, reads the version, opens and
  scrolls every document, captures
  the content and accessibility tree, returns to Session, then proves the
  Pause sets return path.
- **Installed version:** compare the About snapshot with `CFBundleShortVersionString`
  in the iOS app bundle or `jpackage.app-version` in the staged Mac launcher.
  An unpackaged development run may say Version unavailable.
- **Text fidelity:** compare the document paragraphs in the captured snapshots
  with the root source files, accounting for rendered Markdown syntax and soft
  line breaks; generated and bundled resources must also match
  the root files byte-for-byte. Do not replace full-document proof with a title
  assertion alone.

## Gotchas

- License and Notice retain plain text. Third-party notices render Markdown
  headings, emphasis, lists, and a responsive component table. Its Apache License
  link appears as Read Apache License, Version 2.0 beneath the paragraph and opens
  the bundled License document.
  They do not fetch a website or launch another application.
- Document bodies scroll in the Document text region, independently of the persistent
  Back to licenses action. The iPhone bottom navigation is hidden in this secondary screen.
- On the test iPhone, `licenses.json` fails now and then at a random scroll
  step ("Failed to get matching snapshot"). Rerun it once before calling a
  failure a regression; a failure at the same step twice is real.
- App icon installation is owned by the platform wiring tasks; the licenses
  recipe does not establish Dock, Finder, or Home Screen icon appearance.
