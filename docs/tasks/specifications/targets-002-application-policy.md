# `TARGETS-002`: Manage one semantic application group

- **Review tier:** `high-risk`
- **Tier reason:** User-controlled Unicode and a v1-to-v2 migration can corrupt,
  partially replace, or disclose sensitive persisted policy.
- **Dependencies:** `MODEL-001`; completed `TARGETS-001` implementation baseline
- **Integration group:** `PR-APP-POLICY`
- **Authority:** `TARGETS-002` in the accepted MVP roadmap and ADR 0006

## Outcome

A person can add, edit, remove, and review one shared semantic application
group while the shared UI truthfully identifies device-local app selection as
still required.

## Boundaries

- Normalize a bounded, trimmed group name to Unicode NFC and require one
  through 80 strict UTF-8 bytes. Reject Unicode General Category `Cc` in common
  code as the portable C0 `U+0000`–`U+001F` and C1
  `U+007F`–`U+009F` ranges; NFC normalization is the only platform leaf.
- Persist the optional group atomically with exact domains in the existing
  revisioned local policy. Preserve valid v1 policy during migration and every
  failed replacement.
- Keep native app identities and selections device-local. Do not add a picker,
  platform token, mapping store, availability producer, navigation, use case,
  module, or dependency.
- Use **Application group** in visible copy while retaining application policy
  as the technical and synchronization term.
- Keep `.research/blocker` read-only and use it only as feasibility evidence;
  copy no PoC code or generic application-identifier model.

## Acceptance

- `AC-01` — JVM and iOS accept the same trimmed NFC name and reject empty,
  malformed, C0 or C1 controlled, noncanonical stored, oversized raw, or
  over-80-byte values without exposing them in default strings or diagnostics.
- `AC-02` — A real v1 database migrates to v2 without changing its revision or
  exact domains, and fresh or reopened v2 stores round-trip the optional group.
- `AC-03` — Add, edit, remove, conflict, corruption, and storage failure preserve
  the last valid aggregate policy unless one atomic replacement succeeds.
- `AC-04` — The shared macOS and iOS screen manages one application group and
  states that apps still need selection on this device without presenting a
  fake mapping action or platform identifier.
- `AC-05` — Existing exact-domain behavior, feature ownership, previews, and
  platform builds remain valid.

## Verification

- Run focused domain, ViewModel, real-SQLite, and migration tests on JVM and the
  iOS Simulator.
- Run `./gradlew quality`, the credential-free iOS host build, platform manual
  inspection, privacy/structure checks, a path-specific check that the committed
  v1 `databases/1.db` fixture is unchanged, and `git diff --check`.

## Decisions or blockers

- Use a narrow compile-time NFC `expect`/`actual` leaf: JDK normalization for
  JVM and Android, and Foundation canonical precomposition for iOS.
- No blocker is known before implementation.
