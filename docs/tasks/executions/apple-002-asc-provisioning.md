# Execution: `APPLE-002`

- **Brief:** [Provision development profiles and certificates through the App Store Connect API](../specifications/apple-002-asc-provisioning.md)
- **Status:** `active`
- **Review tier:** `standard`
- **Implementer:** agent (worktree `posato-apple-002`)
- **Reviewer:** pending until assigned
- **Branch:** `feature/apple-002-asc-provisioning`
- **Worktree:** `~/Projects/Polyglot/posato-apple-002`
- **Updated:** 2026-09-05

## Plan

1. Module skeleton, configuration, redaction, envelope, error codes.
2. ES256 token: PKCS#8 key, JWT claims, DER-to-JOSE signature conversion.
3. Bounded HTTP with redacted errors and `GET`-only retry.
4. The six App Store Connect operations and the five App IDs.
5. `devices register`, 6. `certificates ensure`, 7. `profiles ensure`, 8. `doctor`.
9. Guide, module README, `.gitignore`, brief corrections.
10. Manual run against the real account, then closeout.

## Result

All ten steps are complete as `:posato-provisioning` (`tools/posato-provisioning`),
JDK-only apart from the existing clikt and kotlinx-serialization catalog
entries. The maintainer created the App Store Connect team key on 2026-09-05
and the acceptance run passed on the real account.

Three defects were found by the independent plan review and corrected before
implementation. Each would have produced a tool that reported success on a
wrong result:

- the Mac's device identifier is the Provisioning UDID from `system_profiler`,
  not the Platform UUID from `ioreg`; on this Apple silicon Mac they are
  different values of different lengths, and the wrong one yields an account
  record no profile matches. The same trap exists for an iPhone, where the
  identifier is `hardwareProperties.udid` and not devicectl's `identifier`;
- a local signing identity is correlated to the account by exact certificate
  bytes before a profile is built around it. Without that, the tool could name
  an account certificate this Mac has no private key for, install the profile,
  and report success;
- only `GET` is retried. A retried `POST` that had already been applied would
  create a duplicate certificate against Apple's per-team cap, a conflicting
  device, or a second profile claiming a unique name.

The acceptance run found one further defect. `devicectl` reports a phone
paired over the local network as connected as well, and its `tunnelState`
changed between two consecutive listings on this Mac, so the original filter
would have registered a different set of devices depending on when it ran and
would have consumed a device slot for a phone that merely shares the network.
Selection now requires `transportType` of `wired`.

Deliberate deviations from the brief, both recorded there: certificate
creation sits behind `--create`, and the expired-versus-valid reading of the
`--replace` rule. Pagination is not implemented; a second page stops the
command instead of following a service-supplied URL.

No suppression annotation was added. Two `ThrowsCount` findings were resolved
by routing each parser's rejections through one throw site, and an unchecked
cast in a test was removed by narrowing the transport seam to a single request
rather than the whole HTTP client.

## Completed-change review

- **Verdict:** `pending`
- **Critical or Required findings:** pending
- **Resolution:** pending

## Verification

| Check run | Result | Evidence |
| --- | --- | --- |
| `./gradlew quality` | pass | Full aggregate gate with the new module's detekt, ktlintCheck, and test included, rerun after the last correction |
| `:posato-provisioning:test` | pass | 76 tests, including a 200-sample ES256 signature round trip verified against a generated public key |
| `AC-01` doctor inventory | pass | 15 checks in a fixed order, each unmet one carrying a remedy; a test asserts no configured or discovered value appears in any detail or hint |
| `AC-03` no key configured | pass | `doctor` exits 3, reports "No request was attempted", and `profiles ensure` exits 3; `~/Library/Developer/Posato/` unchanged by checksum |
| `AC-03` rejected credential | pass | Unit-level: 401 maps to `ASC_UNAUTHORIZED` and is never retried |
| Private-data scan | pass | `git diff --check` clean; no team, device, key, or personal path in the diff; device fixtures confirmed not to collide with this Mac's real identifiers |
| `AC-02` iOS extension profile | pass | `profiles ensure app.posato.ios.activitymonitor` installed a profile carrying the Family Controls entitlement, the `group.app.posato.ios.session` App Group, this Mac's certificate, and the registered devices, to both the Posato directory and the Xcode directory |
| `AC-02` macOS sync profile | pass | `profiles ensure app.posato.macos.sync` replaced the hand-made `SYNC-006` file in place; `:desktopApp:verifyMacOsDevelopmentPackaging` passes against it with the development identity, so its signed companion-entitlement assertions hold |
| `AC-02` repetition | pass | Repeating `devices register`, `certificates ensure`, and both `profiles ensure` cases reports `already-registered`/`reused`, issues no `POST` or `DELETE`, and leaves every installed file unchanged by checksum |
| `AC-04` secret-free tracked files | pass | The scan above, plus `doctor` reporting all fifteen conditions without printing a key, issuer, team, device identifier, or path |
| All five profiles installed | pass | `doctor` reports 15 of 15 conditions ok |

## Blockers and accepted risks

- Cleared 2026-09-05: the maintainer created the App Store Connect team key and
  configured the three `posato.asc.` values. No blocker remains.
- Local build state: verifying `AC-02` requires staging the desktop package
  with the development identity, which leaves it signed that way. `quality`
  runs without those properties and expects the ad-hoc entitlements, so rerun
  `:desktopApp:verifyMacOsDevelopmentPackaging` with no properties afterwards
  to restage. This is a property of the existing packaging tasks, not of this
  change.
- Observed once: a `profiles ensure app.posato.macos` run failed before its
  profile was created, and the immediate rerun succeeded reporting that no
  profile of that name existed. No duplicate was produced, which is the
  behaviour the `POST`-never-retried rule exists to give; the failure category
  was not captured and is not reproducible.
- Write surface: three files outside the brief's list were changed and the
  brief now records why — root `build.gradle.kts` (three lines in `quality`,
  without which `AC-04` is unsatisfiable), `.gitignore`, and one paragraph in
  `docs/development/README.md`.
- Unexercised path: `certificates ensure --create` cannot run on this Mac
  without revoking its valid identity, so it ships covered by unit tests and
  the guide's troubleshooting rather than by a live run. It is behind a flag
  for that reason.
- Follow-up: `tools/posato-control/README.md` could cross-link the two doctor
  reports. It was out of scope here because a separate change is in flight in
  that module.

## Final

- **Status:** `active`
- **Outcome:** Implementation complete and accepted against the real account:
  all five development profiles are installed, `doctor` reports fifteen of
  fifteen conditions ok, and the macOS packaging verification passes with the
  tool-produced profile. Awaiting the independent completed-change review.
