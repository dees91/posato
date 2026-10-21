# Execution: `TARGETS-007`

- **Brief:** [Export pause sets and schedules to a file and import them on any device](../specifications/targets-007-export-import.md)
- **Status:** `planned` (plan review folded; waiting for a short re-check)
- **Review tier:** `high-risk`
- **Implementer:** Claude
- **Branch:** `targets-007-export-import` (rebase after #182, roadmap revision 26)

## Plan

1. **Format.** Plain: UTF-8 JSON `{"format":"posato-pause-sets","version":1,
   "sets":[{"ref","name","first","domains"}],"defaultSet","schedules":[{"name",
   "weekdays","start","end","set"}]}`. File-local `ref`s; no `PauseSetId`
   leaves the device; the unnamed first set is `"name":null,"first":true`.
   Only live sets and schedules are exported. Coded: header = magic `PSX1`,
   container version byte (1), iterations (u32 BE), salt (16), nonce (12);
   body = AES-256-GCM of the JSON with the whole header as associated data.
   The container version covers the binary layout; the JSON `version` covers
   the content. Extension `.posato`, media type
   `application/vnd.posato.pause-sets`, iOS type `app.posato.pause-sets`.
   Readers detect plain or coded by the first bytes, never the name.
2. **Code and key.** 9 characters drawn uniformly at random from the 32
   letters of the pairing alphabet: 45 bits. PBKDF2-HMAC-SHA256 over the
   code's 9 ASCII bytes (no groups or spaces, upper case) with the salt,
   then HKDF-SHA256 with info `app.posato.export.v1/key` for the AES key
   (domain separation). Iterations: one fixed value per container version,
   chosen after measuring (step 9); a reader refuses any other count before
   deriving. Residual risk stated in `PRIVACY.md` and on the export dialog:
   the code stops casual readers, not a determined offline attack on a
   leaked file (`inferred`, order of GPU-years). `SyncCryptoProvider` gains
   `pbkdf2HmacSha256` (JDK and Android `PBKDF2WithHmacSHA256`, iOS
   CommonCrypto in the Swift provider).
3. **Validation before anything changes.** Size cap 4 MiB (derived: 10 sets
   of 1,024 domains of 253 bytes plus names and schedules, pinned by a
   worst-case round-trip test). Then format and versions, JSON shape, refs,
   names and domains normalized (trim, NFC) and checked through
   `PauseSets.of` (at most 10 live sets, 1,024 unique domains, name rules)
   and `SchedulePlan` rules, 10 schedules. A bad header, length, or
   truncation is "damaged"; a failed GCM tag reads "This code doesn't match,
   or the file was changed." (GCM cannot tell the two apart); then a newer
   version and each limit.
4. **Planner (pure).** `planImport(current, file, mode)` returns the next
   `PauseSets`, schedule saves and removals, and the preview.
   - Matching (`user-confirmed`): the file's unnamed first set matches this
     device's first set, also an empty unnamed one on a fresh install.
     Other sets match by normalized, case-insensitive name.
   - Add and combine: matched sets gain missing websites; others are new;
     this device's default and every local name stay (first matches first;
     a file set named like the local first set is new and takes
     " (imported)").
   - Add and keep separate: every file set is new; a clash takes
     " (imported)" (`user-confirmed`) by the `copyNameOf` byte rule. An
     unnamed file first set placed as a new set takes the stored name
     "My set", through the same clash rule.
   - Replace: the file's first set lands on `PauseSetId.FIRST` when FIRST is
     live here, else on a new id (a refused FIRST is removed like other
     refused sets). A named file first set brings its name; an unnamed one
     keeps this device's first-set name (preview shows it, and that the
     first set's apps stay). Without a first set in the file, a live local
     FIRST is kept, emptied, and keeps its name. Every other current set,
     refused ones too, is removed; the file's default becomes the default.
   - Replace is refused while a running pause or running schedule
     occurrence uses a set it would remove or a schedule it would remove,
     with Delete's wording ("You can replace after the pause ends"), checked
     at preview and again at confirm. Add always works; first-set edits
     during a pause follow hand-edit rules.
   - Schedules arrive switched off. Add appends them with mapped refs and
     skips exact duplicates; replace removes current ones first.
   - Over a limit: refused, suggesting combine or replace.
5. **Authoring.** A new composite write path, `ImportWrite`, in one SQL
   transaction on the shared `PosatoDatabase`, under the policy gate then
   the schedule gate (this lock order is the only one used). It captures the
   linked workspace once for both stores and refuses the import if that
   fails; writes sets with `pauseSetIntents(before, after)` and schedules
   with the existing schedule intent rows; runs `sweepRetention`; fires both
   stores' change flows after commit. On confirm the planner runs again
   under the gates; a different result stops and shows the new preview.
6. **Turning schedules on.** The result screen lists the imported schedules
   with **Turn on imported schedules**; it warns when a schedule's window is
   open now (it would start a pause at once). Each can also be turned on
   later in Schedules. `DESIGN.md` gets this step.
7. **Files, code handling, JSON, apps:** as in the brief's "Implementation
   constraints" (pickers per platform, temporary files deleted, typed-code
   normalization and zeroing, JSON field types, app choices on replace).
8. **UI from the mockup:** menu items, export dialog, import dialog with the
   code step, two radio cards, add's two options, preview, Import or
   destructive Replace, the linked-device warning, and the result screen.
9. **Measure first:** PBKDF2 latency at candidate counts on the Galaxy
   XCover7 and the iPhone 13 mini; pick the highest count within 2 s on the
   slower one; derivation runs off the main thread with progress. Numbers
   go in this record.
10. **Tests first:** RFC 7914 PBKDF2-SHA256 vectors and one pinned
    end-to-end coded file in the JVM and Swift provider tests; codec and
    validation incl. nested unknown fields on JVM and Native (AC-01);
    planner cases for each matching, replace, pause, and limit rule
    (AC-02); convergence of two exchanging stores after an import (AC-03).
11. **Tool first:** `posato-control flow export --out PATH [--code]` and
    `flow import --file PATH [--code C] --mode combine|separate|replace`
    on every target, driving the real dialogs (Mac Go to folder, Linux
    Ctrl+L fallback, the iOS picker like `flow folder`), proven in the tool
    before the feature; the host moves files (`vm`, `linux exec`, `adb`).
12. **Devices, one target at a time:** every platform exports once and
    imports once; Mac primary to peer VM with add and replace; an iPhone export
    imported on Android; Android to Linux combine and separate; Linux to
    Android replace; a Mac coded
    export to the iPhone; an import during a running pause; a linked
    Android and Linux pair receiving an import.
13. **Docs:** `PRIVACY.md` "Export and import" (contents, the code and its
    limit, nothing kept), `DESIGN.md` entries.

## Decisions

- `user-confirmed` 2026-10-21: 9-character code with the risk stated; add
  keeps the default and replace takes the file's; refuse over a limit;
  replace refused during a pause that uses what it removes; schedules
  arrive off; first-set matching; " (imported)"; live items only.
- `agent-delegated` 2026-10-21: 4 MiB cap, container layout, HKDF
  separation, `ImportWrite`, re-plan on confirm, "My set", combine keeps
  local names, emptied FIRST when the file has none.
- `PRIVACY.md` is rewritten for 1.5 in #178; the second to land rebases.
