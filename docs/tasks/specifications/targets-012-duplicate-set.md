# `TARGETS-012`: Duplicate a pause set

- **Review tier:** Standard
- **Tier reason:** A new set through the existing set authoring, plus one
  local operation on each platform's application store (Mac SQLite, the
  iOS provider, the Android and Linux file). No synchronized format,
  privilege, or trust change.
- **Dependencies:** `ANDROID-001` (merged in #168); release 1.5, wave 4.
- **Integration group:** PR-SET-DUPLICATE
- **Authority:** [Release roadmap](../release-roadmap.md) revision 24, row
  `TARGETS-012`; idea 43 (`user-confirmed` 2026-10-20).
- **Record:** [execution record](../executions/targets-012-duplicate-set.md)

## Outcome

A set's menu (and its swipe actions on iOS) offers **Duplicate**, which
adds "<name> (copy)" with the same websites and this device's apps for it.

## Boundaries

- The copy goes through the set authoring like **New set**, so it
  synchronizes and counts toward the limit of 10. At the limit, Duplicate
  is disabled, as **New set** is.
- This device's apps are copied locally (`copyChoices`); other devices
  choose their own apps, as for any new set. A copy that fails to save
  drops the copied apps again.
- The name stays within 80 bytes: a long name is shortened before
  " (copy)".
- Non-goals: copying schedules, choosing the new name in a dialog.

## Acceptance

- `AC-01`: Tests written first: the view model's copy has the source's
  websites, its name, and a copy of this device's apps, and nothing is
  added at ten sets. Each store's copy replaces the target's choices with
  the source's.
- `AC-02`: On Android and in a Mac VM the copy appears in the list with the
  source's website and app counts.

## Verification

- `shared` jvmTest, `desktopApp` test, iOS wrapper test.
- `posato-control -t android` and `--vm primary`.

## Decisions or blockers

- None.
