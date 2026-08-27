# `TARGETS-001`: Manage exact website domains locally

- **Review tier:** `high-risk`
- **Tier reason:** User-controlled IDNA input can broaden enforcement or replace
  sensitive persisted policy if validation or state ownership is wrong.
- **Dependencies:** `MODEL-001`
- **Integration group:** `PR-DOMAINS`
- **Authority:** `TARGETS-001` in the accepted MVP roadmap

## Outcome

A person can add, edit, remove, and review canonical exact website domains in
one shared screen on macOS and iOS, with every accepted change persisted by the
existing atomic local-policy store.

## Boundaries

- Accept a domain, not a URL. Reject raw input longer than 1,024 UTF-16 code
  units before trimming or UTS-46 processing, trim surrounding whitespace,
  allow one terminal DNS dot, and canonicalize Unicode through pinned
  cross-platform UTS-46 processing.
- Require at least two valid DNS labels, the existing 63-byte label and
  253-byte domain limits, no IP literal, and a strict Unicode/A-label
  round-trip. Reject malformed, ambiguous, duplicate, or oversized input.
- Preserve the last valid policy until validation and the atomic revision
  update succeed. Never put a domain in a repository-owned error, diagnostic,
  or default string form. The required framework `TextFieldState` is a narrow
  exception because its final implementation includes live text in
  `toString()`; create and retain it only inside the text-field composable and
  never log or pass it to diagnostics.
- Use one AndroidX Multiplatform ViewModel whose private policy, editor, and
  submission flows are combined into immutable aggregate UI state. Keep the
  live domain draft in one `rememberTextFieldState` owned by the text-field
  composable; pass its current text to `submit` and do not mirror the draft in
  `ExactDomainsUiState` or a ViewModel flow. The immutable UI state may expose
  only an editor-session revision needed to recreate the field after edit,
  cancel, or successful persistence. Read the store only while the aggregate
  state is consumed, with an explicit retry rather than an imperative
  initial-load call. The existing store remains the direct data boundary; do
  not add a pass-through use case, module, service locator, custom scope, or
  navigation dependency for this single-screen slice.
- Establish the minimum reusable application UI foundation in this first
  reviewed screen slice: apply one root `PosatoTheme`, use Material 3
  components and state-based text fields exclusively, and make the existing
  Compose Rules Detekt `Material2` check reject Material 2 source use. Do not
  introduce speculative spacing tokens or generic component wrappers.
- Add only consumed IDNA, ViewModel, immutable-collection, Material 3, and
  desktop Main-dispatcher dependencies. Do not add sync, enforcement,
  onboarding, analytics, availability checks, or URL parsing.

## Acceptance

- `AC-01` — Accepted ASCII and Unicode inputs produce one deterministic
  canonical domain on JVM and iOS; malformed, ambiguous, or oversized inputs
  fail, including raw Unicode input that could map or collapse below the DNS
  output limit.
- `AC-02` — Add and edit reject canonical duplicates, invalid edits keep the
  previous valid row, and remove changes only the selected exact row.
- `AC-03` — Successful changes render the persisted snapshot; storage,
  corruption, conflict, and cancellation never replace valid state silently.
- `AC-04` — The shared screen exposes explicit loading, empty, content, edit,
  validation, saving, and safe failure states and works by keyboard on macOS
  and by the iOS Simulator input path without relying on color alone. It uses
  the root Material 3 theme and a state-based `TextFieldState`; static analysis
  rejects Material 2 source use.

## Verification

- Run focused common domain and ViewModel tests on JVM and the iOS Simulator.
- Run `./gradlew quality`, the credential-free iOS host build, manual macOS and
  iOS Simulator interaction/accessibility checks, and `git diff --check`.

## Decisions or blockers

- Pin `org.dexpace:kuri:0.1.0` for deterministic Unicode 17 UTS-46; keep
  Posato's stricter DNS and A-label checks at the product boundary.
- Pin AndroidX Multiplatform ViewModel `2.10.0`; combine private flows into the
  public state with portable `SharingStarted.WhileSubscribed(5_000)` and use
  persistent UI collections. Add coroutines Swing only to supply
  `Dispatchers.Main` on desktop. Navigation 3 remains deferred until a real
  second destination exists.
- Pin stable Compose Multiplatform Material 3 `1.9.0`, which supplies the
  state-based text-field API while remaining independently versioned from the
  Compose Multiplatform plugin. Material 2 is not an allowed source API.
- `user-confirmed` (2026-08-27): scope the redacted-default-string requirement
  to repository-owned carriers and diagnostics. The framework-owned
  `TextFieldState` must stay in the composable layer and must not be logged,
  diagnosed, persisted, or passed to the ViewModel.
