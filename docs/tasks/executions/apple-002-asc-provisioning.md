# Execution: `APPLE-002`

- **Brief:** [Provision development profiles and certificates through the App Store Connect API](../specifications/apple-002-asc-provisioning.md)
- **Status:** `blocked`
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

Steps 1 to 9 are complete as `:posato-provisioning` (`tools/posato-provisioning`),
JDK-only apart from the existing clikt and kotlinx-serialization catalog
entries. Step 10 is blocked on the maintainer's App Store Connect team key.

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
| `./gradlew quality` | pass | Full aggregate gate with the new module's detekt, ktlintCheck, and test included |
| `:posato-provisioning:test` | pass | 76 tests, including a 200-sample ES256 signature round trip verified against a generated public key |
| `AC-01` doctor inventory | pass | 15 checks in a fixed order, each unmet one carrying a remedy; a test asserts no configured or discovered value appears in any detail or hint |
| `AC-03` no key configured | pass | `doctor` exits 3, reports "No request was attempted", and `profiles ensure` exits 3; `~/Library/Developer/Posato/` unchanged by checksum |
| `AC-03` rejected credential | pass | Unit-level: 401 maps to `ASC_UNAUTHORIZED` and is never retried |
| Private-data scan | pass | `git diff --check` clean; no team, device, key, or personal path in the diff; device fixtures confirmed not to collide with this Mac's real identifiers |
| `AC-02` real-account run | blocked | Needs the App Store Connect team key |
| `AC-04` secret-free tracked files | partial | The scan above passes; the account half completes with `AC-02` |

## Blockers and accepted risks

- Maintainer action: create one App Store Connect API team key with the Admin
  role, download the `.p8` once, store it under `~/Library/Developer/Posato/`,
  and add `posato.asc.keyId`, `posato.asc.issuerId`, and
  `posato.asc.privateKeyPath` to `local.properties` in the main checkout, then
  copy the whole file into this worktree. `AC-02` and the account half of
  `AC-04` stay unverified until then.
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

- **Status:** `blocked`
- **Outcome:** Implementation complete and locally verified; the real-account
  acceptance run waits on the maintainer's App Store Connect key.
