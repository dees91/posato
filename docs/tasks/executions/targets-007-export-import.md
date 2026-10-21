# Execution: `TARGETS-007`

- **Brief:** [Export pause sets and schedules to a file and import them on any device](../specifications/targets-007-export-import.md)
- **Status:** `planned` (waiting for the independent plan review)
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
   one): 12 characters from the pairing alphabet (60 bits), shown and typed
   in groups of three like the pairing code. Key = PBKDF2-HMAC-SHA256,
   600,000 iterations, 32 bytes, random salt. `SyncCryptoProvider` gains
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
     with new ids. The current default stays.
   - Add and keep separate: every file set becomes a new set; a clashing
     name gets " (imported)" through the `copyNameOf` byte rule.
   - Replace: the file's first set lands on `PauseSetId.FIRST` (keeping
     this device's apps for it), every other current set is removed with
     its apps, the file's other sets are created, and the file's default
     becomes the default.
   - Schedules: add modes append the file's schedules with refs mapped,
     skipping exact duplicates; replace removes all current schedules and
     saves the file's. Fresh `ScheduleId`s.
   - Over a limit the planner refuses and the summary says which choice
     fits (D3).
5. **Authoring.** Sets through `LocalTargetPolicyStore.replaceSets` in one
   write gate, so `SyncTargetPolicyStore` queues the same intents as hand
   edits; schedules through `LocalScheduleStore.save`/`remove` with the
   linked workspace id. Sets first, then schedules; a failure stops and
   reports what was applied. While a pause or a scheduled pause runs,
   replace is unavailable (D4); add follows the hand-edit rule (added
   items pause now).
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

## Open decisions for the maintainer

- **D1, code length:** 12 characters (60 bits, recommended) or the
  mockup's 9 (45 bits; still slow to guess at 600,000 PBKDF2 rounds, but
  weaker if a file leaks).
- **D2, default set on add:** keep this device's default (recommended) or
  take the file's.
- **D3, over the limit:** refuse and point to replace or combine
  (recommended), or import what fits and list the rest.
- **D4, replace during a running pause:** unavailable until it ends
  (recommended), or allowed with the hand-edit rules.
- **D5, the unnamed first set:** match it to this device's first set
  (recommended, mirrors sync identity) or treat it as a set named "My set".

## Notes

- `PRIVACY.md` is being rewritten for 1.5 in `DOCS-006` (#178, not merged);
  whichever lands second rebases the new section onto the other.
