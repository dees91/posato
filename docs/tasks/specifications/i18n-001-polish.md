# `I18N-001`: Ship Posato in Polish

- **Review tier:** `standard`
- **Tier reason:** The change spans the shared UI, several platform shells, the site, and the store listing, so one independent completed-change review is warranted. It adds no trust boundary, credential, or privileged protocol field.
- **Dependencies:** None. `DOCS-007` and `RELEASE-010` depend on this row.
- **Integration group:** `PR-I18N-PL`, roadmap revision 35, wave R1.6/W1.
- **Authority:** [Release roadmap](../release-roadmap.md) revision 35, idea 12 in [open questions](../../wiki/topics/mvp-open-questions.md) (`user-confirmed` 2026-09-24 and 2026-10-28), [DESIGN.md](../../../DESIGN.md).
- **Record:** [i18n-001-polish.md](../executions/i18n-001-polish.md)

## Outcome

Posato follows the system language. With Polish selected, the whole interface on Mac, iOS, Linux, Android, and Windows, the macOS pause page, permission and file-type descriptions, dates and times, `posato.app` at `/pl/` (including the privacy policy), and a prepared `pl-PL` App Store listing are Polish. English stays the wording it is today, apart from one camera sentence. There is no in-app language switch.

## Boundaries

- The agent drafts the Polish. The maintainer reviews it in the pull request, not before.
- English catalog wording stays unchanged except the scan hint, which says "this device's camera" in English and the matching Polish for "this device's camera".
- Device sentences are full phrases per device. A bare device noun is not interpolated into one sentence.
- No other language, no in-app switch, and no translation of docs, the README, license texts, or Sparkle dialogs.
- Do not publish the listing or a release. `store prepare` gains a locale argument and still defaults to `en-US`.
- The pause-page helper reads the app's per-app language. No new field on the root daemon protocol.
- Guest operating systems stay English. Only the app is set to Polish for the Polish proof. System dialogs stay on those English labels.
- A later change to an English catalog string updates the Polish string with the same key in the same commit.
- Rows `ONBOARDING-005`, `SCHEDULE-007`, `TARGETS-013`, `DOCS-007`, `PAUSE-001`, and `SESSION-008` stay out of this task except for that rule.

## Acceptance

- `AC-01` — Polish as the system or per-app language shows Polish across the shared UI, the pause page, the Android block screen, and dates. English still shows today's English, plus the camera sentence above.
- `AC-02` — Counts that Polish inflects use one, few, and many forms.
- `AC-03` — A missing Polish key fails the build.
- `AC-04` — `/pl/` serves the same pages as the English site, with `hreflang` and a language link, and does not redirect. English URLs stay as they are.
- `AC-05` — `docs/store/pl-PL` holds the description, subtitle, current What's New, and iPhone and iPad screenshots. Nothing is uploaded.
- `AC-06` — An English guest completes a session through controls selected by identifier.

## Verification

- Key parity, then an English session flow in a Tart Mac guest and a short run on the test iPhone.
- A Polish-only-for-the-app proof: Session, pause sets, and Schedules; one few-plural and one many-plural; the Polish pause page on Mac; the Android block screen.
- The site build of both privacy pages at a narrow and a wide viewport.

## Decisions or blockers

- None. The glossary is a draft inside the Polish resources; corrections happen in the pull request.
