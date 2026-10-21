# `TARGETS-007`: Export pause sets and schedules to a file and import them on any device

- **Review tier:** High-risk
- **Tier reason:** A new file format that leaves the device, optional
  encryption with a code a person reads aloud, and an import that authors
  many set and schedule changes at once, including removals in replace
  mode, which reach every linked device through sync. A brief independent
  plan review comes before the implementation.
- **Dependencies:** `TARGETS-012` (merged in #177); release 1.5, wave 4b.
  `DOCS-006` and `RELEASE-007` wait for this row.
- **Integration group:** PR-EXPORT-IMPORT
- **Authority:** [Release roadmap](../release-roadmap.md) revision 26, row
  `TARGETS-007`; idea 14 in the
  [wiki idea queue](../../wiki/topics/mvp-open-questions.md); the clickable
  mockup accepted on 2026-10-21 (`user-confirmed`); [PRIVACY.md](../../../PRIVACY.md);
  [DESIGN.md](../../../DESIGN.md);
  [ADR 0006](../../decisions/0006-apple-mvp-encrypted-operation-and-convergence.md)
  and [ADR 0010](../../decisions/0010-linux-android-and-folder-workspace.md)
  for how authored changes sync.
- **Record:** [execution record](../executions/targets-007-export-import.md)

## Outcome

A person exports their pause sets (names and websites), the default set,
and their schedules to one `.posato` file, optionally protected by a code
Posato shows once, and imports that file on any Posato device, for example
a Mac on another Apple Account. Import shows what the file holds and lets
the person add it (combining sets with the same name, or keeping them
separate) or replace their sets and schedules. It is a one-time copy, not
sync: nothing stays connected. App choices stay on each device.

## Boundaries

- Every platform: Mac, iPhone, Linux, and Android, from the Pause sets menu
  (**Export pause sets…**, **Import…**), as in the mockup.
- The file never holds app choices, sessions, history, device names,
  workspace data, or keys. iPhone app choices are opaque tokens and cannot
  move; desktop and Android app choices stay local by the same rule.
- Imported changes go through the existing set and schedule authoring, so a
  linked device queues them like hand edits and every member receives them;
  replace mode removes sets and schedules for every member too, and the
  import screen says so.
- The workspace's 10-set and 10-schedule limits and the domain limits hold;
  an import that would exceed them is refused before anything changes and
  suggests combine or replace.
- The code is 9 characters (about 45 bits) with a slow key derivation; it
  protects against casual readers, not against a determined offline attack
  on a leaked file, and `PRIVACY.md` and the export dialog say so.
- During a running pause, add always works with the hand-edit rules (added
  websites pause now). Replace is refused while a running pause or a running
  scheduled pause uses a set or schedule it would remove, with Delete's
  wording.
- Imported schedules arrive switched off; a separate step on the result
  screen turns them on and warns when a window is open now.
- Non-goals: opening a `.posato` file from Files, Finder, or a share
  extension (import starts in Posato), exporting app choices, merging
  website lists across differently named sets, sync of any kind, and
  `TARGETS-008` (quick sharing).

## Acceptance

- `AC-01`: Format tests written first and shown failing: a plain and a
  coded file round-trip, with RFC 7914 vectors and one pinned coded file in
  the JVM and Swift providers; a wrong code, a damaged file, an iteration
  count other than the version's, a file over 4 MiB, a newer version,
  invalid domains, and over-limit contents are each refused with their own
  message and change nothing.
- `AC-02`: Import planner tests written first: add-and-combine,
  add-and-keep-separate (`Name (imported)`), and replace, including the
  unnamed first set (matched, placed as a new set, kept name on replace,
  no first set in the file), the default set, schedules that point at
  imported sets and arrive off, duplicates, the limits, and replace refused
  during a running pause.
- `AC-03`: Sets, schedules, and their intents are written in one
  transaction and re-planned on confirm. Two linked stores that exchange
  after an import converge: the peer's sets, default, and schedules match,
  including replace with a removed set, a default change, and a set refused
  at the cap. A real device pair receives an import.
- `AC-04`: On real devices, each platform exports once and imports once:
  Mac primary to peer VM with add and replace, a coded Mac export on the
  test iPhone, Android to Linux in each add mode, Linux to Android with
  replace, and an import during a running pause. PBKDF2 latency measured on
  the Galaxy XCover7 and iPhone 13 mini stays within 2 s.
- `AC-05`: `PRIVACY.md` and `DESIGN.md` describe export and import; the
  `DESIGN.md` entry is `user-confirmed` from the mockup.

## Implementation constraints

From the independent plan review (2026-10-21, `agent-delegated` unless
noted):

- **iPhone:** import with `UIDocumentPickerViewController(forOpeningContentTypes:
  asCopy: true)`; declare `.posato` in `UTExportedTypeDeclarations`, with no
  `CFBundleDocumentTypes`; no Documents or `UIFileSharingEnabled` fallback,
  which would be a product and privacy change. Export through the share
  sheet, then delete the temporary file when the sheet closes.
- **Android:** export with `CreateDocument` (share sheet second, its
  `FileProvider` cache file deleted afterwards); import with `OpenDocument`
  for `*/*` and detect the file by its first bytes.
- **Desktop:** AWT `FileDialog` parented to the Compose window and called on
  the event thread (`NSSavePanel` on macOS, GTK chooser on Linux).
  `posato-control` drives them: on the Mac the accessibility bridge gains
  Go to folder (⇧⌘G); on Linux, where the JDK chooser may be invisible to
  AT-SPI, an `xdotool` Ctrl+L, path, Return fallback, proven in the tool
  before the feature uses it.
- **Code handling:** typed codes are normalized (case, spaces, hyphens;
  0→O, 1→I, 8→B); the code stays out of saved state, `rememberSaveable`,
  and logs; `PBEKeySpec.clearPassword()` and key bytes zeroed with
  `fill(0)`, as `FolderPairing` does.
- **JSON:** times are minutes from midnight (0 to 1439), weekdays a 7-bit
  mask with Monday as bit 0, names and domains strings; two schedules are
  duplicates when name, weekdays, start, end, and set match after
  normalization. Deeply nested unknown fields under the size cap are
  ignored safely on JVM and Native. `SyncScheduleStore` captures the
  workspace itself, so schedule intents need no extra plumbing.
- **App choices on replace:** removed sets lose this device's app choices
  through `retainOnly`, as `keepSurvivingChoices` does; the preview says
  this device's apps for the first set stay with the imported first set.
- **Cap race:** a peer can fill the 10-set cap first; imported sets may then
  be refused on every device, deterministically, and the result screen
  shows them as refused, as the Pause sets list does.

## Verification

- `shared` tests: the codec, the code key derivation (published test
  vectors), the planner, and the authoring path.
- `posato-control` first (tool-first): `flow export` and `flow import` on
  every target, and moving the file between targets from the host.
- Device runs as in `AC-04`, cited by run id on the final head.

## Decisions or blockers

The maintainer's decisions are `user-confirmed` and the agent's choices
`agent-delegated` (2026-10-21); see the execution record.
