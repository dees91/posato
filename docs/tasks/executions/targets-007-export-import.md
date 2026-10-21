# Execution: `TARGETS-007`

- **Brief:** [Export pause sets and schedules to a file and import them on any device](../specifications/targets-007-export-import.md)
- **Status:** `planned` (decisions folded; waiting for the independent plan review)
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Branch:** `targets-007-export-import`

## Plan

1. **Format (`shared/.../targets/transfer/`).** A plain file is UTF-8 JSON:
   `{"format":"posato-pause-sets","version":1,"sets":[{"ref":"s1","name":"Focus","first":true,"domains":[…]}],"defaultSet":"s1","schedules":[{"name","weekdays","start","end","enabled","set":"s1"}]}`.
   `ref` values are local to the file; `PauseSetId`s never leave the device.
   The unnamed first set is written with `"name":null,"first":true`. A coded
   file is binary: magic `PSX1`, version byte, PBKDF2 iteration count (u32),
   16-byte salt, 12-byte nonce, then AES-256-GCM of the JSON with the header
   as associated data. Extension `.posato`; media type
   `application/vnd.posato.pause-sets` (unregistered vendor tree); iOS
   exports the type `app.posato.pause-sets` conforming to `public.data`.
   Readers detect plain versus coded by the first bytes.
2. **Code and key.** Posato generates the code (the person never invents
   one): 9 characters from the pairing alphabet (about 45 bits, D1), shown
   and typed in three groups of three like the pairing code. Key =
   PBKDF2-HMAC-SHA256, 1,000,000 iterations (raised from the usual 600,000
   because the code is short; the count is in the header so a later version
   can raise it), 32 bytes, random 16-byte salt. Residual risk, stated in
   `PRIVACY.md` and on the export dialog: the code stops a casual reader and
   a leaked file found by chance, but someone who has the file and spends
   serious computing power on guessing (on the order of a hundred GPU-years
   at today's PBKDF2 rates, `inferred`) could open it, so a person who needs
   more should not send the file through places they do not trust. `SyncCryptoProvider` gains
   `pbkdf2HmacSha256`: JDK and Android `PBKDF2WithHmacSHA256`, iOS
   CommonCrypto `CCKeyDerivationPBKDF` in the Swift provider, tested
   against published SHA-256 vectors. AES-GCM, randomness, and the code
   alphabet are reused from `FolderPairing`; the code is never stored.
3. **Validation before anything changes.** Files over 1 MiB, an unknown
   `format` or a newer `version`, malformed JSON, more than 10 sets or 10
   schedules, a set over 1,024 domains or the 2,048 total, names over 80
   bytes, invalid domains (`ExactDomainPolicy`), unknown or dangling
   `ref`s, and impossible schedules (`SchedulePlan` problems) are each
   refused with a message; unknown extra fields are ignored. Duplicates in
   a set collapse.
4. **Planner (pure, `planImport(current, file, mode)`).** It returns the
   resulting `PauseSets`, schedule saves and removals, and a summary.
   - Add and combine: a file set matches a current set by trimmed,
     NFC, case-insensitive name; the unnamed first set matches the current
     first set. Matched sets gain missing websites; others are new sets
     with new ids. This device's default stays (D2).
   - Add and keep separate: every file set becomes a new set; a clashing
     name gets " (imported)" through the `copyNameOf` byte rule.
   - Replace: the file's first set lands on `PauseSetId.FIRST` (keeping
     this device's apps for it), every other current set is removed with
     its apps, the file's other sets are created, and the file's default
     becomes the default.
   - Schedules: add modes append the file's schedules with refs mapped,
     skipping exact duplicates; replace removes all current schedules and
     saves the file's. Fresh `ScheduleId`s.
   - Over a limit the planner refuses and the summary suggests the choice
     that fits, combine or replace (D3).
5. **Authoring.** Sets through `LocalTargetPolicyStore.replaceSets` in one
   write gate, so `SyncTargetPolicyStore` queues the same intents as hand
   edits; schedules through `LocalScheduleStore.save`/`remove` with the
   linked workspace id. Sets first, then schedules; a failure stops and
   reports what was applied. During a running pause, add and replace follow
   the hand-edit rules (D4): added websites pause now, and websites removed
   by replace, including those of a removed set, stay paused until the pause
   ends, as when a set is edited or deleted by hand.
6. **Files and sharing.** Mac and Linux: AWT `FileDialog` save and open
   (native on macOS, GTK on Linux). iPhone: share sheet with the file
   (AirDrop, Save to Files) for export and `UIDocumentPickerViewController`
   for the `.posato` type on import, beside `FilesFolderPicker`. Android:
   share sheet through a `FileProvider` for export and
   `ActivityResultContracts.OpenDocument` for import, beside the folder
   picker in `MainActivity`. A host port per platform, like
   `FolderSyncControls.browse`.
7. **UI from the mockup.** Pause sets menu items; the export dialog
   (summary, **Protect with a code** switch, the code, the warning for a
   plain file); the import dialog (file summary, the code step when coded,
   **Choose how to import** with two radio cards, add's two options,
   the result preview, Import or a destructive Replace, and on a linked
   device "Changes also reach your other devices").
8. **Tests first:** codec, vectors, validation, planner, authoring intents
   (AC-01 to AC-03), each failing before its code.
9. **Tool first:** `posato-control flow export --out PATH [--code]` (prints
   the code) and `flow import --file PATH [--code C] --mode
   combine|separate|replace` on desktop, device, Android, and Linux; the
   host moves files with `vm`/`linux exec`, `adb push/pull`, and
   `devicectl` (iOS picker automation is the main risk: if XCUITest cannot
   drive the document picker, place the file in the app's Documents and
   pick it from "On My iPhone").
10. **Docs:** `PRIVACY.md` "Export and import" (what the file holds, the
    code, that Posato keeps neither the code nor a copy, and that the file
    goes wherever the person sends it), `DESIGN.md` entries
    (`user-confirmed` mockup), and the limits page if a limit is visible.
11. **Devices (AC-04), one target at a time:** Mac Tart clone export coded,
    copy to the host, import on the test iPhone (combine); Android export,
    import on a Linux clone (separate, then combine); Linux export, import
    on Android with replace; a linked Android and Linux pair for AC-03.

## Decisions

- `user-confirmed` 2026-10-21: D1 a 9-character code, as in the mockup,
  with 1,000,000 PBKDF2 iterations and the residual risk stated; D2 add
  keeps this device's default and replace takes the file's; D3 an import
  over a limit is refused and suggests combine or replace; D4 replace during
  a running pause follows the hand-edit rules.
- `agent-delegated` 2026-10-21: D5 the file's unnamed first set matches
  this device's first set, as sync identity does.

## Notes

- `PRIVACY.md` is being rewritten for 1.5 in `DOCS-006` (#178, not merged);
  whichever lands second rebases the new section onto the other.
