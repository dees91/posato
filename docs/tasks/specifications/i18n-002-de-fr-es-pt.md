# `I18N-002`: Ship Posato in German, French, Spanish, and Portuguese

- **Review tier:** `standard`
- **Tier reason:** The change adds four languages across the shared UI, the platform shells, the site, and the store listings, so one independent completed-change review is warranted. It adds no trust boundary, credential, or protocol field.
- **Dependencies:** `I18N-001` and its retro (merged). `DOCS-007` and `RELEASE-010` depend on this row.
- **Integration group:** `PR-I18N-DE-FR-ES-PT`, roadmap revision 37, wave R1.6/W1.
- **Authority:** [Release roadmap](../release-roadmap.md) revision 37, idea 60 in [open questions](../../wiki/topics/mvp-open-questions.md) (`user-confirmed` 2026-10-28), [localization guide](../../development/localization.md), [DESIGN.md](../../../DESIGN.md).
- **Record:** [i18n-002-de-fr-es-pt.md](../executions/i18n-002-de-fr-es-pt.md)

## Outcome

With German, French, Spanish, or Portuguese as the system or per-app language, Posato's interface on every platform, the macOS pause page, and the permission descriptions are in that language. `posato.app` serves `/de/`, `/fr/`, `/es/`, and `/pt/`, and `docs/store` holds prepared `de-DE`, `fr-FR`, `es-ES`, and `pt-BR` listings with screenshots. English and Polish stay unchanged.

## Boundaries

- The steps of the localization guide, in one pull request; all four languages move into `PosatoLocales.SHIPPED`.
- App resources use the bare language code (`values-pt`, `pt.lproj`); the store listing names the variant.
- The agent drafts the copy. One independent agent reviews the catalogs, site, and listings and translates the key screens of each language back into English; the maintainer accepts the copy from that report in the pull request.
- No English or Polish wording change, no in-app switch, no translation of docs, the README, license texts, or Sparkle dialogs.
- Nothing is uploaded and no release is made.

## Acceptance

- `AC-01` — Each language as the app's language shows that language in Session, pause sets, and Schedules on the Mac in a Tart guest, on the test iPhone, and on Android, with correct plurals; the macOS pause page follows it.
- `AC-02` — `verifyEnglishText`, `verifyCatalogs`, and `verifyLocaleLists` pass with five shipped languages.
- `AC-03` — Each new site directory serves every English page with `hreflang` and the language link; English and Polish URLs are unchanged.
- `AC-04` — Each store directory holds the listing, description, current What's New, `capture.json`, and iPhone and iPad screenshots from `store capture`. Nothing is uploaded.
- `AC-05` — German and French labels are not clipped at the Mac minimum window width or on a small iPhone Simulator.

## Verification

- The three Gradle checks, then the guide's language proof per target for each language: Mac in Tart, test iPhone, Android.
- `store capture` on both capture-recipe Simulators per language, and on iPhone 16e for the long-text pass.
- The site build, with each new privacy page at a narrow and a wide viewport.

## Decisions or blockers

- None. Each language keeps its glossary inside its resources; corrections happen in the pull request.
