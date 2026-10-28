# Localization

Posato follows the system language, or the per-app language where the
platform has one, and has no in-app language switch. English is the source.
Polish shipped first, in `I18N-001`; this page is how the next language is
added.

## Shipped and planned languages

[`PosatoLocales`](../../buildSrc/src/main/kotlin/app/posato/buildlogic/PosatoLocales.kt)
in `buildSrc` is the list of shipped languages. Each entry holds:

- the language code that names its directories;
- its App Store listing directory under `docs/store`;
- its CLDR plural categories;
- its digit grouping.

German, French, Spanish, and Portuguese are already defined there, ready to
move into `SHIPPED`.

| Language | Code | Store directory | App Store Connect `--locale` | Plural categories | 65536 written as |
| --- | --- | --- | --- | --- | --- |
| Polish | `pl` | `pl-PL` | `pl` | one, few, many, other | `65 536` (no-break space), `1024` |
| German | `de` | `de-DE` | `de-DE` | one, other | `65.536`, `1.024` |
| French | `fr` | `fr-FR` | `fr-FR` | one, many, other | `65 536` (narrow no-break space) |
| Spanish | `es` | `es-ES` | `es-ES` | one, many, other | `65.536`, `1024` |
| Portuguese | `pt` | `pt-BR` | `pt-BR` | one, many, other | `65.536`, `1.024` |

The maintainer chose the variants `es-ES` and `pt-BR` (`user-confirmed`,
2026-10-28). App resources use the language code alone, such as `values-pt`
and `pt.lproj`, so a speaker of another variant also gets the translation
instead of English (`agent-delegated`, 2026-10-28). The store listing names
the variant.

## Adding a language

Do the steps in one pull request. Run `./gradlew verifyEnglishText
verifyCatalogs verifyLocaleLists` after step 1; until the language is
complete, the checks name each missing file and list entry.

1. **List it.** Move the language into `PosatoLocales.SHIPPED`. The build
   derives the Mac app's `CFBundleLocalizations`, the Mac bundling of the
   helper's translated strings, and the directories `verifyEnglishText`
   accepts from that list.
2. **App catalogs.** Translate each of these files. Then add the language to
   the system lists that cannot read `PosatoLocales`.
   - Files to translate:
     - `shared/src/commonMain/composeResources/values-<code>/strings.xml`;
     - `androidApp/src/main/res/values-<code>/strings.xml`;
     - `macosHelper/Resources/<code>.lproj/Localizable.strings` (the pause page);
     - `macosHelper/Resources/<code>.lproj/InfoPlist.strings`;
     - `iosApp/iosApp/<code>.lproj/InfoPlist.strings`;
     - `windowsHelper/src/main/resources/values-<code>/service.properties`.
   - Lists to extend:
     - `CFBundleLocalizations` in `iosApp/iosApp/Info.plist` and
       `macosHelper/Resources/HelperInfo.plist`;
     - `knownRegions` in `iosApp/iosApp.xcodeproj/project.pbxproj`, together
       with a file reference for `<code>.lproj/InfoPlist.strings` in the
       `InfoPlist.strings` variant group (copy the `pl` entries);
     - `androidApp/src/main/res/xml/locales_config.xml`.
3. **Website.** Under `website/src/pages/<code>/`, add one page for each
   English `.astro` page. Add `_PRIVACY.md` for the privacy page. Add
   `chrome.json` with the layout's labels; its `languageName` is the
   language's own name. The language switcher, `hreflang` links, and prefixed
   URLs come from the directory.
4. **Store listing.** In `docs/store/<store directory>/`, add:
   - `listing.md`, `description.txt`, and the current What's New text;
   - `capture.json`, with the launch language, locale, and synthetic schedule
     name.

   Then capture the screenshots with the store capture command in the
   [`posato-control` README](../../tools/posato-control/README.md). Nothing is
   uploaded until a release runs `store prepare --locale`.
5. **Review the copy.** The maintainer reviews Polish directly. For any other
   language, an independent agent reviews the catalogs, site, and listing.
   It also translates the key screens back into English. The maintainer
   accepts the copy from that report in the pull request (`user-confirmed`,
   2026-10-28).
6. **Prove it.** Run the per-target language proof in the
   [verify-posato feature map](../../.agents/skills/verify-posato/features/README.md)
   on the Mac in a Tart VM, the iPhone, and Android. Linux and Windows take
   the language from the session locale. `I18N-001` proved them through the
   shared catalog only, and `posato-control` has no language recipe for them
   yet; add one to the feature map when a language row first needs it. Also
   do a narrow pass for longer text, since German runs about a third longer
   than English:
   - run the store capture command on the smallest supported iPhone
     Simulator, writing into `build/verification/`;
   - show the main screens of the Mac app at its minimum window width;
   - look for clipped or wrapped labels in both.

## What the checks enforce

- **`verifyEnglishText`** rejects any accented Latin letter outside a shipped
  language's directories. A quote in another language fails wherever it
  lands, and test data spells such letters as `\u` escapes.
- **`verifyCatalogs`** compares every translated catalog with its English
  one. It checks:
  - the same keys;
  - the same placeholders;
  - every CLDR plural category of the language;
  - every number of 1000 or more from the English text, grouped as that
    language writes it.

  Info.plist strings have no English file, so only blanks are checked there.
- **`verifyLocaleLists`** requires these to name exactly the shipped
  languages:
  - the iOS and helper `CFBundleLocalizations`;
  - the iOS `knownRegions` and file reference;
  - the Android locale config.

  It also requires each language to have every website page and a store
  listing.

## Lessons from `I18N-001`

- Read Compose resources with `getString` only after the process has chosen
  its locale. Text that domain code needs, such as the default set name, is
  loaded once at startup, as `loadSetNameText()` does. Compose `getString`
  fails in JVM unit tests.
- Verification presses Posato controls by `--id`, so recipes work in every
  language. Native iOS alert buttons have no id, and flows press them by
  their English title. Run flows in English and switch only the app's
  language for the language proof.
- A translated catalog keeps the English numbers. Write limits such as
  `1,024` with the language's grouping; `verifyCatalogs` names the expected
  form.
